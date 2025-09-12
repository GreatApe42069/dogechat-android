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

@Singleton
class WalletManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    companion object {
        private const val TAG = "WalletManager"
        private const val FILE_PREFIX = "dogechat_doge"
        private const val PREFS_NAME = "dogechat_wallet"
        private const val PREF_KEY_RECEIVE_ADDRESS = "receive_address"
    }

    data class TxRow(
        val hash: String,
        val value: String,
        val isIncoming: Boolean,
        val time: Date?,
        val confirmations: Int
    )

    private val scope = CoroutineScope(Dispatchers.IO)

    // New API: use BitcoinNetwork enum + derive NetworkParameters from it
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

    fun startNetwork(
        torProxyHost: String? = null,
        torProxyPort: Int? = null
    ) {
        scope.launch {
            try {
                Log.i(TAG, "Starting Dogecoin SPV node…")
                // New context usage: no-arg constructor
                BcjContext.propagate(BcjContext())

                val dir = File(appContext.filesDir, "wallet")
                if (!dir.exists()) dir.mkdirs()

                // New WalletAppKit signature: (BitcoinNetwork, ScriptType, KeyChainGroupStructure, File, String)
                val k = object : WalletAppKit(network, ScriptType.P2PKH, KeyChainGroupStructure.BIP32, dir, FILE_PREFIX) {
                    override fun onSetupCompleted() {
                        pushBalance()
                        pushAddress()
                        pushHistory()

                        wallet().addCoinsReceivedEventListener { _, _, _, _ ->
                            pushBalance(); pushHistory()
                        }
                        wallet().addCoinsSentEventListener { _, _, _, _ ->
                            pushBalance(); pushHistory()
                        }
                        wallet().addChangeEventListener {
                            pushBalance(); pushHistory()
                        }
                    }
                }.apply {
                    setBlockingStartup(false)
                    setDownloadListener(object : DownloadProgressTracker() {
                        // Signature now uses java.time.Instant?
                        override fun progress(pct: Double, blocksSoFar: Int, date: Instant?) {
                            _syncPercent.value = pct.toInt().coerceIn(0, 100)
                            updateSpvStatus()
                            Log.i(TAG, "SPV sync: ${_syncPercent.value}%")
                        }
                        override fun doneDownload() {
                            _syncPercent.value = 100
                            pushBalance()
                            pushHistory()
                            updateSpvStatus()
                        }
                    })
                }

                // Tor SOCKS proxy: PeerGroup.setProxy() not available in this fork; skip for now.
                if (torProxyHost != null && torProxyPort != null) {
                    Log.i(TAG, "Tor proxy requested ($torProxyHost:$torProxyPort) but setProxy is unavailable in this fork; skipping proxy setup.")
                }

                kit = k

                // Peer event listeners
                k.peerGroup().addConnectedEventListener { _, peerCount ->
                    _peerCount.value = peerCount
                    Log.i(TAG, "SPV node peer count: $peerCount")
                    updateSpvStatus()
                }
                k.peerGroup().addDisconnectedEventListener { _, peerCount ->
                    _peerCount.value = peerCount
                    Log.i(TAG, "SPV node peer count: $peerCount")
                    updateSpvStatus()
                }

                _spvStatus.value = "Connecting"
                k.startAsync()
                k.awaitRunning()

                Log.i(TAG, "SPV kit running. Address: ${currentReceiveAddress()}")
                pushAddress()
                pushBalance()
                pushHistory()
                updateSpvStatus()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start SPV wallet: ${e.message}", e)
                _spvStatus.value = "Error"
            }
        }
    }

    fun stopNetwork() {
        scope.launch {
            try {
                kit?.apply {
                    Log.i(TAG, "Stopping Dogecoin SPV node…")
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
            }
        }
    }

    /**
     * Returns the current receive address from the wallet (persisted), or null if not available yet.
     * This will always return the same address unless a new one is explicitly requested.
     */
    fun currentReceiveAddress(): String? = try {
        // Prefer wallet's current receive address
        kit?.wallet()?.currentReceiveAddress()?.toString()
            // Fallback to stored address
            ?: prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
    } catch (_: Throwable) {
        prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
    }

    /**
     * Generates a new receive address and saves it persistently.
     */
    fun generateNewReceiveAddress() {
        scope.launch {
            val address = kit?.wallet()?.freshReceiveAddress()?.toString()
            if (address != null) {
                prefs.edit().putString(PREF_KEY_RECEIVE_ADDRESS, address).apply()
                _address.value = address
            }
        }
    }

    /**
     * Loads the current address from wallet (does not generate new one), persists if needed.
     */
    private fun pushAddress() {
        try {
            val address = kit?.wallet()?.currentReceiveAddress()?.toString()
            if (address != null) {
                prefs.edit().putString(PREF_KEY_RECEIVE_ADDRESS, address).apply()
                _address.value = address
            } else {
                // fallback to persisted
                _address.value = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
            }
        } catch (_: Throwable) {
            _address.value = prefs.getString(PREF_KEY_RECEIVE_ADDRESS, null)
        }
    }

    fun refreshAddress() {
        // Only generate a new address if requested, otherwise call pushAddress()
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
                // Address parsing in this fork is via AddressParser.getDefault(network)
                val address: Address = AddressParser.getDefault(network).parseAddress(toAddress)
                val amount = Coin.valueOf(amountDoge * 100_000_000L)

                // In this fork, SendResult fields like broadcastComplete/tx may be unavailable/private.
                // Rely on the call succeeding; then update UI immediately.
                localKit.wallet().sendCoins(localKit.peerGroup(), address, amount)

                // Update UI immediately; the broadcast is in-flight.
                pushBalance()
                pushHistory()
                onResult(true, "Broadcast requested")
            } catch (e: Throwable) {
                Log.e(TAG, "sendCoins failed: ${e.message}", e)
                onResult(false, e.message ?: "Unknown error")
            }
        }
    }

    private fun pushBalance() {
        val w = kit?.wallet() ?: return
        // Always display as ĐOGE, never BTC/satoshi
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