package com.dogechat.android.wallet

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dogechat.android.wallet.logging.AppLog
import com.dogechat.android.wallet.logging.AppLog.Channel
import com.dogechat.android.wallet.logging.SpvLogBuffer
import com.dogechat.android.wallet.net.PeerDirectory
import com.dogechat.android.wallet.net.TorManagerWallet
import com.dogechat.android.wallet.net.WalletTorPreferenceManager
import com.dogechat.android.wallet.util.TransactionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bitcoinj.core.Address
import org.bitcoinj.core.DumpedPrivateKey
import org.bitcoinj.core.ECKey
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

/**
 * WalletManager with enhanced logging, keep-alive, crash handling.
 */
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
        private const val KEEP_ALIVE_INTERVAL_MS = 15_000L

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
                AppLog.action("SpvToggle", "set", "turnOn=$turnOn")
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
                SpvLogBuffer.append(line)
                AppLog.d(Channel.SPV, TAG, line)
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

    private val coroutineErrorHandler = CoroutineExceptionHandler { _, t ->
        AppLog.crash(TAG, "Coroutine exception", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + coroutineErrorHandler)

    private val params: NetworkParameters = DogecoinMainNetParams.get()
    @Volatile private var kit: WalletAppKit? = null

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
        installGlobalCrashHandler()
        WalletTorPreferenceManager.init(appContext)
        ensurePreGeneratedAddressIfMissing()
        _address.value = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
        SpvController.enabled.value = prefs.getBoolean(PREF_KEY_SPV_ENABLED, false)
        AppLog.state(Channel.SPV, TAG, "init.spvEnabled", SpvController.enabled.value)
        if (SpvController.enabled.value) startNetwork()
        launchKeepAlive()
    }

    // Crash handler
    private fun installGlobalCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            AppLog.crash("Global", "Uncaught in thread ${t.name}", e)
            prev?.uncaughtException(t, e) ?: run {
                // rethrow to let system crash
                throw e
            }
        }
    }

    private fun ensurePreGeneratedAddressIfMissing() {
        val hasWif = !prefs.getString(PREF_KEY_CACHED_WIF, null).isNullOrBlank()
        val hasAddr = !prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null).isNullOrBlank()
        if (hasWif || hasAddr) {
            AppLog.d(Channel.SPV, TAG, "Pre-gen skipped (existing address or WIF)")
            return
        }
        runCatching {
            val key = ECKey()
            val wif = key.getPrivateKeyAsWiF(params)
            val addr = LegacyAddress.fromKey(params, key).toString()
            prefs.edit()
                .putString(PREF_KEY_CACHED_WIF, wif)
                .putString(PREF_KEY_RECEIVE_ADDRESS, addr)
                .apply()
            _address.value = addr
            SpvController.log("pre-generated address $addr")
            AppLog.i(Channel.SPV, TAG, "Pre-generated address=$addr wifLen=${wif.length}")
        }.onFailure {
            AppLog.w(Channel.SPV, TAG, "Pre-generate failed: ${it.message}", it)
        }
    }

    fun startNetwork() {
        scope.launch {
            if (kit != null) {
                AppLog.d(Channel.SPV, TAG, "startNetwork: kit already exists; ignoring")
                return@launch
            }
            try {
                AppLog.i(Channel.SPV, TAG, "SPV start net=${params.javaClass.simpleName} port=${params.port}")
                SpvController.log("SPV: starting …")
                BtcContext.propagate(BtcContext(params))

                val app = appContext.applicationContext as Application

                val torWanted = WalletTorPreferenceManager.get(appContext) == com.dogechat.android.net.TorMode.ON
                AppLog.state(Channel.SPV, TAG, "walletTorWanted", torWanted)
                if (torWanted) {
                    TorManagerWallet.start(app)
                    waitForWalletTorReady()
                } else {
                    TorManagerWallet.stop()
                    clearJvmSocks()
                    SpvController.updateTor(false, 0)
                    SpvController.log("wallet tor disabled")
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
                    SpvController.log("dns discovery seeds=${DOGE_DNS_SEEDS.joinToString()}")
                    PeerDirectory.resolveDnsSeedsAndPersist(appContext, DOGE_DNS_SEEDS, params.port)
                } else {
                    SpvController.log("dns discovery skipped (tor active)")
                }

                val k = object : WalletAppKit(params, dir, FILE_PREFIX) {
                    override fun onSetupCompleted() {
                        val fmt = { t: String -> "${timeFmt.format(Date())} $t" }
                        AppLog.i(Channel.SPV, TAG, "onSetupCompleted")
                        try {
                            val pg = peerGroup()
                            runCatching { pg.setUseLocalhostPeerWhenPossible(false) }
                            runCatching { pg.setMaxConnections(MAX_PEERS) }
                            runCatching { pg.setRequiredServices(0) }

                            initialPeers.forEach { pa ->
                                runCatching { pg.addAddress(pa) }
                                    .onSuccess { SpvController.log("seed ${pa.socketAddress?.hostString}:${pa.port}") }
                                    .onFailure { AppLog.w(Channel.SPV, TAG, "Seed add failed: ${it.message}") }
                            }

                            if (!usingSocks) {
                                runCatching {
                                    pg.addPeerDiscovery(DnsDiscovery(DOGE_DNS_SEEDS, params))
                                    SpvController.log("dns discovery enabled")
                                }.onFailure {
                                    SpvController.log("dns discovery failed: ${it.message}")
                                }
                            }

                            ensureInitialAddress()

                            wallet().addCoinsReceivedEventListener { _: Wallet, tx, _, _ ->
                                SpvController.log(fmt("coins received ${tx.txId}"))
                                pushBalance(); pushHistory()
                            }
                            wallet().addCoinsSentEventListener { _: Wallet, tx, _, _ ->
                                SpvController.log(fmt("coins sent ${tx.txId}"))
                                pushBalance(); pushHistory()
                            }
                            wallet().addChangeEventListener {
                                AppLog.d(Channel.SPV, TAG, "wallet change -> update balance/history")
                                pushBalance(); pushHistory()
                            }

                            attachConfidenceListenerIfAvailable()

                            // Use the same 'pg' defined above; do not redeclare
                            try {
                                pg.addConnectedEventListener { peer, count ->
                                    _peerCount.value = count
                                    SpvController.updatePeers(count)
                                    val isa = (peer?.address as? PeerAddress)?.socketAddress
                                    SpvController.log(fmt("peer +1 ($count) ${isa?.hostString}:${isa?.port}"))
                                }
                                pg.addDisconnectedEventListener { peer, count ->
                                    _peerCount.value = count
                                    SpvController.updatePeers(count)
                                    val isa = (peer?.address as? PeerAddress)?.socketAddress
                                    SpvController.log(fmt("peer -1 ($count) ${isa?.hostString}:${isa?.port}"))
                                }
                            } catch (_: Throwable) {}

                            runCatching {
                                chain().addNewBestBlockListener { stored ->
                                    val h = runCatching { stored.header.hash.toString() }.getOrElse { "unknown" }
                                    val ht = runCatching { stored.height }.getOrElse { -1 }
                                    SpvController.log("best $ht $h")
                                }
                            }

                            importCachedWifIfPresent()
                            triggerRescanForImportedIfNeeded()

                            pushAddress()
                            pushBalance()
                            pushHistory()
                        } catch (t: Throwable) {
                            AppLog.w(Channel.SPV, TAG, "onSetupCompleted error: ${t.message}", t)
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
                SpvController.log("kit running")

                runCatching { k.peerGroup().startBlockChainDownload(object : DownloadProgressTracker() {}) }
                    .onFailure { AppLog.w(Channel.SPV, TAG, "force download start failed: ${it.message}") }

                AppLog.i(Channel.SPV, TAG, "SPV running. Address=${currentReceiveAddress()}")
                pushAddress(); pushBalance(); pushHistory()
                updateSpvStatus()
            } catch (e: Throwable) {
                AppLog.e(Channel.SPV, TAG, "SPV start failed: ${e.message}", e)
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
            AppLog.i(Channel.SPV, TAG, "Stop requested")
            try {
                kit?.apply {
                    SpvController.log("stopping …")
                    stopAsync()
                    awaitTerminated()
                }
            } catch (e: Throwable) {
                AppLog.e(Channel.SPV, TAG, "Failed to stop wallet kit: ${e.message}", e)
            } finally {
                kit = null
                _syncPercent.value = 0
                _peerCount.value = 0
                _spvStatus.value = "Not Connected"
                SpvController.updateRunning(false)
                SpvController.updatePeers(0)
                SpvController.updateSync(0)
                clearJvmSocks()
                SpvController.log("stopped")
            }
        }
    }

    private fun launchKeepAlive() {
        scope.launch {
            AppLog.i(Channel.SPV, TAG, "KeepAlive loop started")
            while (isActive) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                val enabled = SpvController.enabled.value
                val localKit = kit
                val running = localKit != null && localKit.isRunning
                if (enabled && !running) {
                    AppLog.w(Channel.SPV, TAG, "KeepAlive: SPV enabled but not running -> restart")
                    startNetwork()
                }
                // Wallet Tor keep-alive
                val wantTor = WalletTorPreferenceManager.get(appContext) == com.dogechat.android.net.TorMode.ON
                if (wantTor && !TorManagerWallet.isRunning()) {
                    AppLog.w(Channel.SPV, TAG, "KeepAlive: Wallet Tor wanted but not running -> start")
                    runCatching { TorManagerWallet.start(appContext.applicationContext as Application) }
                }
            }
        }
    }

    // Public operations

    fun currentReceiveAddress(): String? = runCatching {
        val cached = prefs.getString(PREF_KEY_CACHED_WIF, null)
        if (!cached.isNullOrBlank()) deriveAddressFromWif(cached)
        else kit?.wallet()?.currentReceiveAddress()?.toString()
            ?: prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
    }.getOrNull()

    fun refreshAddress() {
        AppLog.action("WalletScreen", "refreshAddress")
        scope.launch {
            val addr = kit?.wallet()?.freshReceiveAddress()?.toString()
            AppLog.i(Channel.SPV, TAG, "refreshAddress new=$addr")
            if (addr != null) {
                prefs.edit().remove(PREF_KEY_CACHED_WIF).putString(PREF_KEY_RECEIVE_ADDRESS, addr).apply()
                _address.value = addr
                SpvController.log("new address $addr")
            }
        }
    }

    fun refreshNow() {
        AppLog.action("WalletScreen", "refreshNow")
        scope.launch {
            pushAddress(); pushBalance(); pushHistory()
            val local = kit
            if (local == null) {
                SpvController.log("refresh skipped (wallet not started)")
                return@launch
            }
            if (!local.isRunning) {
                SpvController.log("refresh aborted (kit not running)")
                return@launch
            }
            runCatching {
                local.peerGroup().startBlockChainDownload(object : DownloadProgressTracker() {
                    override fun doneDownload() {
                        pushBalance(); pushHistory()
                        SpvController.log("manual refresh done")
                    }
                })
                SpvController.log("manual refresh triggered")
            }.onFailure {
                SpvController.log("refresh trigger failed: ${it.message}")
                AppLog.w(Channel.SPV, TAG, "refreshNow failed: ${it.message}", it)
            }
        }
    }

    fun sendCoins(toAddress: String, amountDoge: Long, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        AppLog.action("WalletScreen", "sendCoins", "to=$toAddress amount=$amountDoge")
        scope.launch {
            val localKit = kit ?: run {
                withContext(Dispatchers.Main) { onResult(false, "Wallet not started") }
                return@launch
            }
            try {
                val address: Address = LegacyAddress.fromBase58(params, toAddress)
                val amount = org.bitcoinj.core.Coin.valueOf(amountDoge * 100_000_000L)
                AppLog.i(Channel.SPV, TAG, "Attempting send tx amount=$amount to=$toAddress")
                localKit.wallet().sendCoins(localKit.peerGroup(), address, amount).let { req ->
                    SpvController.log("broadcast requested tx=${req.tx.txId}")
                    AppLog.i(Channel.SPV, TAG, "Broadcast requested tx=${req.tx.txId}")
                }
                pushBalance(); pushHistory()
                withContext(Dispatchers.Main) { onResult(true, "Broadcast requested") }
            } catch (e: Throwable) {
                AppLog.e(Channel.SPV, TAG, "sendCoins failed: ${e.message}", e)
                SpvController.log("send error: ${e.message}")
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "Unknown error") }
            }
        }
    }

    // WIF/cache/rescan

    fun getOrExportAndCacheWif(): String? = runCatching {
        val cached = prefs.getString(PREF_KEY_CACHED_WIF, null)
        if (!cached.isNullOrBlank()) {
            AppLog.d(Channel.SPV, TAG, "getOrExportAndCacheWif using cached")
            cached
        } else exportCurrentReceivePrivateKeyWif()?.also {
            persistWif(it, deriveAddressFromWif(it))
            AppLog.i(Channel.SPV, TAG, "exported WIF length=${it.length}")
        }
    }.getOrElse {
        AppLog.w(Channel.SPV, TAG, "getOrExportAndCacheWif failed: ${it.message}", it)
        null
    }

    fun exportCurrentReceivePrivateKeyWif(): String? = runCatching {
        val w = kit?.wallet() ?: return null
        val key = w.currentReceiveKey() ?: return null
        key.getPrivateKeyAsWiF(params)
    }.getOrElse {
        AppLog.w(Channel.SPV, TAG, "export WIF failed: ${it.message}", it)
        null
    }

    fun getCachedWif(): String? = prefs.getString(PREF_KEY_CACHED_WIF, null)

    fun importPrivateKeyWif(wif: String, onResult: (Boolean, String) -> Unit) {
        AppLog.action("PrivateKeyImport", "importWIF", "len=${wif.length}")
        scope.launch {
            try {
                val key = DumpedPrivateKey.fromBase58(params, wif).key
                val addr = LegacyAddress.fromKey(params, key).toString()
                val w = kit?.wallet()
                if (w == null) {
                    persistWif(wif, addr)
                    _address.value = addr
                    SpvController.log("WIF cached (wallet idle)")
                    withContext(Dispatchers.Main) { onResult(true, "WIF cached. Loads when wallet starts.") }
                    return@launch
                }
                val exists = runCatching { w.importedKeys.contains(key) }.getOrDefault(false)
                if (!exists) runCatching { w.importKey(key) }.onSuccess {
                    SpvController.log("key imported")
                }
                persistWif(wif, addr)
                _address.value = addr
                triggerRescanFromBirth(key.creationTimeSeconds)
                withContext(Dispatchers.Main) { onResult(true, "Private key imported") }
            } catch (e: Throwable) {
                AppLog.e(Channel.SPV, TAG, "importPrivateKeyWif failed: ${e.message}", e)
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
            SpvController.log("cached WIF loaded")
        }.onFailure { AppLog.w(Channel.SPV, TAG, "Import cached WIF failed: ${it.message}", it) }
    }

    private fun persistWif(wif: String, address: String?) {
        prefs.edit().apply {
            putString(PREF_KEY_CACHED_WIF, wif)
            if (!address.isNullOrBlank()) putString(PREF_KEY_RECEIVE_ADDRESS, address)
        }.apply()
        AppLog.d(Channel.SPV, TAG, "persistWif done address=$address")
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
        AppLog.i(Channel.SPV, TAG, "triggerRescan birth=$birthTimeSecs")
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
        }.onFailure {
            SpvController.log("rescan failed: ${it.message}")
            AppLog.w(Channel.SPV, TAG, "rescan failed: ${it.message}", it)
        }
    }

    fun wipeWalletData(): Boolean = runCatching {
        AppLog.action("WalletScreen", "wipeWalletData")
        runCatching { kit?.stopAsync(); kit?.awaitTerminated() }
        kit = null
        runCatching {
            val dir = File(appContext.filesDir, "wallet"); if (dir.exists()) dir.deleteRecursively()
        }
        prefs.edit()
            .remove(PREF_KEY_RECEIVE_ADDRESS)
            .remove(PREF_KEY_CACHED_WIF)
            .putBoolean(PREF_KEY_SPV_ENABLED, false)
            .apply()
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
    }.onFailure {
        AppLog.e(Channel.SPV, TAG, "wipeWalletData failed: ${it.message}", it)
    }.getOrDefault(false)

    // Internal

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

    private fun attachConfidenceListenerIfAvailable() {
        val w = kit?.wallet() ?: return
        // bitcoinj versions differ; attempt reflection
        val method = w.javaClass.methods.firstOrNull { it.name == "addTransactionConfidenceEventListener" }
        if (method != null) {
            runCatching {
                val listenerProxy = java.lang.reflect.Proxy.newProxyInstance(
                    w.javaClass.classLoader,
                    arrayOf(method.parameterTypes[0])
                ) { _, m, args ->
                    if (m.name == "onTransactionConfidenceChanged" && args?.size ?: 0 >= 2) {
                        val tx = args[1]
                        val txId = runCatching {
                            val f = tx.javaClass.methods.firstOrNull { it.name == "getTxId" }?.invoke(tx)
                            f.toString()
                        }.getOrElse { "unknown" }
                        val depth = runCatching {
                            val conf = tx.javaClass.methods.firstOrNull { it.name == "getConfidence" }?.invoke(tx)
                            conf?.javaClass?.methods?.firstOrNull { it.name == "getDepthInBlocks" }?.invoke(conf)
                        }.getOrElse { "?" }
                        SpvController.log("confidence tx=$txId depth=$depth")
                    }
                    null
                }
                method.invoke(w, listenerProxy)
                AppLog.i(Channel.SPV, TAG, "Confidence listener attached")
            }.onFailure {
                AppLog.w(Channel.SPV, TAG, "Confidence listener attach failed: ${it.message}")
            }
        } else {
            AppLog.d(Channel.SPV, TAG, "No confidence listener API in this bitcoinj version")
        }
    }

    // Tor helpers

    private suspend fun waitForWalletTorReady() {
        var waited = 0L
        while (waited < TOR_WAIT_MS) {
            val s = TorManagerWallet.status.value
            SpvController.updateTor(s.running, s.bootstrapPercent)
            if (s.running && s.bootstrapPercent >= 100 && s.socks != null) {
                SpvController.log("wallet tor ready")
                return
            }
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
            SpvController.log("SOCKS applied ${socks.hostString}:${socks.port}")
        }.onFailure { AppLog.w(Channel.SPV, TAG, "applyJvmSocks failed: ${it.message}", it) }
    }

    private fun clearJvmSocks() {
        runCatching { System.clearProperty("socksProxyHost") }
        runCatching { System.clearProperty("socksProxyPort") }
        runCatching { System.clearProperty("socksProxyVersion") }
        SpvController.log("SOCKS cleared")
    }
}