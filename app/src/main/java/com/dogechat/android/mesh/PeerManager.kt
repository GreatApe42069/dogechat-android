package com.dogechat.android.mesh

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Peer information structure with verification status
 * Compatible with iOS PeerInfo structure
 */
data class PeerInfo(
    val id: String,
    var nickname: String,
    var isConnected: Boolean,
    var isDirectConnection: Boolean,
    var noisePublicKey: ByteArray?,
    var signingPublicKey: ByteArray?,      // Ed25519 public key for verification
    var isVerifiedNickname: Boolean,       // Verification status flag
    var lastSeen: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PeerInfo

        if (id != other.id) return false
        if (nickname != other.nickname) return false
        if (isConnected != other.isConnected) return false
        if (isDirectConnection != other.isDirectConnection) return false
        if (noisePublicKey != null) {
            if (other.noisePublicKey == null) return false
            if (!noisePublicKey.contentEquals(other.noisePublicKey)) return false
        } else if (other.noisePublicKey != null) return false
        if (signingPublicKey != null) {
            if (other.signingPublicKey == null) return false
            if (!signingPublicKey.contentEquals(other.signingPublicKey)) return false
        } else if (other.signingPublicKey != null) return false
        if (isVerifiedNickname != other.isVerifiedNickname) return false
        if (lastSeen != other.lastSeen) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nickname.hashCode()
        result = 31 * result + isConnected.hashCode()
        result = 31 * result + isDirectConnection.hashCode()
        result = 31 * result + (noisePublicKey?.contentHashCode() ?: 0)
        result = 31 * result + (signingPublicKey?.contentHashCode() ?: 0)
        result = 31 * result + isVerifiedNickname.hashCode()
        result = 31 * result + lastSeen.hashCode()
        return result
    }
}

/**
 * Manages active peers, nicknames, RSSI tracking, and peer fingerprints
 * Extracted from BluetoothMeshService for better separation of concerns
 *
 * Includes centralized fingerprint management via PeerFingerprintManager singleton
 * and support for signed announcement verification.
 */
class PeerManager {

    companion object {
        private const val TAG = "PeerManager"
        private const val STALE_PEER_TIMEOUT = 180000L // 3 minutes (same as iOS)
        private const val CLEANUP_INTERVAL = 60000L // 1 minute
    }

    // Peer tracking data - enhanced with verification status
    private val peers = ConcurrentHashMap<String, PeerInfo>() // peerID -> PeerInfo
    private val peerRSSI = ConcurrentHashMap<String, Int>()
    private val announcedPeers = CopyOnWriteArrayList<String>()
    private val announcedToPeers = CopyOnWriteArrayList<String>()

    // Centralized fingerprint management
    private val fingerprintManager = PeerFingerprintManager.getInstance()

    // Delegate for callbacks
    var delegate: PeerManagerDelegate? = null

