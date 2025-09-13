package com.dogechat.android.wallet.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogechat.android.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.libdohj.params.DogecoinMainNetParams
import javax.inject.Inject

/**
 * WalletViewModel that:
 * - Makes SPV default OFF (spv_enabled=false by default)
 * - Observes AboutSheet’s shared pref toggle and starts/stops accordingly
 * - Logs NetworkParameters id/port at startup for quick diagnostics
 * - Guards startWallet() so opening WalletScreen won’t auto-start when OFF
 * - Avoids any Hilt usage in AboutSheet (AboutSheet only flips the pref)
 */
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletManager: WalletManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "WalletViewModel"
        private const val PREFS_NAME = "dogechat_wallet"
        private const val KEY_SPV_ENABLED = "spv_enabled"
    }

    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Listen for AboutSheet toggle changes
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_SPV_ENABLED) {
            val enabled = prefs.getBoolean(KEY_SPV_ENABLED, false) // default OFF
            Log.i(TAG, "Pref '$KEY_SPV_ENABLED' changed -> $enabled")
            if (enabled) {
                startWalletInternal(logParams = true)
            } else {
                stopWalletInternal()
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        val enabled = prefs.getBoolean(KEY_SPV_ENABLED, false)
        Log.i(TAG, "init: '$KEY_SPV_ENABLED'=$enabled (default OFF)")
        if (enabled) startWalletInternal(logParams = true)
    }

    override fun onCleared() {
        super.onCleared()
        try { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Throwable) {}
    }

    // Exposed UI state
    val balance: StateFlow<String> = walletManager.balance
        .stateIn(viewModelScope, SharingStarted.Lazily, "0 DOGE")

    val address: StateFlow<String?> = walletManager.address
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val syncPercent: StateFlow<Int> = walletManager.syncPercent
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val history: StateFlow<List<WalletManager.TxRow>> = walletManager.history
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /**
     * Guarded start: only starts if spv_enabled is true.
     */
    fun startWallet() {
        val enabled = prefs.getBoolean(KEY_SPV_ENABLED, false)
        if (!enabled) {
            Log.i(TAG, "startWallet(): skipped because '$KEY_SPV_ENABLED' is false (default OFF).")
            return
        }
        startWalletInternal(logParams = true)
    }

    fun stopWallet() = stopWalletInternal()

    fun getReceiveAddress(): String? = walletManager.currentReceiveAddress()

    fun refreshAddress() {
        viewModelScope.launch { walletManager.refreshAddress() }
    }

    fun sendCoins(
        toAddress: String,
        amountDoge: Long,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            walletManager.sendCoins(toAddress, amountDoge, onResult)
        }
    }

    // Internal helpers

    private fun startWalletInternal(logParams: Boolean) {
        viewModelScope.launch {
            if (logParams) {
                try {
                    val params = DogecoinMainNetParams.get()
                    Log.i(TAG, "Diagnostics: params.id=${params.id} port=${params.port}")
                } catch (t: Throwable) {
                    Log.w(TAG, "Diagnostics: failed to load NetworkParameters: ${t.message}")
                }
            }
            walletManager.startNetwork()
        }
    }

    private fun stopWalletInternal() {
        viewModelScope.launch { walletManager.stopNetwork() }
    }

    // UI enums (kept for compatibility with your UIStateManager)
    enum class SendType { DOGE }
    enum class ReceiveType { DOGE }

    data class SuccessAnimationData(val message: String, val txHash: String? = null)
    data class FailureAnimationData(val message: String, val reason: String? = null)
}