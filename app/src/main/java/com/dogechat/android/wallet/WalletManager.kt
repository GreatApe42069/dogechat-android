package com.dogechat.android.wallet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dogechat.android.net.TorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.bitcoinj.core.Address
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.PeerAddress
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.wallet.Wallet
import org.libdohj.params.DogecoinMainNetParams
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.Date

// Alias bitcoinj Context to avoid clashing with android.content.Context
import org.bitcoinj.core.Context as BtcContext

data class SpvStatus(
    val running: Boolean,
    val peerCount: Int,
    val syncPercent: Int,
    val lastLogLine: String
)

@Singleton
class WalletManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    companion object {
        private const val TAG = "WalletManager"
        private const val FILE_PREFIX = "dogechat_doge"
        private const val PREFS_NAME = "dogechat_wallet"
        private const val PREF_KEY_RECEIVE_ADDRESS = "receive_address"
        private const val PREF_KEY_SPV_ENABLED = "spv_enabled"
        private const val PEERS_ASSET = "dogecoin_peers.csv"
        private const val TOR_WAIT_MS = 20_000L
        private const val TOR_POLL_MS = 300L
        private const val MAX_PEERS = 4

        object SpvController {
            val enabled = MutableStateFlow(false)
            val status = MutableStateFlow(
                SpvStatus(
                    running = false,
                    peerCount = 0,
                    syncPercent = 0,
                    lastLogLine = ""
                )
            )

            fun get(context: Context): Boolean {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val isOn = prefs.getBoolean(PREF_KEY_SPV_ENABLED, false) // default OFF
                enabled.value = isOn
                return isOn
            }

            fun set(context: Context, turnOn: Boolean) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(PREF_KEY_SPV_ENABLED, turnOn).apply()
                enabled.value = turnOn
                instanceRef?.let { mgr ->
                    if (turnOn) mgr.startNetwork() else mgr.stopNetwork()
                }
            }

