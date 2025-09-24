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
import java.net.Proxy
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.SocketFactory
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

                            // Apply custom SocketFactory for Tor connections if using SOCKS
                            if (usingSocks) {
                                val socksAddr = TorManagerWallet.currentSocks()
                                if (socksAddr != null) {
                                    AppLog.i(Channel.SPV, TAG, "Applying custom SocketFactory for Tor connections: $socksAddr")
                                    try {
                                        val proxy = Proxy(Proxy.Type.SOCKS, socksAddr)
                                        val customSocketFactory = object : SocketFactory() {
                                            override fun createSocket(): Socket = Socket(proxy)
                                            override fun createSocket(host: String?, port: Int): Socket = Socket(proxy)
                                            override fun createSocket(host: String?, port: Int, localHost: java.net.InetAddress?, localPort: Int): Socket = Socket(proxy)
                                            override fun createSocket(host: java.net.InetAddress?, port: Int): Socket = Socket(proxy)
                                            override fun createSocket(address: java.net.InetAddress?, port: Int, localAddress: java.net.InetAddress?, localPort: Int): Socket = Socket(proxy)
                                        }
                                        pg.setSocketFactory(customSocketFactory)
                                        SpvController.log("custom Tor SocketFactory applied")
                                    } catch (e: Exception) {
                                        AppLog.w(Channel.SPV, TAG, "Failed to apply custom SocketFactory: ${e.message}", e)
                                        SpvController.log("warning: SocketFactory application failed: ${e.message}")
                                    }
                                }
                            }

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
            var consecutiveFailures = 0
            val maxConsecutiveFailures = 5
            
            while (isActive) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                val enabled = SpvController.enabled.value
                val localKit = kit
                val running = localKit != null && localKit.isRunning
                
                if (enabled && !running) {
                    consecutiveFailures++
                    val backoffDelay = minOf(1000L * (1 shl consecutiveFailures), 30000L) // Exponential backoff, max 30s
                    AppLog.w(Channel.SPV, TAG, "KeepAlive: SPV enabled but not running -> restart (failure #$consecutiveFailures, will wait ${backoffDelay}ms)")
                    SpvController.log("keep-alive: restart needed (failure #$consecutiveFailures)")
                    
                    if (consecutiveFailures <= maxConsecutiveFailures) {
                        delay(backoffDelay)
                        startNetwork()
                    } else {
                        AppLog.e(Channel.SPV, TAG, "KeepAlive: Max consecutive failures reached ($maxConsecutiveFailures), disabling keep-alive")
                        SpvController.log("keep-alive: too many failures, disabled")
                        break
                    }
                } else if (running) {
                    // Reset failure counter when successfully running
                    if (consecutiveFailures > 0) {
                        AppLog.i(Channel.SPV, TAG, "KeepAlive: SPV running normally, resetting failure counter")
                        consecutiveFailures = 0
                    }
                }
                
                // Wallet Tor keep-alive with better error handling
                val wantTor = WalletTorPreferenceManager.get(appContext) == com.dogechat.android.net.TorMode.ON
                if (wantTor && !TorManagerWallet.isRunning()) {
                    AppLog.w(Channel.SPV, TAG, "KeepAlive: Wallet Tor wanted but not running -> start")
                    SpvController.log("keep-alive: wallet Tor restart needed")
                    runCatching { 
                        TorManagerWallet.start(appContext.applicationContext as Application) 
                    }.onFailure { 
                        AppLog.w(Channel.SPV, TAG, "KeepAlive: Wallet Tor start failed: ${it.message}", it)
                        SpvController.log("keep-alive: wallet Tor start failed: ${it.message}")
                    }
                }
            }
            AppLog.i(Channel.SPV, TAG, "KeepAlive loop ended")
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
            try {
                val localKit = kit
                if (localKit == null) {
                    SpvController.log("refresh address skipped (wallet not started)")
                    AppLog.d(Channel.SPV, TAG, "refreshAddress: wallet not started")
                    return@launch
                }
                
                val addr = runCatching { 
                    localKit.wallet()?.freshReceiveAddress()?.toString() 
                }.getOrNull()
                
                if (!addr.isNullOrBlank()) {
                    // Clear any cached WIF since we're generating a fresh address
                    prefs.edit()
                        .remove(PREF_KEY_CACHED_WIF)
                        .putString(PREF_KEY_RECEIVE_ADDRESS, addr)
                        .apply()
                    _address.value = addr
                    SpvController.log("new fresh address $addr")
                    AppLog.i(Channel.SPV, TAG, "refreshAddress: generated fresh address $addr")
                } else {
                    SpvController.log("refresh address failed (could not generate)")
                    AppLog.w(Channel.SPV, TAG, "refreshAddress: could not generate fresh address")
                }
            } catch (e: Throwable) {
                SpvController.log("refresh address error: ${e.message}")
                AppLog.e(Channel.SPV, TAG, "refreshAddress failed: ${e.message}", e)
            }
        }
    }

    fun refreshNow() {
        AppLog.action("WalletScreen", "refreshNow")
        scope.launch {
            try {
                pushAddress(); pushBalance(); pushHistory()
                val local = kit
                if (local == null) {
                    SpvController.log("refresh skipped (wallet not started)")
                    AppLog.d(Channel.SPV, TAG, "refreshNow: wallet not started")
                    return@launch
                }
                if (!local.isRunning) {
                    SpvController.log("refresh aborted (kit not running)")
                    AppLog.d(Channel.SPV, TAG, "refreshNow: kit not running")
                    return@launch
                }
                
                // Check if we have peer connections before attempting sync
                val peerGroup = runCatching { local.peerGroup() }.getOrNull()
                if (peerGroup == null) {
                    SpvController.log("refresh skipped (no peer group)")
                    AppLog.d(Channel.SPV, TAG, "refreshNow: no peer group available")
                    return@launch
                }
                
                val connectedPeers = runCatching { peerGroup.connectedPeers.size }.getOrElse { 0 }
                if (connectedPeers == 0) {
                    SpvController.log("refresh deferred (no peers connected)")
                    AppLog.d(Channel.SPV, TAG, "refreshNow: no peers connected, deferring")
                    return@launch
                }
                
                runCatching {
                    peerGroup.startBlockChainDownload(object : DownloadProgressTracker() {
                        override fun doneDownload() {
                            pushBalance(); pushHistory()
                            SpvController.log("manual refresh done")
                            AppLog.i(Channel.SPV, TAG, "refreshNow: manual refresh completed")
                        }
                        override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                            val p = pct.toInt().coerceIn(0, 100)
                            if (blocksSoFar % 50 == 0) { // Log less frequently during manual refresh
                                SpvController.log("manual refresh progress: $p% ($blocksSoFar blocks)")
                            }
                        }
                    })
                    SpvController.log("manual refresh triggered ($connectedPeers peers)")
                    AppLog.i(Channel.SPV, TAG, "refreshNow: manual refresh started with $connectedPeers peers")
                }.onFailure {
                    SpvController.log("refresh trigger failed: ${it.message}")
                    AppLog.w(Channel.SPV, TAG, "refreshNow failed: ${it.message}", it)
                }
            } catch (e: Throwable) {
                // Comprehensive exception handling to prevent crashes
                SpvController.log("refresh error: ${e.message}")
                AppLog.e(Channel.SPV, TAG, "refreshNow unexpected error: ${e.message}", e)
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
                
                // Check balance before attempting send
                val balance = localKit.wallet().balance
                if (balance < amount) {
                    val msg = "Insufficient balance: have ${balance.toFriendlyString()}, need ${amount.toFriendlyString()}"
                    AppLog.w(Channel.SPV, TAG, msg)
                    withContext(Dispatchers.Main) { onResult(false, msg) }
                    return@launch
                }
                
                // Estimate fee (Dogecoin uses ~0.01 DOGE per KB)
                val estimatedFee = org.bitcoinj.core.Coin.valueOf(100_000L) // 0.001 DOGE base fee
                val totalNeeded = amount.add(estimatedFee)
                
                if (balance < totalNeeded) {
                    val msg = "Insufficient balance for transaction + fee: need ${totalNeeded.toFriendlyString()}"
                    AppLog.w(Channel.SPV, TAG, msg)
                    withContext(Dispatchers.Main) { onResult(false, msg) }
                    return@launch
                }
                
                AppLog.i(Channel.SPV, TAG, "Attempting send tx amount=$amount to=$toAddress (estimated fee: $estimatedFee)")
                SpvController.log("send: ${amount.toFriendlyString()} to ${toAddress.take(10)}… (fee ~${estimatedFee.toFriendlyString()})")
                
                val sendReq = localKit.wallet().sendCoins(localKit.peerGroup(), address, amount)
                val actualFee = sendReq.tx.fee
                
                SpvController.log("broadcast requested tx=${sendReq.tx.txId} (actual fee: ${actualFee?.toFriendlyString() ?: "unknown"})")
                AppLog.i(Channel.SPV, TAG, "Broadcast requested tx=${sendReq.tx.txId} fee=${actualFee?.toFriendlyString()}")
                
                pushBalance(); pushHistory()
                withContext(Dispatchers.Main) { 
                    val feeInfo = actualFee?.let { " (fee: ${it.toFriendlyString()})" } ?: ""
                    onResult(true, "Transaction broadcasted$feeInfo") 
                }
            } catch (e: Throwable) {
                AppLog.e(Channel.SPV, TAG, "sendCoins failed: ${e.message}", e)
                SpvController.log("send error: ${e.message}")
                withContext(Dispatchers.Main) { 
                    val errorMsg = when {
                        e.message?.contains("Insufficient money") == true -> "Insufficient funds"
                        e.message?.contains("No peer") == true -> "No network connection"
                        e.message?.contains("dust") == true -> "Amount too small (dust limit)"
                        else -> e.message ?: "Transaction failed"
                    }
                    onResult(false, errorMsg) 
                }
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
                // Validate WIF format first
                if (wif.isBlank()) {
                    withContext(Dispatchers.Main) { onResult(false, "Empty private key") }
                    return@launch
                }
                
                // Attempt to parse the WIF
                val key = try {
                    DumpedPrivateKey.fromBase58(params, wif.trim()).key
                } catch (e: Exception) {
                    AppLog.w(Channel.SPV, TAG, "Invalid WIF format: ${e.message}")
                    withContext(Dispatchers.Main) { onResult(false, "Invalid private key format") }
                    return@launch
                }
                
                val addr = LegacyAddress.fromKey(params, key).toString()
                AppLog.i(Channel.SPV, TAG, "WIF import: derived address $addr")
                
                val w = kit?.wallet()
                if (w == null) {
                    // Wallet not running - cache the WIF for later import
                    persistWif(wif.trim(), addr)
                    _address.value = addr
                    SpvController.log("WIF cached (wallet idle) -> $addr")
                    withContext(Dispatchers.Main) { 
                        onResult(true, "Private key cached. Will be imported when wallet starts.\nAddress: $addr") 
                    }
                    return@launch
                }
                
                // Check if key is already imported
                val exists = runCatching { w.importedKeys.contains(key) }.getOrDefault(false)
                if (exists) {
                    AppLog.i(Channel.SPV, TAG, "Key already imported")
                    persistWif(wif.trim(), addr)
                    _address.value = addr
                    withContext(Dispatchers.Main) { 
                        onResult(true, "Private key already imported.\nAddress: $addr") 
                    }
                    return@launch
                }
                
                // Import the key
                runCatching { w.importKey(key) }.onSuccess {
                    SpvController.log("key imported successfully")
                    AppLog.i(Channel.SPV, TAG, "Private key imported successfully")
                }.onFailure {
                    AppLog.w(Channel.SPV, TAG, "Key import failed: ${it.message}", it)
                    withContext(Dispatchers.Main) { onResult(false, "Failed to import: ${it.message}") }
                    return@launch
                }
                
                persistWif(wif.trim(), addr)
                _address.value = addr
                
                // Trigger rescan from key creation time
                val creationTime = key.creationTimeSeconds
                SpvController.log("triggering rescan from ${Date(creationTime * 1000)}")
                triggerRescanFromBirth(creationTime)
                
                withContext(Dispatchers.Main) { 
                    onResult(true, "Private key imported successfully.\nAddress: $addr\nRescanning blockchain...") 
                }
            } catch (e: Throwable) {
                AppLog.e(Channel.SPV, TAG, "importPrivateKeyWif failed: ${e.message}", e)
                SpvController.log("WIF import error: ${e.message}")
                withContext(Dispatchers.Main) { 
                    val errorMsg = when {
                        e.message?.contains("Checksum") == true -> "Invalid private key checksum"
                        e.message?.contains("Base58") == true -> "Invalid private key format"
                        else -> e.message ?: "Import failed"
                    }
                    onResult(false, errorMsg) 
                }
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
        AppLog.i(Channel.SPV, TAG, "triggerRescan birth=$birthTimeSecs (${Date(birthTimeSecs * 1000)})")
        
        try {
            // Set fast catchup time
            runCatching { pg.setFastCatchupTimeSecs(birthTimeSecs) }
                .onSuccess { SpvController.log("fast catchup time set to $birthTimeSecs") }
                .onFailure { AppLog.w(Channel.SPV, TAG, "setFastCatchupTimeSecs failed: ${it.message}") }
            
            // Try to recalculate filter - use reflection for compatibility
            runCatching {
                val m = pg.javaClass.methods.firstOrNull { 
                    it.name == "recalculateFastCatchupAndFilter" && it.parameterTypes.isEmpty() 
                }
                if (m != null) {
                    m.invoke(pg)
                    SpvController.log("filter recalculated (no params)")
                } else {
                    // Try with enum parameter for older bitcoinj versions
                    runCatching {
                        val enumClass = Class.forName("org.bitcoinj.core.PeerGroup\$FilterRecalculateMode")
                        val forceSend = enumClass.enumConstants?.firstOrNull()
                        val m2 = pg.javaClass.getMethod("recalculateFastCatchupAndFilter", enumClass)
                        m2.invoke(pg, forceSend)
                        SpvController.log("filter recalculated (with enum)")
                    }.onFailure {
                        SpvController.log("filter recalculation fallback failed: ${it.message}")
                    }
                }
            }.onFailure {
                SpvController.log("filter recalculation failed: ${it.message}")
                AppLog.w(Channel.SPV, TAG, "filter recalculation failed: ${it.message}")
            }
            
            // Start blockchain download
            runCatching {
                pg.startBlockChainDownload(object : DownloadProgressTracker() {
                    override fun doneDownload() {
                        pushBalance(); pushHistory()
                        SpvController.log("rescan complete")
                        AppLog.i(Channel.SPV, TAG, "Rescan download completed")
                    }
                    override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                        val p = pct.toInt().coerceIn(0, 100)
                        if (blocksSoFar % 100 == 0) { // Log less frequently during rescan
                            SpvController.log("rescan progress: $p% ($blocksSoFar blocks)")
                        }
                    }
                })
                SpvController.log("rescan started")
                AppLog.i(Channel.SPV, TAG, "Rescan blockchain download started")
            }.onFailure {
                SpvController.log("rescan start failed: ${it.message}")
                AppLog.w(Channel.SPV, TAG, "rescan start failed: ${it.message}", it)
            }
            
        } catch (e: Throwable) {
            SpvController.log("rescan trigger error: ${e.message}")
            AppLog.e(Channel.SPV, TAG, "triggerRescanFromBirth error: ${e.message}", e)
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
        // bitcoinj versions differ; attempt reflection for confidence listener
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
                        val confType = runCatching {
                            val conf = tx.javaClass.methods.firstOrNull { it.name == "getConfidence" }?.invoke(tx)
                            conf?.javaClass?.methods?.firstOrNull { it.name == "getConfidenceType" }?.invoke(conf)?.toString()
                        }.getOrElse { "UNKNOWN" }
                        
                        val fmt = { t: String -> "${timeFmt.format(Date())} $t" }
                        SpvController.log(fmt("confidence tx=${txId.take(12)}… depth=$depth type=$confType"))
                        
                        // Update history when confidence changes significantly
                        if (depth is Int && depth % 3 == 0) { // Update every 3 confirmations
                            pushHistory()
                        }
                    }
                    null
                }
                method.invoke(w, listenerProxy)
                AppLog.i(Channel.SPV, TAG, "Confidence listener attached with enhanced logging")
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