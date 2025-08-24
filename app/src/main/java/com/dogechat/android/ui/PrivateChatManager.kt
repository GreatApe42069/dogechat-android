package com.dogechat.android.ui

import com.dogechat.android.model.DogechatMessage
import com.dogechat.android.model.DeliveryStatus
import com.dogechat.android.mesh.PeerFingerprintManager
import com.dogechat.android.mesh.BluetoothMeshService
import java.util.*
import android.util.Log

/**
 * Interface for Noise session operations needed by PrivateChatManager
 * This avoids reflection and makes dependencies explicit
 */
interface NoiseSessionDelegate {
    fun hasEstablishedSession(peerID: String): Boolean
    fun initiateHandshake(peerID: String)
    fun getMyPeerID(): String
}

/**
 * Handles private chat functionality including peer management and blocking
 * Now uses centralized PeerFingerprintManager for all fingerprint operations
 */
class PrivateChatManager(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val dataManager: DataManager,
    private val noiseSessionDelegate: NoiseSessionDelegate
) {

    companion object {
        private const val TAG = "PrivateChatManager"
    }

    // Use centralized fingerprint management - NO LOCAL STORAGE
    private val fingerprintManager = PeerFingerprintManager.getInstance()

    // Track received private messages that need read receipts
    private val unreadReceivedMessages = mutableMapOf<String, MutableList<DogechatMessage>>()

    // MARK: - Private Chat Lifecycle

    fun startPrivateChat(peerID: String, meshService: BluetoothMeshService): Boolean {
        if (isPeerBlocked(peerID)) {
            val peerNickname = getPeerNickname(peerID, meshService)
            val systemMessage = DogechatMessage(
                sender = "system",
                content = "cannot start chat with $peerNickname: user is blocked.",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return false
        }

        // Establish Noise session if needed before starting the chat
        establishNoiseSessionIfNeeded(peerID, meshService)

        state.setSelectedPrivateChatPeer(peerID)

        // Clear unread
        messageManager.clearPrivateUnreadMessages(peerID)

        // Initialize chat if needed
        messageManager.initializePrivateChat(peerID)

        // Send read receipts for all unread messages from this peer
        sendReadReceiptsForPeer(peerID, meshService)

        return true
    }

    fun endPrivateChat() {
        state.setSelectedPrivateChatPeer(null)
    }

    fun sendPrivateMessage(
        content: String,
        peerID: String,
        recipientNickname: String?,
        senderNickname: String?,
        myPeerID: String,
        onSendMessage: (String, String, String, String) -> Unit
    ): Boolean {
        if (isPeerBlocked(peerID)) {
            val systemMessage = DogechatMessage(
                sender = "system",
                content = "cannot send message to $recipientNickname: user is blocked.",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return false
        }

        val message = DogechatMessage(
            sender = senderNickname ?: myPeerID,
            content = content,
            timestamp = Date(),
            isRelay = false,
            isPrivate = true,
            recipientNickname = recipientNickname,
            senderPeerID = myPeerID,
            deliveryStatus = DeliveryStatus.Sending
        )

        messageManager.addPrivateMessage(peerID, message)
        onSendMessage(content, peerID, recipientNickname ?: "", message.id)

        return true
    }

    // MARK: - Peer Management

    fun isPeerBlocked(peerID: String): Boolean {
        val fingerprint = fingerprintManager.getFingerprintForPeer(peerID)
        return fingerprint != null && dataManager.isUserBlocked(fingerprint)
    }

    fun toggleFavorite(peerID: String) {
        val fingerprint = fingerprintManager.getFingerprintForPeer(peerID) ?: return

        Log.d(TAG, "toggleFavorite called for peerID: $peerID, fingerprint: $fingerprint")

        val wasFavorite = dataManager.isFavorite(fingerprint)
        Log.d(TAG, "Current favorite status: $wasFavorite")

        val currentFavorites = state.getFavoritePeersValue()
        Log.d(TAG, "Current UI state favorites: $currentFavorites")

        if (wasFavorite) {
            dataManager.removeFavorite(fingerprint)
            Log.d(TAG, "Removed from favorites: $fingerprint")
        } else {
            dataManager.addFavorite(fingerprint)
            Log.d(TAG, "Added to favorites: $fingerprint")
        }

        // Always update state to trigger UI refresh - create new set to ensure change detection
        val newFavorites = dataManager.favoritePeers.toSet()
        state.setFavoritePeers(newFavorites)

        Log.d(TAG, "Force updated favorite peers state. New favorites: $newFavorites")
        Log.d(TAG, "All peer fingerprints: ${fingerprintManager.getAllPeerFingerprints()}")
    }

    fun isFavorite(peerID: String): Boolean {
        val fingerprint = fingerprintManager.getFingerprintForPeer(peerID) ?: return false
        val isFav = dataManager.isFavorite(fingerprint)
        Log.d(TAG, "isFavorite check: peerID=$peerID, fingerprint=$fingerprint, result=$isFav")
        return isFav
    }

    fun getPeerFingerprint(peerID: String): String? {
        return fingerprintManager.getFingerprintForPeer(peerID)
    }

    fun getPeerFingerprints(): Map<String, String> {
        return fingerprintManager.getAllPeerFingerprints()
    }

    // MARK: - Block/Unblock Operations

    fun blockPeer(peerID: String, meshService: BluetoothMeshService): Boolean {
        val fingerprint = fingerprintManager.getFingerprintForPeer(peerID)
        if (fingerprint != null) {
            dataManager.addBlocked(fingerprint)
            state.setBlockedPeers(dataManager.blockedPeers.toSet())
            
            // End private chat if blocked
            if (state.getSelectedPrivateChatPeerValue() == peerID) {
                endPrivateChat()
            }
            
            // Remove from favorites if blocked
            if (dataManager.isFavorite(fingerprint)) {
                dataManager.removeFavorite(fingerprint)
                state.setFavoritePeers(dataManager.favoritePeers.toSet())
            }
            
            val peerNickname = getPeerNickname(peerID, meshService)
            val systemMessage = DogechatMessage(
                sender = "system",
                content = "$peerNickname blocked",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return true
        }
        return false
    }

    fun unblockPeer(peerID: String, meshService: BluetoothMeshService): Boolean {
        val fingerprint = fingerprintManager.getFingerprintForPeer(peerID)
        if (fingerprint != null) {
            dataManager.removeBlocked(fingerprint)
            state.setBlockedPeers(dataManager.blockedPeers.toSet())
            
            val peerNickname = getPeerNickname(peerID, meshService)
            val systemMessage = DogechatMessage(
                sender = "system",
                content = "$peerNickname unblocked",
                timestamp = Date(),
                isRelay = false
            )
            messageManager.addMessage(systemMessage)
            return true
        }
        return false
    }

    fun getBlockedPeers(meshService: BluetoothMeshService): List<String> {
        return dataManager.blockedPeers.mapNotNull { fingerprint ->
            fingerprintManager.getPeerForFingerprint(fingerprint)?.let { peerID ->
                getPeerNickname(peerID, meshService)
            }
        }
    }

    // MARK: - Message Handling

    fun handleIncomingPrivateMessage(message: DogechatMessage) {
        message.senderPeerID?.let { senderPeerID ->
            messageManager.addPrivateMessage(senderPeerID, message)
            
            // Track for read receipt if not already viewed
            if (state.getSelectedPrivateChatPeerValue() != senderPeerID) {
                val unreadList = unreadReceivedMessages.getOrPut(senderPeerID) { mutableListOf() }
                unreadList.add(message)
            }
        }
    }

    fun sendReadReceiptsForPeer(peerID: String, meshService: BluetoothMeshService) {
        val unreadList = unreadReceivedMessages[peerID] ?: return
        
        unreadList.forEach { message ->
            message.id.let { messageID ->
                meshService.sendReadReceipt(messageID, peerID)
            }
        }

        // Clear the unread list since we've sent read receipts
        unreadReceivedMessages.remove(peerID)
    }

    fun cleanupDisconnectedPeer(peerID: String) {
        // End private chat if peer disconnected
        if (state.getSelectedPrivateChatPeerValue() == peerID) {
            endPrivateChat()
        }

        // Clean up unread messages for disconnected peer
        unreadReceivedMessages.remove(peerID)
        Log.d(TAG, "Cleaned up unread messages for disconnected peer $peerID")
    }

    // MARK: - Noise Session Management

    /**
     * Establish Noise session if needed before starting private chat
     * Uses same lexicographical logic as MessageHandler.handleNoiseIdentityAnnouncement
     */
    private fun establishNoiseSessionIfNeeded(peerID: String, meshService: BluetoothMeshService) {
        if (noiseSessionDelegate.hasEstablishedSession(peerID)) {
            Log.d(TAG, "Noise session already established with $peerID")
            return
        }

        Log.d(TAG, "No Noise session with $peerID, determining who should initiate handshake")

        val myPeerID = noiseSessionDelegate.getMyPeerID()

        // Use lexicographical comparison to decide who initiates (same logic as MessageHandler)
        if (myPeerID < peerID) {
            // We should initiate the handshake
            Log.d(
                TAG,
                "Our peer ID lexicographically < target peer ID, initiating Noise handshake with $peerID"
            )
            noiseSessionDelegate.initiateHandshake(peerID)
        } else {
            // They should initiate, we send identity announcement through standard announce
            Log.d(
                TAG,
                "Our peer ID lexicographically >= target peer ID, sending identity announcement to prompt handshake from $peerID"
            )
            meshService.sendAnnouncementToPeer(peerID)
        }

    }

    /**
     * Initiate handshake with specific peer using the existing delegate pattern
     */
    private fun initiateHandshakeWithPeer(peerID: String, meshService: Any) {
        try {
            // Use the existing MessageHandler delegate approach to initiate handshake
            // This calls the same code that's in MessageHandler's delegate.initiateNoiseHandshake()
            val messageHandler = meshService::class.java.getDeclaredField("messageHandler")
            messageHandler.isAccessible = true
            val handler = messageHandler.get(meshService)

            val delegate = handler::class.java.getDeclaredField("delegate")
            delegate.isAccessible = true
            val handlerDelegate = delegate.get(handler)

            val method =
                handlerDelegate::class.java.getMethod("initiateNoiseHandshake", String::class.java)
            method.invoke(handlerDelegate, peerID)

            Log.d(TAG, "Successfully initiated Noise handshake with $peerID using delegate pattern")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate Noise handshake with $peerID: ${e.message}")
        }
    }

    /**
     * Send Noise identity announcement to prompt other peer to initiate handshake
     * This follows the same pattern as broadcastNoiseIdentityAnnouncement() in BluetoothMeshService
     */
    private fun sendNoiseIdentityAnnouncement(meshService: Any) {
        try {
            // Call broadcastNoiseIdentityAnnouncement which sends a NoiseIdentityAnnouncement
            val method =
                meshService::class.java.getDeclaredMethod("broadcastNoiseIdentityAnnouncement")
            method.invoke(meshService)
            Log.d(TAG, "Successfully sent Noise identity announcement")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Noise identity announcement: ${e.message}")
        }
    }

    // MARK: - Utility Functions

    private fun getPeerIDForNickname(nickname: String, meshService: BluetoothMeshService): String? {
        return meshService.getPeerNicknames().entries.find { it.value == nickname }?.key
    }

    private fun getPeerNickname(peerID: String, meshService: BluetoothMeshService): String {
        return meshService.getPeerNicknames()[peerID] ?: peerID
    }

    // MARK: - Emergency Clear

    fun clearAllPrivateChats() {
        state.setSelectedPrivateChatPeer(null)
        state.setUnreadPrivateMessages(emptySet())

        // Clear unread messages tracking
        unreadReceivedMessages.clear()

        // Clear fingerprints via centralized manager (only if needed for emergency clear)
        // Note: This will be handled by the parent PeerManager.clearAllPeers()
    }

    // MARK: - Public Getters

    fun getAllPeerFingerprints(): Map<String, String> {
        return fingerprintManager.getAllPeerFingerprints()
    }
}