    // Coroutines
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        startPeriodicCleanup()
    }

    // MARK: - New PeerInfo-based methods

    /**
     * Update peer information with verification data
     */
    fun updatePeerInfo(
        peerID: String,
        nickname: String,
        noisePublicKey: ByteArray,
        signingPublicKey: ByteArray,
        isVerified: Boolean
    ): Boolean {
        if (peerID == "unknown") return false

        val now = System.currentTimeMillis()
        val existingPeer = peers[peerID]
        val isNewPeer = existingPeer == null

        val peerInfo = PeerInfo(
            id = peerID,
            nickname = nickname,
            isConnected = true,
            isDirectConnection = existingPeer?.isDirectConnection ?: false,
            noisePublicKey = noisePublicKey,
            signingPublicKey = signingPublicKey,
            isVerifiedNickname = isVerified,
            lastSeen = now
        )

        peers[peerID] = peerInfo

        if (isNewPeer && isVerified) {
            announcedPeers.add(peerID)
            notifyPeerListUpdate()
            Log.d(TAG, "🆕 New verified peer: $nickname ($peerID)")
            return true
        } else if (isVerified) {
            Log.d(TAG, "🔄 Updated verified peer: $nickname ($peerID)")
        } else {
            Log.d(TAG, "⚠️ Unverified peer announcement from: $nickname ($peerID)")
        }

        return false
    }

    fun getPeerInfo(peerID: String): PeerInfo? = peers[peerID]

    fun isPeerVerified(peerID: String): Boolean = peers[peerID]?.isVerifiedNickname == true

    fun getVerifiedPeers(): Map<String, PeerInfo> = peers.filterValues { it.isVerifiedNickname }

    /**
     * Set whether a peer is directly connected over Bluetooth.
     * Triggers a peer list update to refresh UI badges.
     */
    fun setDirectConnection(peerID: String, isDirect: Boolean) {
        peers[peerID]?.let { existing ->
            if (existing.isDirectConnection != isDirect) {
                peers[peerID] = existing.copy(isDirectConnection = isDirect)
                notifyPeerListUpdate()
            }
        }
    }

    // MARK: - Legacy/compatibility methods

    fun updatePeerLastSeen(peerID: String) {
        if (peerID != "unknown") {
            peers[peerID]?.let { info ->
                peers[peerID] = info.copy(lastSeen = System.currentTimeMillis())
            }
        }
    }

    fun addOrUpdatePeer(peerID: String, nickname: String): Boolean {
        if (peerID == "unknown") return false

        // Clean up stale peer IDs with the same nickname (same logic as iOS)
        val now = System.currentTimeMillis()
        val stalePeerIDs = mutableListOf<String>()
        peers.forEach { (existingPeerID, info) ->
            if (info.nickname == nickname && existingPeerID != peerID) {
                val wasRecentlySeen = (now - info.lastSeen) < 10_000
                if (!wasRecentlySeen) {
                    stalePeerIDs.add(existingPeerID)
                }
            }
        }
        stalePeerIDs.forEach { removePeer(it, notifyDelegate = false) }

        val isFirstAnnounce = !announcedPeers.contains(peerID)

        val existing = peers[peerID]
        if (existing != null) {
            peers[peerID] = existing.copy(nickname = nickname, lastSeen = now, isConnected = true)
        } else {
            peers[peerID] = PeerInfo(
                id = peerID,
                nickname = nickname,
                isConnected = true,
                isDirectConnection = false,
                noisePublicKey = null,
                signingPublicKey = null,
                isVerifiedNickname = false,
                lastSeen = now
            )
        }

        if (isFirstAnnounce) {
            announcedPeers.add(peerID)
            notifyPeerListUpdate()
            return true
        }

        Log.d(TAG, "Updated peer: $peerID ($nickname)")
        return false
    }

    fun removePeer(peerID: String, notifyDelegate: Boolean = true) {
        val removed = peers.remove(peerID)
        peerRSSI.remove(peerID)
        announcedPeers.remove(peerID)
        announcedToPeers.remove(peerID)

        // Also remove fingerprint mappings
        fingerprintManager.removePeer(peerID)

        if (notifyDelegate && removed != null) {
            try {
                delegate?.onPeerRemoved(peerID)
            } catch (_: Exception) {
            }
            notifyPeerListUpdate()
        }
    }

    fun updatePeerRSSI(peerID: String, rssi: Int) {
        if (peerID != "unknown") {
            peerRSSI[peerID] = rssi
        }
    }

    fun hasAnnouncedToPeer(peerID: String): Boolean = announcedToPeers.contains(peerID)

    fun markPeerAsAnnouncedTo(peerID: String) {
        if (!announcedToPeers.contains(peerID)) {
            announcedToPeers.add(peerID)
        }
    }

    fun isPeerActive(peerID: String): Boolean {
        val info = peers[peerID] ?: return false
        val now = System.currentTimeMillis()
        return (now - info.lastSeen) <= STALE_PEER_TIMEOUT && info.isConnected
    }

    fun getPeerNickname(peerID: String): String? = peers[peerID]?.nickname

    fun getAllPeerNicknames(): Map<String, String> = peers.mapValues { it.value.nickname }

    fun getAllPeerRSSI(): Map<String, Int> = peerRSSI.toMap()

    fun getActivePeerIDs(): List<String> {
        val now = System.currentTimeMillis()
        return peers.filterValues { (now - it.lastSeen) <= STALE_PEER_TIMEOUT && it.isConnected }
            .keys
            .toList()
            .sorted()
    }

    fun getActivePeerCount(): Int = getActivePeerIDs().size

    fun clearAllPeers() {
        peers.clear()
        peerRSSI.clear()
        announcedPeers.clear()
        announcedToPeers.clear()
    }

    /**
     * Get debug information
     */
    fun getDebugInfo(addressPeerMap: Map<String, String>? = null): String {
        val now = System.currentTimeMillis()
        val activeIds = getActivePeerIDs().toSet()
        return buildString {
            appendLine("=== Peer Manager Debug Info ===")
            appendLine("Active Peers: ${activeIds.size}")
            peers.forEach { (peerID, info) ->
                val timeSince = (now - info.lastSeen) / 1000
                val rssi = peerRSSI[peerID]?.let { "${it} dBm" } ?: "No RSSI"
                val deviceAddress = addressPeerMap?.entries?.find { it.value == peerID }?.key
                val addressInfo = deviceAddress?.let { " [Device: $it]" } ?: " [Device: Unknown]"
                val status = if (activeIds.contains(peerID)) "ACTIVE" else "INACTIVE"
                val direct = if (info.isDirectConnection) "DIRECT" else "ROUTED"
                appendLine("  - $peerID (${info.nickname})$addressInfo - $status/$direct, last seen ${timeSince}s ago, RSSI: $rssi")
            }
            appendLine("Announced Peers: ${announcedPeers.size}")
            appendLine("Announced To Peers: ${announcedToPeers.size}")
        }
    }

    /**
     * Get debug information with device addresses
     */
    fun getDebugInfoWithDeviceAddresses(addressPeerMap: Map<String, String>): String {
        return buildString {
            appendLine("=== Device Address to Peer Mapping ===")
            if (addressPeerMap.isEmpty()) {
                appendLine("No device address mappings available")
            } else {
                addressPeerMap.forEach { (deviceAddress, peerID) ->
                    val nickname = peers[peerID]?.nickname ?: "Unknown"
                    val isActive = isPeerActive(peerID)
                    val status = if (isActive) "ACTIVE" else "INACTIVE"
                    appendLine("  Device: $deviceAddress -> Peer: $peerID ($nickname) [$status]")
                }
            }
            appendLine()
            appendLine(getDebugInfo(addressPeerMap))
        }
    }

    /**
     * Notify delegate of peer list updates
     */
    private fun notifyPeerListUpdate() {
        val peerList = getActivePeerIDs()
        delegate?.onPeerListUpdated(peerList)
    }

    /**
     * Start periodic cleanup of stale peers
     */
    private fun startPeriodicCleanup() {
        managerScope.launch {
            while (isActive) {
                delay(CLEANUP_INTERVAL)
                cleanupStalePeers()
            }
        }
    }

    /**
     * Clean up stale peers (same 3-minute threshold as iOS)
     */
    private fun cleanupStalePeers() {
        val now = System.currentTimeMillis()
        val peersToRemove = peers.filterValues { (now - it.lastSeen) > STALE_PEER_TIMEOUT }
            .keys
            .toList()

        peersToRemove.forEach { peerID ->
            Log.d(TAG, "Removing stale peer: $peerID")
            removePeer(peerID)
        }

        if (peersToRemove.isNotEmpty()) {
            Log.d(TAG, "Cleaned up ${peersToRemove.size} stale peers")
        }
    }

    // MARK: - Fingerprint Management (Centralized)

    fun storeFingerprintForPeer(peerID: String, publicKey: ByteArray): String {
        return fingerprintManager.storeFingerprintForPeer(peerID, publicKey)
    }

    fun updatePeerIDMapping(oldPeerID: String?, newPeerID: String, fingerprint: String) {
        fingerprintManager.updatePeerIDMapping(oldPeerID, newPeerID, fingerprint)
    }

    fun getFingerprintForPeer(peerID: String): String? = fingerprintManager.getFingerprintForPeer(peerID)

    fun getPeerIDForFingerprint(fingerprint: String): String? =
        fingerprintManager.getPeerIDForFingerprint(fingerprint)

    fun hasFingerprintForPeer(peerID: String): Boolean =
        fingerprintManager.hasFingerprintForPeer(peerID)

    fun getAllPeerFingerprints(): Map<String, String> = fingerprintManager.getAllPeerFingerprints()

    fun clearAllFingerprints() {
        fingerprintManager.clearAllFingerprints()
    }

    fun getFingerprintDebugInfo(): String = fingerprintManager.getDebugInfo()

    /**
     * Shutdown the manager
     */
    fun shutdown() {
        managerScope.cancel()
        clearAllPeers()
    }
}

/**
 * Delegate interface for peer manager callbacks
 */
interface PeerManagerDelegate {
    fun onPeerListUpdated(peerIDs: List<String>)
    fun onPeerRemoved(peerID: String)
}