            internal fun updateRunning(running: Boolean) {
                val cur = status.value
                status.value = cur.copy(running = running)
            }
            internal fun updatePeers(count: Int) {
                val cur = status.value
                status.value = cur.copy(peerCount = count)
            }
            internal fun updateSync(p: Int) {
                val cur = status.value
                status.value = cur.copy(syncPercent = p)
            }
            internal fun log(line: String) {
                val cur = status.value
                status.value = cur.copy(lastLogLine = line)
            }
        }

        @Volatile internal var instanceRef: WalletManager? = null
    }

    data class TxRow(
        val hash: String,
        val value: String,
        val isIncoming: Boolean,
        val time: Date?,
        val confirmations: Int
    )

    private val scope = CoroutineScope(Dispatchers.IO)

    // Dogecoin params (libdohj)
    private val params: NetworkParameters = DogecoinMainNetParams.get()

    private var kit: WalletAppKit? = null

    private val _balance = MutableStateFlow("0 DOGE")
    val balance: StateFlow<String> = _balance

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address

    private val _syncPercent = MutableStateFlow(0)
    val syncPercent: StateFlow<Int> = _syncPercent

    private val _history = MutableStateFlow<List<TxRow>>(emptyList())
    val history: StateFlow<List<TxRow>> = _history

    private val _peerCount = MutableStateFlow(0)
    val peerCount: StateFlow<Int> = _peerCount

    private val _spvStatus = MutableStateFlow("Not Connected")
    val spvStatus: StateFlow<String> = _spvStatus

    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        instanceRef = this
        val shouldStart = prefs.getBoolean(PREF_KEY_SPV_ENABLED, false)
        SpvController.enabled.value = shouldStart
        if (shouldStart) startNetwork() else SpvController.updateRunning(false)
    }

    fun startNetwork() {
        scope.launch {
            try {
                // Diagnostics: confirm we’re on Dogecoin (port should be 22556)
                Log.i(TAG, "Starting SPV… params.id=${params.id} port=${params.port}")
                BtcContext.propagate(BtcContext(params))

                // Configure Tor SOCKS if enabled
                configureTorSocksIfAvailable()

                val dir = File(appContext.filesDir, "wallet")
                if (!dir.exists()) dir.mkdirs()

                val usingSocks = System.getProperty("socksProxyHost")?.isNotBlank() == true
                val staticPeers = loadStaticPeersFromAssets(params, preferUnresolvedHostnames = usingSocks)

                val k = object : WalletAppKit(params, dir, FILE_PREFIX) {
                    override fun onSetupCompleted() {
                        try {
                            try { peerGroup().setUseLocalhostPeerWhenPossible(false) } catch (_: Throwable) {}
                            try { peerGroup().setMaxConnections(MAX_PEERS) } catch (_: Throwable) {}

                            // If we have static peers, lock to them before start
                            if (staticPeers.isNotEmpty()) {
                                try {
                                    setPeerNodes(*staticPeers.toTypedArray())
                                    Log.i(TAG, "Using ${staticPeers.size} static peers from assets ($PEERS_ASSET)")
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Failed to set static peers: ${t.message}")
                                }
                            }

                            pushBalance()
                            pushAddress()
                            pushHistory()

                            wallet().addCoinsReceivedEventListener { _: Wallet, _, _, _ ->
                                pushBalance(); pushHistory()
                                SpvController.log("coins received")
                            }
                            wallet().addCoinsSentEventListener { _: Wallet, _, _, _ ->
                                pushBalance(); pushHistory()
                                SpvController.log("coins sent")
                            }
                            wallet().addChangeEventListener { _ ->
                                pushBalance(); pushHistory()
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "onSetupCompleted tuning failed: ${t.message}")
                        }
                    }
                }.apply {
                    setBlockingStartup(false)
                    setDownloadListener(object : DownloadProgressTracker() {
                        override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                            val p = pct.toInt().coerceIn(0, 100)
                            _syncPercent.value = p
                            SpvController.updateSync(p)
                            _spvStatus.value = if (p < 100) "Syncing" else "Synced"
                            if (blocksSoFar % 100 == 0) SpvController.log("sync $p% ($blocksSoFar blocks)")
                        }
                        override fun doneDownload() {
                            _syncPercent.value = 100
                            SpvController.updateSync(100)
                            pushBalance()
                            pushHistory()
                            _spvStatus.value = "Synced"
                            SpvController.log("sync complete")
                        }
                    })
                }

                kit = k

                // Peer event listeners differ by version; wrap in try
                try {
                    k.peerGroup().addConnectedEventListener { _, pc ->
                        _peerCount.value = pc
                        SpvController.updatePeers(pc)
                        SpvController.log("peer connected ($pc)")
                        updateSpvStatus()
                    }
                    k.peerGroup().addDisconnectedEventListener { _, pc ->
                        _peerCount.value = pc
                        SpvController.updatePeers(pc)
                        SpvController.log("peer disconnected ($pc)")
                        updateSpvStatus()
                    }
                } catch (_: Throwable) {
                    // If not available on this version, we’ll still show sync status
                }

                _spvStatus.value = "Connecting"
                SpvController.updateRunning(true)
                SpvController.log("starting…")

                k.startAsync()
                k.awaitRunning()

                Log.i(TAG, "SPV running. Address: ${currentReceiveAddress()}")
                pushAddress()
                pushBalance()
                pushHistory()
                updateSpvStatus()

                // Extra periodic diagnostics if stuck
                scope.launch {
                    var tries = 0
                    while (tries < 20 && kit != null) {
                        delay(2000)
                        tries++
                        val pc = _peerCount.value
                        val sp = _syncPercent.value
                        SpvController.log("diag peers=$pc sync=$sp%")
                        if (pc > 0 || sp > 0) break
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start SPV wallet: ${e.message}", e)
                _spvStatus.value = "Error"
                SpvController.updateRunning(false)
                SpvController.log("error: ${e.message}")
            }
        }
    }

    fun stopNetwork() {
        scope.launch {
            try {
                kit?.apply {
                    Log.i(TAG, "Stopping SPV…")
                    SpvController.log("stopping…")
                    stopAsync()
                    awaitTerminated()
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to stop wallet kit: ${e.message}", e)
            } finally {
                kit = null
                _syncPercent.value = 0
                _peerCount.value = 0
                _spvStatus.value = "Not Connected"
                SpvController.updateRunning(false)
                SpvController.updatePeers(0)
                SpvController.updateSync(0)
                SpvController.log("stopped")
                clearSocksProxy()
            }
        }
    }

    fun currentReceiveAddress(): String? = try {
        kit?.wallet()?.currentReceiveAddress()?.toString()
            ?: prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
    } catch (_: Throwable) {
        prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
    }

    fun generateNewReceiveAddress() {
        scope.launch {
            val address = kit?.wallet()?.freshReceiveAddress()?.toString()
            if (address != null) {
                prefs.edit().putString(PREF_KEY_RECEIVE_ADDRESS, address).apply()
                _address.value = address
            }
        }
    }

    private fun pushAddress() {
        try {
            val address = kit?.wallet()?.currentReceiveAddress()?.toString()
            if (address != null) {
                prefs.edit().putString(PREF_KEY_RECEIVE_ADDRESS, address).apply()
                _address.value = address
            } else {
                _address.value = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
            }
        } catch (_: Throwable) {
            _address.value = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
        }
    }

    fun refreshAddress() {
        generateNewReceiveAddress()
    }

    fun sendCoins(
        toAddress: String,
        amountDoge: Long,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        scope.launch {
            val localKit = kit ?: run {
                onResult(false, "Wallet not started")
                return@launch
            }
            try {
                val address: Address = LegacyAddress.fromBase58(params, toAddress)
                val amount = org.bitcoinj.core.Coin.valueOf(amountDoge * 100_000_000L)
                localKit.wallet().sendCoins(localKit.peerGroup(), address, amount)
                pushBalance()
                pushHistory()
                SpvController.log("broadcast requested")
                onResult(true, "Broadcast requested")
            } catch (e: Throwable) {
                Log.e(TAG, "sendCoins failed: ${e.message}", e)
                SpvController.log("send error: ${e.message}")
                onResult(false, e.message ?: "Unknown error")
            }
        }
    }

    private fun pushBalance() {
        val w = kit?.wallet() ?: return
        val balance = w.balance.toPlainString()
        _balance.value = "$balance DOGE"
    }

    private fun pushHistory() {
        val w = kit?.wallet() ?: return
        val rows = w.getTransactionsByTime().map { tx ->
            val vToMe = tx.getValueSentToMe(w)
            val vFromMe = tx.getValueSentFromMe(w)
            val delta = vToMe.subtract(vFromMe)
            val time: Date? = tx.updateTime
            TxRow(
                hash = tx.txId.toString(),
                value = delta.toPlainString() + " DOGE",
                isIncoming = delta.signum() > 0,
                time = time,
                confirmations = tx.confidence.depthInBlocks
            )
        }
        _history.value = rows
    }

    private fun updateSpvStatus() {
        _spvStatus.value = when {
            _peerCount.value == 0 -> "Not Connected"
            _syncPercent.value < 100 -> "Syncing"
            else -> "Synced"
        }
    }

    private suspend fun configureTorSocksIfAvailable() {
        try {
            val status0 = TorManager.statusFlow.value
            if (status0.mode == com.dogechat.android.net.TorMode.ON) {
                var waited = 0L
                while (waited < TOR_WAIT_MS) {
                    val s = TorManager.statusFlow.value
                    if (s.running && s.bootstrapPercent >= 100) break
                    delay(TOR_POLL_MS)
                    waited += TOR_POLL_MS
                }
                val addr = TorManager.currentSocksAddress()
                if (addr != null) {
                    System.setProperty("socksProxyHost", addr.hostString)
                    System.setProperty("socksProxyPort", addr.port.toString())
                    System.setProperty("socksProxyVersion", "5")
                    // Prefer IPv4 when tunneling via Tor to avoid IPv6 oddities
                    System.setProperty("java.net.preferIPv6Addresses", "false")
                    System.setProperty("java.net.preferIPv4Stack", "true")
                    SpvController.log("using Tor SOCKS ${addr.hostString}:${addr.port}")
                    Log.i(TAG, "Using Tor SOCKS ${addr.hostString}:${addr.port} for P2P")
                } else {
                    Log.w(TAG, "Tor is ON but socks address is null; will proceed without proxy")
                    clearSocksProxy()
                }
            } else {
                clearSocksProxy()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Tor SOCKS configuration failed: ${t.message}")
        }
    }

    private fun clearSocksProxy() {
        try {
            System.clearProperty("socksProxyHost")
            System.clearProperty("socksProxyPort")
            System.clearProperty("socksProxyVersion")
        } catch (_: Throwable) {}
    }

    private fun loadStaticPeersFromAssets(
        params: NetworkParameters,
        preferUnresolvedHostnames: Boolean
    ): List<PeerAddress> {
        val out = mutableListOf<PeerAddress>()
        try {
            val am = appContext.assets
            if (am.list("")?.contains(PEERS_ASSET) != true) {
                Log.i(TAG, "No $PEERS_ASSET in assets; will use default discovery")
                return emptyList()
            }
            am.open(PEERS_ASSET).use { ins ->
                BufferedReader(InputStreamReader(ins)).useLines { lines ->
                    lines.forEach { raw ->
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) return@forEach
                        val host: String
                        val port: Int
                        if (line.contains(":")) {
                            val parts = line.split(":")
                            host = parts[0].trim()
                            port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: params.port
                        } else {
                            host = line
                            port = params.port
                        }
                        try {
                            // If we prefer unresolved hostnames (Tor/SOCKS), avoid local DNS resolution:
                            // - Use InetSocketAddress(String host, int port) to keep it unresolved.
                            // If it's clearly an IP literal, either path is fine.
                            val sock: InetSocketAddress = if (preferUnresolvedHostnames) {
                                InetSocketAddress(host, port) // unresolved; SOCKS will resolve
                            } else {
                                // Best effort: resolve if not using SOCKS
                                val ip = runCatching { InetAddress.getByName(host) }.getOrNull()
                                if (ip != null) InetSocketAddress(ip, port) else InetSocketAddress(host, port)
                            }
                            out.add(PeerAddress(params, sock))
                        } catch (e: Throwable) {
                            Log.w(TAG, "Bad peer entry '$line': ${e.message}")
                        }
                    }
                }
            }
            if (out.isEmpty()) {
                Log.w(TAG, "No valid static peers parsed from $PEERS_ASSET")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed loading $PEERS_ASSET: ${t.message}")
        }
        return out
    }
}