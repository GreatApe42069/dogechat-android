package com.dogechat.android.wallet

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Context as BcjContext
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.script.Script
import org.libdohj.params.DogecoinMainNetParams
import java.io.File
import java.util.Date

@Singleton
class WalletManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    companion object {
        private const val TAG = "WalletManager"
        private const val FILE_PREFIX = "dogechat_doge"
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

    fun startNetwork() {
        scope.launch {
            try {
                Log.i(TAG, "Starting Dogecoin SPV node…")
                BcjContext.propagate(BcjContext(params))

                val dir = File(appContext.filesDir, "wallet")
                if (!dir.exists()) dir.mkdirs()

                val k = object : WalletAppKit(params, Script.ScriptType.P2PKH, null, dir, FILE_PREFIX) {
                    override fun onSetupCompleted() {
                     // allowSpendingUnconfirmedTransactions() may not exist in this bitcoinj/libdohj version.
                     // If your wallet library supports allowing spending unconfirmed txs, re-enable the call here.
                     // TODO: Re-enable if the Wallet implementation in your lib provides this function.
                     // wallet().allowSpendingUnconfirmedTransactions()
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
                        override fun progress(pct: Double, blocksSoFar: Int, date: Date?) {
                            _syncPercent.value = pct.toInt().coerceIn(0, 100)
                        }
                        override fun doneDownload() {
                            _syncPercent.value = 100
                            pushBalance()
                            pushHistory()
                        }
                    })
                }

                kit = k
                k.startAsync()
                k.awaitRunning()

                Log.i(TAG, "SPV kit running. Address: ${currentReceiveAddress()}")
                pushAddress()
                pushBalance()
                pushHistory()
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start SPV wallet: ${e.message}", e)
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
            }
        }
    }

    fun currentReceiveAddress(): String? = try {
        kit?.wallet()?.freshReceiveAddress()?.toString()
    } catch (_: Throwable) { null }

    fun refreshAddress() {
        scope.launch { pushAddress() }
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
                val address = Address.fromString(params, toAddress)
                val amount = Coin.valueOf(amountDoge * 100_000_000L)
                val result = localKit.wallet().sendCoins(localKit.peerGroup(), address, amount)
                result.broadcastComplete.addListener(
                    {
                        pushBalance()
                        pushHistory()
                        onResult(true, "Broadcasted: ${result.tx.txId}")
                    },
                    Runnable::run
                )
            } catch (e: Throwable) {
                Log.e(TAG, "sendCoins failed: ${e.message}", e)
                onResult(false, e.message ?: "Unknown error")
            }
        }
    }

    private fun pushAddress() {
        try {
            _address.value = kit?.wallet()?.freshReceiveAddress()?.toString()
        } catch (_: Throwable) { }
    }

    private fun pushBalance() {
        val w = kit?.wallet() ?: return
        _balance.value = w.balance.toFriendlyString()
    }

    private fun pushHistory() {
        val w = kit?.wallet() ?: return
        val rows = w.getTransactionsByTime().map { tx ->
            val vToMe = tx.getValueSentToMe(w)
            val vFromMe = tx.getValueSentFromMe(w)
            val delta = vToMe.minus(vFromMe)
            TxRow(
                hash = tx.txId.toString(),
                value = delta.toFriendlyString(),
                isIncoming = delta.isPositive,
                time = tx.updateTime,
                confirmations = tx.confidence.depthInBlocks
            )
        }
        _history.value = rows
    }
}
