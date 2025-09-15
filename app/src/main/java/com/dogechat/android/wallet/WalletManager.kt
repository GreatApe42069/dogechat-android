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
import org.bitcoinj.net.discovery.DnsDiscovery
import org.bitcoinj.wallet.Wallet
import org.libdohj.params.DogecoinMainNetParams
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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

        private const val PEERS_ASSET = "dogecoin_peers.csv"     // read-only bundled peers (optional)
        private const val PEERS_FILE = "dogecoin_peers.csv"      // writable peers cache in filesDir
        private const val TOR_WAIT_MS = 20_000L
        private const val TOR_POLL_MS = 300L
        private const val MAX_PEERS = 6
        private const val DOGE_PORT = 22556

        // Hardcoded Dogecoin DNS seeds (mainnet)
        private val DOGE_DNS_SEEDS = arrayOf(
            "seed.dogecoin.com",
            "seed.multidoge.org",
            "seed2.multidoge.org",
            "seed.dogechain.info"
        )

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

    // in-memory peer cache to reduce disk writes
    private val discoveredPeers = ConcurrentHashMap.newKeySet<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        instanceRef = this

        // Prime address from prefs
        _addressReady.value = !prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null).isNullOrBlank()
        _address.value = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)

        val shouldStart = prefs.getBoolean(PREF_KEY_SPV_ENABLED, false)
        SpvController.enabled.value = shouldStart
        if (shouldStart) startNetwork() else SpvController.updateRunning(false)
    }

    fun startNetwork() {
        scope.launch {
            try {
                Log.i(TAG, "SPV start requested … net=${params.javaClass.simpleName} port=${params.port}")
                SpvController.log("SPV: starting …")
                BtcContext.propagate(BtcContext(params))

                // Tor / SOCKS setup (if enabled by user elsewhere)
                configureTorSocksIfAvailable()

                // Prime peers file: resolve DNS seeds and persist (non-blocking best-effort)
                resolveDnsSeedsAndPersist()

                val dir = File(appContext.filesDir, "wallet")
                if (!dir.exists()) dir.mkdirs()

                val usingSocks = System.getProperty("socksProxyHost")?.isNotBlank() == true
                val initialPeers = loadPeersFromDiskAndAssets(params, preferUnresolvedHostnames = usingSocks)
                Log.i(TAG, "Bootstrap peers loaded: ${initialPeers.size} (disk+assets). SOCKS=$usingSocks")

                val k = object : WalletAppKit(params, dir, FILE_PREFIX) {
                    override fun onSetupCompleted() {
                        try {
                            val pg = peerGroup()
                            try { pg.setUseLocalhostPeerWhenPossible(false) } catch (_: Throwable) {}
                            try { pg.setMaxConnections(MAX_PEERS) } catch (_: Throwable) {}

                            // Feed any known peers (do NOT call setPeerNodes which disables discovery)
                            if (initialPeers.isNotEmpty()) {
                                initialPeers.forEach { pa ->
                                    runCatching { pg.addAddress(pa) }
                                        .onSuccess {
                                            val host = runCatching { pa.addr?.hostAddress }.getOrNull()
                                            Log.i(TAG, "Seeded peer: ${host ?: "host?"}:${pa.port}")
                                        }
                                        .onFailure {
                                            val host = runCatching { pa.addr?.hostAddress }.getOrNull()
                                            Log.w(TAG, "Failed to add seed peer ${host ?: "host?"}:${pa.port}: ${it.message}")
                                        }
                                }
                            }

                            // Add DNS discovery (only when not leaking through clearnet if Tor is enabled)
                            if (!usingSocks) {
                                try {
                                    // NOTE: in your bitcoinj/libdohj combo this constructor expects (String[] seeds, NetworkParameters)
                                    pg.addPeerDiscovery(DnsDiscovery(DOGE_DNS_SEEDS, params))
                                    Log.i(TAG, "DNS discovery enabled seeds=${DOGE_DNS_SEEDS.joinToString()}")
                                } catch (t: Throwable) {
                                    Log.w(TAG, "Enabling DNS discovery failed: ${t.message}")
                                }
                            } else {
                                Log.i(TAG, "Skipping DNS discovery (SOCKS active)")
                            }

                            // Import any cached WIF so address/UI reflect imported key
                            importCachedWifIfPresent()
                            // Ensure chain covers imported keys
                            triggerRescanForImportedIfNeeded()

                            // Push initial UI state
                            pushAddress()
                            pushBalance()
                            pushHistory()

                            val fmt = { t: String -> "${timeFmt.format(Date())} $t" }
                            val w = wallet()

                            w.addCoinsReceivedEventListener { _: Wallet, _, _, _ ->
                                pushBalance(); pushHistory()
                                SpvController.log(fmt("coins received"))
                            }
                            w.addCoinsSentEventListener { _: Wallet, _, _, _ ->
                                pushBalance(); pushHistory()
                                SpvController.log(fmt("coins sent"))
                            }
                            w.addChangeEventListener { _ -> pushBalance(); pushHistory() }

                            // Hook peer connect/disconnect for logs and persistence
                            try {
                                pg.addConnectedEventListener { peer, count ->
                                    _peerCount.value = count
                                    SpvController.updatePeers(count)
                                    val isa: InetSocketAddress? = runCatching {
                                        (peer?.address as? PeerAddress)?.socketAddress
                                    }.getOrNull()
                                    val addrStr = isa?.let { "${it.hostString}:${it.port}" } ?: "unknown"
                                    Log.i(TAG, "Peer connected: $addrStr (count=$count)")
                                    SpvController.log(fmt("peer +1 ($count)"))
                                    if (isa != null) persistPeer(isa, params.port)
                                }
                                pg.addDisconnectedEventListener { peer, count ->
                                    _peerCount.value = count
                                    SpvController.updatePeers(count)
                                    val isa: InetSocketAddress? = runCatching {
                                        (peer?.address as? PeerAddress)?.socketAddress
                                    }.getOrNull()
                                    val addrStr = isa?.let { "${it.hostString}:${it.port}" } ?: "unknown"
                                    Log.i(TAG, "Peer disconnected: $addrStr (count=$count)")
                                    SpvController.log(fmt("peer -1 ($count)"))
                                }
                            } catch (_: Throwable) { /* ignore */ }
                        } catch (t: Throwable) {
                            Log.w(TAG, "onSetupCompleted failed: ${t.message}", t)
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
                            if (blocksSoFar % 250 == 0) {
                                Log.i(TAG, "Sync $p% ($blocksSoFar blocks)")
                                SpvController.log("sync $p% ($blocksSoFar blocks)")
                            }
                        }
                        override fun doneDownload() {
                            _syncPercent.value = 100
                            SpvController.updateSync(100)
                            pushBalance()
                            pushHistory()
                            _spvStatus.value = "Synced"
                            SpvController.log("sync complete")
                            Log.i(TAG, "Sync complete")
                        }
                    })
                }

                kit = k

                _spvStatus.value = "Connecting"
                SpvController.updateRunning(true)
                SpvController.log("starting peerGroup …")

                k.startAsync()
                k.awaitRunning()

                Log.i(TAG, "SPV running. Active address=${currentReceiveAddress()}")
                SpvController.log("running")

                // Final push after running to reflect steady state
                pushAddress()
                pushBalance()
                pushHistory()
                updateSpvStatus()
            } catch (e: Throwable) {
                Log.e(TAG, "SPV start failed: ${e.message}", e)
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
                    Log.i(TAG, "Stopping SPV …")
                    SpvController.log("stopping …")
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

    // Prefer imported WIF address if cached; fallback to HD receive; then pref
    fun currentReceiveAddress(): String? = try {
        val cached = prefs.getString(PREF_KEY_CACHED_WIF, null)
        if (!cached.isNullOrBlank()) {
            deriveAddressFromWif(cached) ?: prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
        } else {
            kit?.wallet()?.currentReceiveAddress()?.toString()
                ?: prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
        }
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
                Log.i(TAG, "New HD receive address generated")
                SpvController.log("new address")
            }
        }
    }

    private fun pushAddress() {
        try {
            val cachedWif = prefs.getString(PREF_KEY_CACHED_WIF, null)
            if (!cachedWif.isNullOrBlank()) {
                val wifAddr = deriveAddressFromWif(cachedWif)
                if (!wifAddr.isNullOrBlank()) {
                    prefs.edit().putString(PREF_KEY_RECEIVE_ADDRESS, wifAddr).apply()
                    _address.value = wifAddr
                    _addressReady.value = true
                    return
                }
            }
            val prev = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
            val addr = kit?.wallet()?.currentReceiveAddress()?.toString()
            if (addr != null) {
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

    // Manual refresh: push UI and nudge SPV to download
    fun refreshNow() {
        scope.launch {
            Log.i(TAG, "Manual refreshNow()")
            pushAddress()
            pushBalance()
            pushHistory()
            try {
                kit?.peerGroup()?.startBlockChainDownload(object : DownloadProgressTracker() {
                    override fun doneDownload() {
                        pushBalance(); pushHistory()
                        Log.i(TAG, "Manual refresh download pass done")
                    }
                })
            } catch (t: Throwable) {
                Log.w(TAG, "refreshNow download trigger failed: ${t.message}")
            }
        }
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
                    persistWif(wif, deriveAddressFromWif(wif))
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
                    // Cache for later and reflect immediately
                    persistWif(wif, addr)
                    _address.value = addr
                    _addressReady.value = true
                    withContext(Dispatchers.Main) { onResult(true, "WIF cached. It will be loaded next time the wallet starts.") }
                    return@launch
                }

                val already = try { w.importedKeys.contains(key) } catch (_: Throwable) { false }
                if (!already) {
                    try { w.importKey(key) } catch (_: Throwable) { /* ignore */ }
                }
                persistWif(wif, addr)
                _address.value = addr
                _addressReady.value = true
                SpvController.log("imported WIF; addr=$addr")

                // IMPORTANT: trigger a rescan so balance/history show up
                triggerRescanFromBirth(key.creationTimeSeconds)

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
                try { w.importKey(key) } catch (_: Throwable) { /* ignore */ }
            }
            val addr = LegacyAddress.fromKey(params, key).toString()
            // Persist and reflect imported address as active
            persistWif(wif, addr)
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

    private fun deriveAddressFromWif(wif: String): String? = try {
        val key = DumpedPrivateKey.fromBase58(params, wif).key
        LegacyAddress.fromKey(params, key).toString()
    } catch (t: Throwable) {
        Log.w(TAG, "deriveAddressFromWif failed: ${t.message}")
        null
    }

    // --- Rescan helpers ------------------------------------------------

    private fun triggerRescanForImportedIfNeeded() {
        val wif = prefs.getString(PREF_KEY_CACHED_WIF, null) ?: return
        val birth = 0L // conservative: full rescan; can optimize with user-provided birthday
        triggerRescanFromBirth(birth)
    }

    private fun triggerRescanFromBirth(birthTimeSecs: Long) {
        val pg = kit?.peerGroup() ?: return
        try { pg.setFastCatchupTimeSecs(birthTimeSecs) } catch (_: Throwable) {}
        try {
            // Update bloom filters to include imported keys. Support multiple bitcoinj versions.
            val m = pg.javaClass.methods.firstOrNull {
                it.name == "recalculateFastCatchupAndFilter" && it.parameterTypes.isEmpty()
            }
            if (m != null) {
                m.invoke(pg)
            } else {
                runCatching {
                    val enumClass = Class.forName("org.bitcoinj.core.PeerGroup\$FilterRecalculateMode")
                    val forceSend = enumClass.enumConstants?.firstOrNull()
                    val m2 = pg.javaClass.getMethod("recalculateFastCatchupAndFilter", enumClass)
                    m2.invoke(pg, forceSend)
                }
            }
        } catch (_: Throwable) {}
        try {
            pg.startBlockChainDownload(object : DownloadProgressTracker() {
                override fun doneDownload() {
                    pushBalance()
                    pushHistory()
                    SpvController.log("rescan complete")
                    Log.i(TAG, "Rescan complete")
                }
            })
            SpvController.log("rescan started")
            Log.i(TAG, "Rescan started (birth=$birthTimeSecs)")
        } catch (t: Throwable) {
            Log.w(TAG, "triggerRescanFromBirth failed: ${t.message}")
        }
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
    // Balance & history push

    private fun pushBalance() {
        val w = kit?.wallet() ?: return
        val amount = w.balance.toPlainString()
        _balance.value = "$amount DOGE"
        Log.i(TAG, "Balance update: $amount DOGE")
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
        Log.i(TAG, "History rows=${rows.size}")
    }

    private fun updateSpvStatus() {
        _spvStatus.value = when {
            _peerCount.value == 0 -> "Not Connected"
            _syncPercent.value < 100 -> "Syncing"
            else -> "Synced"
        }
    }

    // ------------------------------------------------------------------
    // Tor SOCKS

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
                    Log.w(TAG, "Tor ON but socks address is null; clearing SOCKS")
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

    // ------------------------------------------------------------------
    // Peer seeding, persistence, and DNS bootstrap

    private fun peersFile(): File = File(appContext.filesDir, PEERS_FILE)

    // Best-effort DNS seed resolution to populate local peers file before SPV starts
    private fun resolveDnsSeedsAndPersist() {
        scope.launch {
            try {
                val set = LinkedHashSet<String>()
                DOGE_DNS_SEEDS.forEach { host ->
                    runCatching {
                        val addrs = InetAddress.getAllByName(host)
                        addrs.forEach { ip ->
                            val key = "${ip.hostAddress}:$DOGE_PORT"
                            set.add(key)
                        }
                    }.onFailure { Log.w(TAG, "DNS seed resolve failed for $host: ${it.message}") }
                }
                if (set.isNotEmpty()) {
                    Log.i(TAG, "DNS bootstrap resolved ${set.size} peers")
                    // merge with existing disk peers
                    val existing = readPeerLines().toMutableSet()
                    existing.addAll(set)
                    writePeers(existing)
                } else {
                    Log.w(TAG, "DNS bootstrap returned no peers")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "DNS bootstrap error: ${t.message}")
            }
        }
    }

    private fun loadPeersFromDiskAndAssets(
        params: NetworkParameters,
        preferUnresolvedHostnames: Boolean
    ): List<PeerAddress> {
        val out = mutableListOf<PeerAddress>()
        // 1) disk peers (writable)
        try {
            val diskPeers = readPeerLines()
            diskPeers.forEach { line ->
                val entry = line.trim()
                if (entry.isEmpty() || entry.startsWith("#")) return@forEach
                val host: String
                val port: Int
                if (entry.contains(":")) {
                    val parts = entry.split(":")
                    host = parts[0].trim()
                    port = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: params.port
                } else {
                    host = entry
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
                    Log.w(TAG, "Bad disk peer '$entry': ${e.message}")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed reading disk peers: ${t.message}")
        }

        // 2) assets fallback (read-only)
        if (out.isEmpty()) {
            try {
                val am = appContext.assets
                if (am.list("")?.contains(PEERS_ASSET) == true) {
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
                                    Log.w(TAG, "Bad asset peer '$line': ${e.message}")
                                }
                            }
                        }
                    }
                } else {
                    Log.i(TAG, "No $PEERS_ASSET in assets; rely on DNS discovery")
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed loading $PEERS_ASSET: ${t.message}")
            }
        }

        return out
    }

    private fun readPeerLines(): List<String> {
        val file = peersFile()
        if (!file.exists()) return emptyList()
        return runCatching { file.readLines() }.getOrElse { emptyList() }
    }

    private fun writePeers(lines: Set<String>) {
        val file = peersFile()
        runCatching {
            FileWriter(file, false).use { fw ->
                lines.forEach { fw.write(it.trim() + "\n") }
            }
        }.onSuccess { Log.i(TAG, "Peers persisted to ${file.absolutePath} (${lines.size})") }
            .onFailure { Log.w(TAG, "Failed writing peers: ${it.message}") }
    }

    private fun persistPeer(isa: InetSocketAddress, defaultPort: Int) {
        val host = isa.hostString ?: isa.address?.hostAddress ?: return
        val port = if (isa.port > 0) isa.port else defaultPort
        val key = "$host:$port"
        if (discoveredPeers.add(key)) {
            // batch flush occasionally to avoid IO thrash
            scope.launch {
                val existing = readPeerLines().toMutableSet()
                existing.add(key)
                writePeers(existing)
            }
        }
    }
}