package com.dogechat.android.wallet

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dogechat.android.wallet.logging.SpvLogBuffer
import com.dogechat.android.wallet.net.PeerDirectory
import com.dogechat.android.wallet.net.TorManagerWallet
import com.dogechat.android.wallet.net.WalletTorPreferenceManager
import com.dogechat.android.wallet.util.TransactionHelper
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
import java.io.File
import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

        private const val MAX_PEERS = 6
        private const val TOR_WAIT_MS = 45_000L
        private const val TOR_POLL_MS = 300L

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
                val v = prefs.getBoolean(PREF_KEY_SPV_ENABLED, false)
                enabled.value = v
                return v
            }

            fun set(context: Context, turnOn: Boolean) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putBoolean(PREF_KEY_SPV_ENABLED, turnOn).apply()
                enabled.value = turnOn
                instanceRef?.let { if (turnOn) it.startNetwork() else it.stopNetwork() }
            }

            internal fun updateRunning(running: Boolean) {
                val cur = status.value; status.value = cur.copy(running = running)
            }
            internal fun updatePeers(count: Int) {
                val cur = status.value; status.value = cur.copy(peerCount = count)
            }
            internal fun updateSync(p: Int) {
                val cur = status.value; status.value = cur.copy(syncPercent = p)
            }
            internal fun updateTor(running: Boolean, bootstrap: Int) {
                val cur = status.value; status.value = cur.copy(torRunning = running, torBootstrap = bootstrap.coerceIn(0,100))
            }
            internal fun log(line: String) {
                val cur = status.value
                status.value = cur.copy(lastLogLine = line)
                // Mirror to buffer
                SpvLogBuffer.append(line)
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

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        instanceRef = this
        WalletTorPreferenceManager.init(appContext)
        _address.value = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
        SpvController.enabled.value = prefs.getBoolean(PREF_KEY_SPV_ENABLED, false)
        if (SpvController.enabled.value) startNetwork()
    }

    fun startNetwork() {
        scope.launch {
            try {
                Log.i(TAG, "SPV start… net=${params.javaClass.simpleName} port=${params.port}")
                SpvController.log("SPV: starting …")
                BtcContext.propagate(BtcContext(params))

                val app = appContext.applicationContext as Application

                // Wallet-only Tor preference
                val torWanted = WalletTorPreferenceManager.get(appContext) == com.dogechat.android.net.TorMode.ON
                if (torWanted) {
                    TorManagerWallet.start(app)
                    waitForWalletTorReady()
                } else {
                    try { TorManagerWallet.stop() } catch (_: Throwable) {}
                    clearJvmSocks()
                    SpvController.updateTor(false, 0)
                }

                val dir = File(appContext.filesDir, "wallet").apply { if (!exists()) mkdirs() }
                val usingSocks = TorManagerWallet.isRunning()

                val initialPeers: List<PeerAddress> = PeerDirectory.initialPeers(
                    context = appContext,
                    params = params,
                    torMode = usingSocks,
                    preferUnresolved = usingSocks
                )

                if (!usingSocks) {
                    PeerDirectory.resolveDnsSeedsAndPersist(appContext, DOGE_DNS_SEEDS, params.port)
                }

                val k = object : WalletAppKit(params, dir, FILE_PREFIX) {
                    override fun onSetupCompleted() {
                        val fmt = { t: String -> "${timeFmt.format(Date())} $t" }
                        try {
                            val pg = peerGroup()
                            runCatching { pg.setUseLocalhostPeerWhenPossible(false) }
                            runCatching { pg.setMaxConnections(MAX_PEERS) }
                            runCatching { pg.setRequiredServices(0) }

                            initialPeers.forEach { pa ->
                                runCatching { pg.addAddress(pa) }
                                    .onSuccess { Log.i(TAG, "Seeded peer: ${pa.socketAddress?.hostString}:${pa.port}") }
                                    .onFailure { Log.w(TAG, "Seed add failed: ${it.message}") }
                            }

                            if (!usingSocks) {
                                runCatching {
                                    pg.addPeerDiscovery(DnsDiscovery(DOGE_DNS_SEEDS, params))
                                    Log.i(TAG, "DNS discovery enabled: ${DOGE_DNS_SEEDS.joinToString()}")
                                }.onFailure { Log.w(TAG, "DNS discovery failed: ${it.message}") }
                            } else {
                                Log.i(TAG, "DNS discovery disabled (Tor active)")
                            }

                            // Ensure brand-new wallets immediately have an address persisted
                            ensureInitialAddress()

                            // Wallet listeners
                            wallet().addCoinsReceivedEventListener { _: Wallet, _, _, _ ->
                                pushBalance(); pushHistory()
                                SpvController.log(fmt("coins received"))
                            }
                            wallet().addCoinsSentEventListener { _: Wallet, _, _, _ ->
                                pushBalance(); pushHistory()
                                SpvController.log(fmt("coins sent"))
                            }
                            wallet().addChangeEventListener { _ -> pushBalance(); pushHistory() }

                            try {
                                pg.addConnectedEventListener { peer, count ->
                                    _peerCount.value = count
                                    SpvController.updatePeers(count)
                                    val isa: InetSocketAddress? = runCatching {
                                        (peer?.address as? PeerAddress)?.socketAddress
                                    }.getOrNull()
                                    Log.i(TAG, "Peer +1: ${isa?.hostString}:${isa?.port} (count=$count)")
                                    SpvController.log(fmt("peer +1 ($count)"))
                                    isa?.let {
                                        val key = "${it.hostString}:${it.port}"
                                        val existing = PeerDirectory.readDiskPeers(appContext).toMutableSet()
                                        existing.add(key)
                                        PeerDirectory.writeDiskPeers(appContext, existing)
                                    }
                                }
                                pg.addDisconnectedEventListener { peer, count ->
                                    _peerCount.value = count
                                    SpvController.updatePeers(count)
                                    val isa: InetSocketAddress? = runCatching {
                                        (peer?.address as? PeerAddress)?.socketAddress
                                    }.getOrNull()
                                    Log.i(TAG, "Peer -1: ${isa?.hostString}:${isa?.port} (count=$count)")
                                    SpvController.log(fmt("peer -1 ($count)"))
                                }
                            } catch (_: Throwable) {}

                            // Best-block log (portable across versions)
                            runCatching {
                                chain().addNewBestBlockListener { stored ->
                                    val h = runCatching { stored.header.hash.toString() }.getOrElse { "unknown" }
                                    val ht = runCatching { stored.height }.getOrElse { -1 }
                                    SpvController.log("best $ht $h")
                                }
                            }

                            // Import cached WIF early and rescan if needed
                            importCachedWifIfPresent()
                            triggerRescanForImportedIfNeeded()

                            // Initial UI push
                            pushAddress()
                            pushBalance()
                            pushHistory()
                        } catch (t: Throwable) {
                            Log.w(TAG, "onSetupCompleted error: ${t.message}", t)
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
                            if (blocksSoFar % 200 == 0) {
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
                        }
                    })
                }

                if (usingSocks) applyJvmSocks(TorManagerWallet.currentSocks())

                kit = k
                _spvStatus.value = "Connecting"
                SpvController.updateRunning(true)
                SpvController.log("starting peerGroup …")

                k.startAsync()
                k.awaitRunning()

                // Force a download pass to kick progress listeners reliably
                runCatching { k.peerGroup().startBlockChainDownload(object : DownloadProgressTracker() {}) }

                Log.i(TAG, "SPV running. Address=${currentReceiveAddress()}")
                pushAddress(); pushBalance(); pushHistory()
                updateSpvStatus()
            } catch (e: Throwable) {
                Log.e(TAG, "SPV start failed: ${e.message}", e)
                _spvStatus.value = "Error"
                SpvController.updateRunning(false)
                SpvController.log("error: ${e.message}")
                clearJvmSocks()
                TorManagerWallet.stop()
            }
        }
    }

    fun stopNetwork() {
        scope.launch {
            try {
                kit?.apply {
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
                clearJvmSocks()
                TorManagerWallet.stop()
                SpvController.updateTor(false, 0)
                SpvController.log("stopped")
            }
        }
    }

    // Public ops

    fun currentReceiveAddress(): String? = try {
        val cached = prefs.getString(PREF_KEY_CACHED_WIF, null)
        if (!cached.isNullOrBlank()) deriveAddressFromWif(cached)
        else kit?.wallet()?.currentReceiveAddress()?.toString()
            ?: prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
    } catch (_: Throwable) { prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null) }

    fun refreshAddress() {
        scope.launch {
            val addr = kit?.wallet()?.freshReceiveAddress()?.toString()
            if (addr != null) {
                prefs.edit().remove(PREF_KEY_CACHED_WIF).putString(PREF_KEY_RECEIVE_ADDRESS, addr).apply()
                _address.value = addr
            }
        }
    }

    fun refreshNow() {
        scope.launch {
            // Push UI regardless
            pushAddress(); pushBalance(); pushHistory()
            val local = kit
            if (local == null) {
                SpvController.log("refresh skipped (wallet not started)")
                return@launch
            }
            // Only trigger download when running
            runCatching {
                local.peerGroup().startBlockChainDownload(object : DownloadProgressTracker() {
                    override fun doneDownload() {
                        pushBalance(); pushHistory()
                        SpvController.log("manual refresh done")
                    }
                })
            }.onFailure { SpvController.log("refresh trigger failed: ${it.message}") }
        }
    }

    fun sendCoins(toAddress: String, amountDoge: Long, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            val localKit = kit ?: run {
                withContext(Dispatchers.Main) { onResult(false, "Wallet not started") }
                return@launch
            }
            try {
                val address: Address = LegacyAddress.fromBase58(params, toAddress)
                val amount = org.bitcoinj.core.Coin.valueOf(amountDoge * 100_000_000L)
                localKit.wallet().sendCoins(localKit.peerGroup(), address, amount)
                pushBalance(); pushHistory()
                SpvController.log("broadcast requested")
                withContext(Dispatchers.Main) { onResult(true, "Broadcast requested") }
            } catch (e: Throwable) {
                Log.e(TAG, "sendCoins failed: ${e.message}", e)
                SpvController.log("send error: ${e.message}")
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "Unknown error") }
            }
        }
    }

    // WIF/cache/rescan

    fun getOrExportAndCacheWif(): String? = try {
        val cached = prefs.getString(PREF_KEY_CACHED_WIF, null)
        if (!cached.isNullOrBlank()) cached
        else exportCurrentReceivePrivateKeyWif()?.also { persistWif(it, deriveAddressFromWif(it)) }
    } catch (t: Throwable) { Log.w(TAG, "getOrExportAndCacheWif failed: ${t.message}"); null }

    fun exportCurrentReceivePrivateKeyWif(): String? = try {
        val w = kit?.wallet() ?: return null
        val key = w.currentReceiveKey() ?: return null
        key.getPrivateKeyAsWiF(params)
    } catch (t: Throwable) { Log.w(TAG, "export WIF failed: ${t.message}"); null }

    fun getCachedWif(): String? = prefs.getString(PREF_KEY_CACHED_WIF, null)

    fun importPrivateKeyWif(wif: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                val key = DumpedPrivateKey.fromBase58(params, wif).key
                val addr = LegacyAddress.fromKey(params, key).toString()
                val w = kit?.wallet()
                if (w == null) {
                    persistWif(wif, addr)
                    _address.value = addr
                    withContext(Dispatchers.Main) { onResult(true, "WIF cached. It will load when wallet starts.") }
                    return@launch
                }
                val exists = runCatching { w.importedKeys.contains(key) }.getOrDefault(false)
                if (!exists) runCatching { w.importKey(key) }
                persistWif(wif, addr)
                _address.value = addr
                // Rescan only if we imported a brand-new key
                triggerRescanFromBirth(key.creationTimeSeconds)
                withContext(Dispatchers.Main) { onResult(true, "Private key imported") }
            } catch (e: Throwable) {
                Log.e(TAG, "importPrivateKeyWif failed: ${e.message}", e)
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "Import failed") }
            }
        }
    }

    private fun importCachedWifIfPresent() {
        val wif = prefs.getString(PREF_KEY_CACHED_WIF, null) ?: return
        runCatching {
            val w = kit?.wallet() ?: return
            val key = DumpedPrivateKey.fromBase58(params, wif).key
            val exists = runCatching { w.importedKeys.contains(key) }.getOrDefault(false)
            if (!exists) runCatching { w.importKey(key) }
            val addr = LegacyAddress.fromKey(params, key).toString()
            persistWif(wif, addr)
            _address.value = addr
        }.onFailure { Log.w(TAG, "Import cached WIF failed: ${it.message}") }
    }

    private fun persistWif(wif: String, address: String?) {
        prefs.edit().apply {
            putString(PREF_KEY_CACHED_WIF, wif)
            if (!address.isNullOrBlank()) putString(PREF_KEY_RECEIVE_ADDRESS, address)
        }.apply()
    }

    private fun deriveAddressFromWif(wif: String): String? = runCatching {
        val key = DumpedPrivateKey.fromBase58(params, wif).key
        LegacyAddress.fromKey(params, key).toString()
    }.getOrNull()

    private fun triggerRescanForImportedIfNeeded() {
        if (prefs.getString(PREF_KEY_CACHED_WIF, null).isNullOrBlank()) return
        triggerRescanFromBirth(0L)
    }

    private fun triggerRescanFromBirth(birthTimeSecs: Long) {
        val pg = kit?.peerGroup() ?: return
        runCatching { pg.setFastCatchupTimeSecs(birthTimeSecs) }
        runCatching {
            val m = pg.javaClass.methods.firstOrNull { it.name == "recalculateFastCatchupAndFilter" && it.parameterTypes.isEmpty() }
            if (m != null) m.invoke(pg) else runCatching {
                val enumClass = Class.forName("org.bitcoinj.core.PeerGroup\$FilterRecalculateMode")
                val forceSend = enumClass.enumConstants?.firstOrNull()
                val m2 = pg.javaClass.getMethod("recalculateFastCatchupAndFilter", enumClass)
                m2.invoke(pg, forceSend)
            }
        }
        runCatching {
            pg.startBlockChainDownload(object : DownloadProgressTracker() {
                override fun doneDownload() {
                    pushBalance(); pushHistory()
                    SpvController.log("rescan complete")
                }
            })
            SpvController.log("rescan started")
        }.onFailure { SpvController.log("rescan failed: ${it.message}") }
    }

    fun wipeWalletData(): Boolean = try {
        runCatching { kit?.stopAsync(); kit?.awaitTerminated() }
        kit = null
        runCatching {
            val dir = File(appContext.filesDir, "wallet"); if (dir.exists()) dir.deleteRecursively()
        }
        prefs.edit().remove(PREF_KEY_RECEIVE_ADDRESS).remove(PREF_KEY_CACHED_WIF).putBoolean(PREF_KEY_SPV_ENABLED, false).apply()
        _address.value = null
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
        clearJvmSocks()
        TorManagerWallet.stop()
        true
    } catch (t: Throwable) {
        Log.e(TAG, "wipeWalletData failed: ${t.message}", t)
        false
    }

    // Internals

    private fun ensureInitialAddress() {
        val hasPrefAddr = !prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null).isNullOrBlank()
        val hasCachedWif = !prefs.getString(PREF_KEY_CACHED_WIF, null).isNullOrBlank()
        if (hasPrefAddr || hasCachedWif) return
        val addr = runCatching { kit?.wallet()?.currentReceiveAddress()?.toString() }.getOrNull()
            ?: runCatching { kit?.wallet()?.freshReceiveAddress()?.toString() }.getOrNull()
        if (!addr.isNullOrBlank()) {
            prefs.edit().putString(PREF_KEY_RECEIVE_ADDRESS, addr).apply()
            _address.value = addr
            SpvController.log("new address $addr")
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
                    return
                }
            }
            val hd = kit?.wallet()?.currentReceiveAddress()?.toString()
            if (!hd.isNullOrBlank()) {
                prefs.edit().putString(PREF_KEY_RECEIVE_ADDRESS, hd).apply()
                _address.value = hd
            } else {
                _address.value = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
            }
        } catch (_: Throwable) {
            _address.value = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
        }
    }

    private fun pushBalance() {
        val w = kit?.wallet() ?: return
        val balance = w.balance.toPlainString()
        _balance.value = "$balance DOGE"
    }

    private fun pushHistory() {
        val w = kit?.wallet() ?: return
        _history.value = TransactionHelper.buildRows(w)
    }

    private fun updateSpvStatus() {
        _spvStatus.value = when {
            _peerCount.value == 0 -> "Not Connected"
            _syncPercent.value < 100 -> "Syncing"
            else -> "Synced"
        }
    }

    // Tor helpers

    private suspend fun waitForWalletTorReady() {
        var waited = 0L
        while (waited < TOR_WAIT_MS) {
            val s = TorManagerWallet.status.value
            SpvController.updateTor(s.running, s.bootstrapPercent)
            if (s.running && s.bootstrapPercent >= 100 && s.socks != null) return
            delay(TOR_POLL_MS)
            waited += TOR_POLL_MS
        }
        SpvController.log("Tor not ready in ${TOR_WAIT_MS}ms; proceeding")
    }

    private fun applyJvmSocks(socks: InetSocketAddress?) {
        if (socks == null) return
        runCatching {
            System.setProperty("socksProxyHost", socks.hostString)
            System.setProperty("socksProxyPort", socks.port.toString())
            System.setProperty("socksProxyVersion", "5")
            System.setProperty("java.net.preferIPv6Addresses", "false")
            System.setProperty("java.net.preferIPv4Stack", "true")
        }
    }

    private fun clearJvmSocks() {
        runCatching { System.clearProperty("socksProxyHost") }
        runCatching { System.clearProperty("socksProxyPort") }
        runCatching { System.clearProperty("socksProxyVersion") }
    }
}