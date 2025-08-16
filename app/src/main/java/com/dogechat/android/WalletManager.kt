package com.dogechat.android

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.Context as BcjContext
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.script.Script
import org.libdohj.params.DogecoinMainNetParams
import java.io.File

@Singleton
class WalletManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    companion object {
        private const val TAG = "WalletManager"
        private const val FILE_PREFIX = "dogechat_doge"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val params: NetworkParameters = DogecoinMainNetParams.get()
    private var kit: WalletAppKit? = null

    private val _balance = MutableStateFlow("0")
    val balance: StateFlow<String> = _balance

    fun startNetwork() {
        scope.launch {
            try {
                Log.i(TAG, "Starting Dogecoin SPV node…")
                BcjContext.propagate(BcjContext(params))

                val dir = File(appContext.filesDir, "wallet").apply { if (!exists()) mkdirs() }

                val walletKit = object : WalletAppKit(params, Script.ScriptType.P2PKH, null, dir, FILE_PREFIX) {}
                kit = walletKit

                walletKit.setBlockingStartup(false)
                walletKit.startAsync()
                walletKit.awaitRunning()

                Log.i(TAG, "SPV kit running. Address: ${currentReceiveAddress()}")

                pushBalance()

                walletKit.wallet().addCoinsReceivedEventListener { _, _, _, _ -> pushBalance() }
                walletKit.wallet().addCoinsSentEventListener { _, _, _, _ -> pushBalance() }

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
            }
        }
    }

    fun currentReceiveAddress(): String? = try {
        kit?.wallet()?.freshReceiveAddress()?.toString()
    } catch (_: Throwable) {
        null
    }

    fun sendCoins(toAddress: String, amountDoge: Long, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            val walletKit = kit
            if (walletKit == null) {
                withContext(Dispatchers.Main) { onResult(false, "Wallet not started") }
                return@launch
            }

            try {
                val address = Address.fromString(params, toAddress)
                val amount = Coin.valueOf(amountDoge * 100_000_000L)

                val txFuture = walletKit.wallet().sendCoins(walletKit.peerGroup(), address, amount)
                txFuture.broadcastComplete.addListener({
                    pushBalance()
                    val hash = txFuture.tx.hash
                    scope.launch(Dispatchers.Main) { onResult(true, "Broadcasted: $hash") }
                }, Runnable::run)

            } catch (e: Throwable) {
                Log.e(TAG, "sendCoins failed: ${e.message}", e)
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "Unknown error") }
            }
        }
    }

    private fun pushBalance() {
        val walletBalance = kit?.wallet()?.balance?.toFriendlyString() ?: "0"
        // Ensure StateFlow update happens on the main thread
        scope.launch(Dispatchers.Main) { _balance.value = walletBalance }
    }
}
