package com.dogechat.android.wallet.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * Manages all UI state for the Dogechat wallet.
 * Adapted from bitchat-android's Cashu implementation, but simplified for libdohj.
 */
class UIStateManager {

    // Send/Receive dialog state
    private val _showSendDialog = MutableLiveData(false)
    val showSendDialog: LiveData<Boolean> = _showSendDialog

    private val _showReceiveDialog = MutableLiveData(false)
    val showReceiveDialog: LiveData<Boolean> = _showReceiveDialog

    private val _sendType = MutableLiveData(WalletViewModel.SendType.DOGE)
    val sendType: LiveData<WalletViewModel.SendType> = _sendType

    private val _receiveType = MutableLiveData(WalletViewModel.ReceiveType.DOGE)
    val receiveType: LiveData<WalletViewModel.ReceiveType> = _receiveType

    // Success animation state
    private val _showSuccessAnimation = MutableLiveData(false)
    val showSuccessAnimation: LiveData<Boolean> = _showSuccessAnimation

    private val _successAnimationData = MutableLiveData<WalletViewModel.SuccessAnimationData?>(null)
    val successAnimationData: LiveData<WalletViewModel.SuccessAnimationData?> = _successAnimationData

    // Failure animation state
    private val _showFailureAnimation = MutableLiveData(false)
    val showFailureAnimation: LiveData<Boolean> = _showFailureAnimation

    private val _failureAnimationData = MutableLiveData<WalletViewModel.FailureAnimationData?>(null)
    val failureAnimationData: LiveData<WalletViewModel.FailureAnimationData?> = _failureAnimationData

    // Loading and error state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    // Back navigation handler
    private var backHandler: (() -> Boolean)? = null

    // ---- Dialog Management ----

    fun showSendDialog() {
        _showSendDialog.value = true
    }

    fun hideSendDialog() {
        _showSendDialog.value = false
        _sendType.value = WalletViewModel.SendType.DOGE
    }

    fun showReceiveDialog() {
        _showReceiveDialog.value = true
    }

    fun hideReceiveDialog() {
        _showReceiveDialog.value = false
        _receiveType.value = WalletViewModel.ReceiveType.DOGE
    }

    fun setSendType(type: WalletViewModel.SendType) {
        _sendType.value = type
    }

    fun setReceiveType(type: WalletViewModel.ReceiveType) {
        _receiveType.value = type
    }

    // ---- Animation Management ----

    fun showSuccessAnimation(animationData: WalletViewModel.SuccessAnimationData) {
        _successAnimationData.value = animationData
        _showSuccessAnimation.value = true
    }

    fun hideSuccessAnimation() {
        _showSuccessAnimation.value = false
        _successAnimationData.value = null
    }

    fun showFailureAnimation(animationData: WalletViewModel.FailureAnimationData) {
        _failureAnimationData.value = animationData
        _showFailureAnimation.value = true
    }

    fun hideFailureAnimation() {
        _showFailureAnimation.value = false
        _failureAnimationData.value = null
    }

    // ---- Loading and Error Management ----

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setError(message: String) {
        _errorMessage.value = message
    }

    // ---- Back Navigation ----

    fun setBackHandler(handler: () -> Boolean) {
        backHandler = handler
    }

    fun handleBackPress(): Boolean {
        return backHandler?.invoke() ?: false
    }

    // ---- State Getters ----

    fun getCurrentSendType(): WalletViewModel.SendType =
        _sendType.value ?: WalletViewModel.SendType.DOGE

    fun getCurrentReceiveType(): WalletViewModel.ReceiveType =
        _receiveType.value ?: WalletViewModel.ReceiveType.DOGE

    fun isCurrentlyLoading(): Boolean = _isLoading.value ?: false

    fun getCurrentError(): String? = _errorMessage.value
}
