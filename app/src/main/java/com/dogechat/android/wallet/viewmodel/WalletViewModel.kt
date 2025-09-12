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
import javax.inject.Inject

/**
 * ViewModel wrapper for WalletManager with UI-friendly state.
 *
 * Changes:
 * - SPV default OFF (pref key "spv_enabled" defaults to false).
 * - Observes the AboutSheet toggle via shared prefs and starts/stops the node accordingly.
 * - Guards startWallet() so opening WalletScreen won't auto-start when SPV is Off.
 * - Logs network param id/port at startup for quick diagnosis (also logged by WalletManager).
 *
 * Note: AboutSheet uses shared prefs only; no Hilt references from UI.
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

    // Persistent prefs (shared with AboutSheet)
    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Observe pref changes and start/stop accordingly
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_SPV_ENABLED) {
            val enabled = prefs.getBoolean(KEY_SPV_ENABLED, false) // default OFF
            Log.i(TAG, "Preference '$KEY_SPV_ENABLED' changed -> $enabled")
            if (enabled) {
                // User turned it ON from AboutSheet: start node
                startWalletInternal(logParams = true)
            } else {
                // User turned it OFF: stop node
                stopWalletInternal()
            }
        }
    }

    init {
        // Register listener
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        // If previously enabled, auto-start on app process launch; otherwise stay off by default
        val enabled = prefs.getBoolean(KEY_SPV_ENABLED, false)
        Log.i(TAG, "init: '$KEY_SPV_ENABLED' currently $enabled (default OFF)")
        if (enabled) {
            startWalletInternal(logParams = true)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        } catch (_: Throwable) { }
    }

    // ----- Flows from WalletManager -----

    val balance: StateFlow<String> = walletManager.balance
        .stateIn(viewModelScope, SharingStarted.Lazily, "0 DOGE")

    val address: StateFlow<String?> = walletManager.address
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val syncPercent: StateFlow<Int> = walletManager.syncPercent
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val history: StateFlow<List<WalletManager.TxRow>> = walletManager.history
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ----- Public controls (guarded) -----

    /**
     * Start wallet node only if spv_enabled is true.
     * If disabled, this is a no-op (prevents WalletScreen from auto-starting it).
     */
    fun startWallet() {
        val enabled = prefs.getBoolean(KEY_SPV_ENABLED, false)
        if (!enabled) {
            Log.i(TAG, "startWallet(): ignored because '$KEY_SPV_ENABLED' is false (default OFF).")
            return
        }
        startWalletInternal(logParams = true)
    }

    fun stopWallet() {
        stopWalletInternal()
    }

    fun getReceiveAddress(): String? = walletManager.currentReceiveAddress()

    fun refreshAddress() {
        viewModelScope.launch { walletManager.refreshAddress() }
    }

    /**
     * Send DOGE (amountDoge is whole DOGE units)
     */
    fun sendCoins(
        toAddress: String,
        amountDoge: Long,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            walletManager.sendCoins(toAddress, amountDoge, onResult)
        }
    }

    // ----- Internal helpers -----

    private fun startWalletInternal(logParams: Boolean) {
        viewModelScope.launch {
            if (logParams) {
                // Log what this build will use (matches WalletManager, which will also log at start)
                try {
                    val network = org.bitcoinj.base.BitcoinNetwork.MAINNET
                    val params = org.bitcoinj.core.NetworkParameters.of(network)
                    Log.i(TAG, "Diagnostics: params.id=${params.id} port=${params.port}")
                } catch (t: Throwable) {
                    Log.w(TAG, "Diagnostics: failed to load NetworkParameters: ${t.message}")
                }
            }
            walletManager.startNetwork()
        }
    }

    private fun stopWalletInternal() {
        viewModelScope.launch {
            walletManager.stopNetwork()
        }
    }

    // ---- Enums used by UIStateManager ----
    enum class SendType { DOGE }
    enum class ReceiveType { DOGE }

    // ---- Animation Data Models ----
    data class SuccessAnimationData(
        val message: String,
        val txHash: String? = null
    )

    data class FailureAnimationData(
        val message: String,
        val reason: String? = null
    )
}