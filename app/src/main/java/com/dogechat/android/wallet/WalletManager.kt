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
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.DumpedPrivateKey
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
    val lastLogLine: String,
    val torRunning: Boolean,
    val torBootstrap: Int
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
        private const val PREF_KEY_CACHED_WIF = "cached_wif"
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
                    lastLogLine = "",
                    torRunning = false,
                    torBootstrap = 0
                )
            )

            fun get(context: Context): Boolean {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val isOn = prefs.getBoolean(PREF_KEY_SPV_ENABLED, false)
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
            internal fun updateTor(running: Boolean, bootstrap: Int) {
                val cur = status.value
                status.value = cur.copy(torRunning = running, torBootstrap = bootstrap.coerceIn(0, 100))
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
    private val params: NetworkParameters = DogecoinMainNetParams.get()
    private var kit: WalletAppKit? = null

    private val _balance = MutableStateFlow("0 DOGE")
    val balance: StateFlow<String> = _balance

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address

    private val _addressReady = MutableStateFlow(false)
    val addressReady: StateFlow<Boolean> = _addressReady

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
        _addressReady.value = !prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null).isNullOrBlank()
        _address.value = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)

        val shouldStart = prefs.getBoolean(PREF_KEY_SPV_ENABLED, false)
        SpvController.enabled.value = shouldStart
        if (shouldStart) startNetwork() else SpvController.updateRunning(false)
    }

    fun startNetwork() {
        scope.launch {
            try {
                Log.i(TAG, "Starting SPV… params.id=${params.id} port=${params.port}")
                BtcContext.propagate(BtcContext(params))
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
                            if (staticPeers.isNotEmpty()) {
                                try { setPeerNodes(*staticPeers.toTypedArray()) } catch (t: Throwable) {
                                    Log.w(TAG, "Failed to set static peers: ${t.message}")
                                }
                            }
                            importCachedWifIfPresent()
                            pushBalance()
                            pushAddress()
                            pushHistory()
                            wallet().addCoinsReceivedEventListener { _: Wallet, _, _, _ ->
                                pushBalance(); pushHistory(); SpvController.log("coins received")
                            }
                            wallet().addCoinsSentEventListener { _: Wallet, _, _, _ ->
                                pushBalance(); pushHistory(); SpvController.log("coins sent")
                            }
                            wallet().addChangeEventListener { _ -> pushBalance(); pushHistory() }
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
                } catch (_: Throwable) { }

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
                SpvController.updateTor(false, 0)
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
                prefs.edit()
                    .remove(PREF_KEY_CACHED_WIF)
                    .putString(PREF_KEY_RECEIVE_ADDRESS, address)
                    .apply()
                _address.value = address
                _addressReady.value = true
            }
        }
    }

    private fun pushAddress() {
        try {
            val prev = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
            val addr = kit?.wallet()?.currentReceiveAddress()?.toString()
            if (addr != null) {
                if (prev != null && prev != addr) {
                    prefs.edit().remove(PREF_KEY_CACHED_WIF).apply()
                }
                prefs.edit().putString(PREF_KEY_RECEIVE_ADDRESS, addr).apply()
                _address.value = addr
                _addressReady.value = true
            } else {
                _address.value = prev
                _addressReady.value = !prev.isNullOrBlank()
            }
        } catch (_: Throwable) {
            val prefAddr = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
            _address.value = prefAddr
            _addressReady.value = !prefAddr.isNullOrBlank()
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
                withContext(Dispatchers.Main) { onResult(false, "Wallet not started") }
                return@launch
            }
            try {
                val address: Address = LegacyAddress.fromBase58(params, toAddress)
                val amount = org.bitcoinj.core.Coin.valueOf(amountDoge * 100_000_000L)
                localKit.wallet().sendCoins(localKit.peerGroup(), address, amount)
                pushBalance()
                pushHistory()
                SpvController.log("broadcast requested")
                withContext(Dispatchers.Main) { onResult(true, "Broadcast requested") }
            } catch (e: Throwable) {
                Log.e(TAG, "sendCoins failed: ${e.message}", e)
                SpvController.log("send error: ${e.message}")
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "Unknown error") }
            }
        }
    }

    // -------------- WIF persistence and import/restore ----------------

    fun getOrExportAndCacheWif(): String? {
        return try {
            val cached = prefs.getString(PREF_KEY_CACHED_WIF, null)
            if (!cached.isNullOrBlank()) {
                cached
            } else {
                val wif = exportCurrentReceivePrivateKeyWif()
                if (!wif.isNullOrBlank()) {
                    val addr = try {
                        val key = DumpedPrivateKey.fromBase58(params, wif).key
                        LegacyAddress.fromKey(params, key).toString()
                    } catch (_: Throwable) {
                        prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
                    }
                    persistWif(wif, addr)
                }
                wif
            }
        } catch (t: Throwable) {
            Log.w(TAG, "getOrExportAndCacheWif failed: ${t.message}")
            null
        }
    }

    fun exportCurrentReceivePrivateKeyWif(): String? = try {
        val w = kit?.wallet() ?: return null
        val key = w.currentReceiveKey() ?: return null
        key.getPrivateKeyAsWiF(params)
    } catch (t: Throwable) {
        Log.w(TAG, "export WIF failed: ${t.message}")
        null
    }

    fun importPrivateKeyWif(wif: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                val key = DumpedPrivateKey.fromBase58(params, wif).key
                val addr = LegacyAddress.fromKey(params, key).toString()

                val w = kit?.wallet()
                if (w == null) {
                    persistWif(wif, addr)
                    _address.value = addr
                    _addressReady.value = true
                    withContext(Dispatchers.Main) { onResult(true, "WIF cached. It will be loaded next time the wallet starts.") }
                    return@launch
                }

                val already = try { w.importedKeys.contains(key) } catch (_: Throwable) { false }
                if (!already) {
                    try { w.importKey(key) } catch (_: Throwable) { }
                }
                persistWif(wif, addr)
                _address.value = addr
                _addressReady.value = true
                SpvController.log("imported WIF; addr=$addr")
                withContext(Dispatchers.Main) { onResult(true, "Private key imported") }
            } catch (e: Throwable) {
                Log.e(TAG, "importPrivateKeyWif failed: ${e.message}", e)
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "Import failed") }
            }
        }
    }

    fun getCachedWif(): String? = prefs.getString(PREF_KEY_CACHED_WIF, null)

    private fun importCachedWifIfPresent() {
        val wif = prefs.getString(PREF_KEY_CACHED_WIF, null) ?: return
        try {
            val w = kit?.wallet() ?: return
            val key = DumpedPrivateKey.fromBase58(params, wif).key
            val already = try { w.importedKeys.contains(key) } catch (_: Throwable) { false }
            if (!already) {
                try { w.importKey(key) } catch (_: Throwable) { }
            }
            val addr = LegacyAddress.fromKey(params, key).toString()
            _address.value = addr
            _addressReady.value = true
            Log.i(TAG, "Imported cached WIF: $addr")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to import cached WIF: ${t.message}")
        }
    }

    private fun persistWif(wif: String, address: String?) {
        prefs.edit().apply {
            putString(PREF_KEY_CACHED_WIF, wif)
            if (!address.isNullOrBlank()) putString(PREF_KEY_RECEIVE_ADDRESS, address)
        }.apply()
    }

    fun wipeWalletData(): Boolean {
        return try {
            try {
                val k = kit
                if (k != null) {
                    k.stopAsync()
                    k.awaitTerminated()
                }
            } catch (_: Throwable) {}
            kit = null

            runCatching {
                val dir = File(appContext.filesDir, "wallet")
                if (dir.exists()) dir.deleteRecursively()
            }

            prefs.edit()
                .remove(PREF_KEY_RECEIVE_ADDRESS)
                .remove(PREF_KEY_CACHED_WIF)
                .putBoolean(PREF_KEY_SPV_ENABLED, false)
                .apply()

            _address.value = null
            _addressReady.value = false
            _balance.value = "0 DOGE"
            _history.value = emptyList()
            _syncPercent.value = 0
            _peerCount.value = 0
            _spvStatus.value = "Not Connected"
            SpvController.enabled.value = false
            SpvController.updateRunning(false)
            SpvController.updatePeers(0)
            SpvController.updateSync(0)
            SpvController.updateTor(false, 0)
            SpvController.log("wallet wiped")

            true
        } catch (t: Throwable) {
            Log.e(TAG, "wipeWalletData failed: ${t.message}", t)
            false
        }
    }

    // ------------------------------------------------------------------

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
                    System.setProperty("java.net.preferIPv6Addresses", "false")
                    System.setProperty("java.net.preferIPv4Stack", "true")
                    SpvController.log("using Tor SOCKS ${addr.hostString}:${addr.port}")
                    Log.i(TAG, "Using Tor SOCKS ${addr.hostString}:${addr.port} for P2P")
                    SpvController.updateTor(true, TorManager.statusFlow.value.bootstrapPercent)
                } else {
                    Log.w(TAG, "Tor is ON but socks address is null; will proceed without proxy")
                    clearSocksProxy()
                    SpvController.updateTor(false, 0)
                }
            } else {
                clearSocksProxy()
                SpvController.updateTor(false, 0)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Tor SOCKS configuration failed: ${t.message}")
            SpvController.updateTor(false, 0)
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
                            val sock: InetSocketAddress = if (preferUnresolvedHostnames) {
                                InetSocketAddress(host, port)
                            } else {
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