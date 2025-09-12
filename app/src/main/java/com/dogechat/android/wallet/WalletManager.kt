package com.dogechat.android.wallet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.bitcoinj.base.Address
import org.bitcoinj.base.AddressParser
import org.bitcoinj.base.BitcoinNetwork
import org.bitcoinj.base.Coin
import org.bitcoinj.base.ScriptType
import org.bitcoinj.core.Context as BcjContext
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.wallet.KeyChainGroupStructure
import java.io.File
import java.time.Instant
import java.util.Date

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

        // Lightweight controller for UI surfaces (AboutSheet, etc.)
        object SpvController {
            val enabled = MutableStateFlow(false)
            val status = MutableStateFlow(SpvStatus(running = false, peerCount = 0, syncPercent = 0, lastLogLine = "stopped"))

            fun get(context: Context): Boolean {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val isOn = prefs.getBoolean(PREF_KEY_SPV_ENABLED, true)
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

            internal fun updateSync(pct: Int) {
                val cur = status.value
                status.value = cur.copy(syncPercent = pct)
            }

            internal fun log(line: String) {
                val cur = status.value
                status.value = cur.copy(lastLogLine = line)
            }
        }

        @Volatile
        internal var instanceRef: WalletManager? = null
    }

    data class TxRow(
        val hash: String,
        val value: String,
        val isIncoming: Boolean,
        val time: Date?,
        val confirmations: Int
    )

    private val scope = CoroutineScope(Dispatchers.IO)

    // Network selection
    private val network: BitcoinNetwork = BitcoinNetwork.MAINNET
    private val params: NetworkParameters = NetworkParameters.of(network)

    private var kit: WalletAppKit? = null

    private val _balance = MutableStateFlow("0 ĐOGE")
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
        // Auto-start SPV based on saved preference (default ON)
        val shouldStart = prefs.getBoolean(PREF_KEY_SPV_ENABLED, true)
        SpvController.enabled.value = shouldStart
        if (shouldStart) {
            startNetwork()
        } else {
            SpvController.updateRunning(false)
        }
    }

    fun startNetwork(
        torProxyHost: String? = null,
        torProxyPort: Int? = null
    ) {
        scope.launch {
            try {
                Log.i(TAG, "Starting SPV…")
                BcjContext.propagate(BcjContext())

                val dir = File(appContext.filesDir, "wallet")
                if (!dir.exists()) dir.mkdirs()

                val k = object : WalletAppKit(network, ScriptType.P2PKH, KeyChainGroupStructure.BIP32, dir, FILE_PREFIX) {
                    override fun onSetupCompleted() {
                        pushBalance()
                        pushAddress()
                        pushHistory()

                        wallet().addCoinsReceivedEventListener { _, _, _, _ ->
                            pushBalance(); pushHistory()
                            SpvController.log("coins received")
                        }
                        wallet().addCoinsSentEventListener { _, _, _, _ ->
                            pushBalance(); pushHistory()
                            SpvController.log("coins sent")
                        }
                        wallet().addChangeEventListener {
                            pushBalance(); pushHistory()
                        }
                    }
                }.apply {
                    setBlockingStartup(false)
                    setDownloadListener(object : DownloadProgressTracker() {
                        override fun progress(pct: Double, blocksSoFar: Int, date: Instant?) {
                            val p = pct.toInt().coerceIn(0, 100)
                            _syncPercent.value = p
                            SpvController.updateSync(p)
                            _spvStatus.value = if (p < 100) "Syncing" else "Synced"
                            SpvController.log("sync $p% (${blocksSoFar} blocks)")
                            Log.i(TAG, "SPV sync: $p%")
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

                // Tor proxy not supported here in this fork; log only.
                if (torProxyHost != null && torProxyPort != null) {
                    Log.i(TAG, "Tor proxy requested ($torProxyHost:$torProxyPort) but setProxy is unavailable; skipping.")
                }

                kit = k

                // Peer events
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
                val address: Address = AddressParser.getDefault(network).parseAddress(toAddress)
                val amount = Coin.valueOf(amountDoge * 100_000_000L)
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
        val balanceDoge = w.balance.toPlainString()
        _balance.value = "$balanceDoge ĐOGE"
    }

    private fun pushHistory() {
        val w = kit?.wallet() ?: return
        val rows = w.getTransactionsByTime().map { tx ->
            val vToMe = tx.getValueSentToMe(w)
            val vFromMe = tx.getValueSentFromMe(w)
            val delta = vToMe.subtract(vFromMe)
            val instant: Instant? = tx.updateTime().orElse(null)
            TxRow(
                hash = tx.txId.toString(),
                value = delta.toPlainString() + " ĐOGE",
                isIncoming = delta.signum() > 0,
                time = instant?.let { Date.from(it) },
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
}