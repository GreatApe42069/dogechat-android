package com.dogechat.android.wallet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dogechat.android.wallet.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel wrapper for WalletManager with UI-friendly state and enums for send/receive types.
 */
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletManager: WalletManager
) : ViewModel() {

    // Flow balance from WalletManager
    val balance: StateFlow<String> = walletManager.balance
        .stateIn(viewModelScope, SharingStarted.Lazily, "0")

    // Expose the on-disk/current receive address
    val address: StateFlow<String?> = walletManager.address
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Expose sync percent
    val syncPercent: StateFlow<Int> = walletManager.syncPercent
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // Expose the transaction history from WalletManager so UI can observe it directly.
    // The UI will map WalletManager.TxRow -> a small UI model if desired.
    val history: StateFlow<List<WalletManager.TxRow>> = walletManager.history
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Start wallet network
    fun startWallet() {
        viewModelScope.launch {
            walletManager.startNetwork()
        }
    }

    // Stop wallet network
    fun stopWallet() {
        viewModelScope.launch {
            walletManager.stopNetwork()
        }
    }

    // Get current receive address (immediate string)
    fun getReceiveAddress(): String? {
        return walletManager.currentReceiveAddress()
    }

    // Request a refresh / new receive address
    fun refreshAddress() {
        viewModelScope.launch { walletManager.refreshAddress() }
    }

    // Send DOGE (amountDoge is whole DOGE units; WalletManager expects Long DOGE)
    fun sendCoins(
        toAddress: String,
        amountDoge: Long,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            walletManager.sendCoins(toAddress, amountDoge, onResult)
        }
    }

    // ---- Enums used by UIStateManager ----
    enum class SendType {
        DOGE
    }

    enum class ReceiveType {
        DOGE
    }

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
