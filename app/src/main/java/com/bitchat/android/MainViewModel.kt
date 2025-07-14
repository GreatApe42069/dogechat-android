package com.bitchat.android

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.bitchat.android.onboarding.BluetoothStatus
import com.bitchat.android.onboarding.LocationStatus

class MainViewModel : ViewModel() {
    
    private var _onboardingState by mutableStateOf(OnboardingState.CHECKING)
    val onboardingState: OnboardingState get() = _onboardingState
    
    private var _bluetoothStatus by mutableStateOf(BluetoothStatus.ENABLED)
    val bluetoothStatus: BluetoothStatus get() = _bluetoothStatus
    
    private var _locationStatus by mutableStateOf(LocationStatus.ENABLED)
    val locationStatus: LocationStatus get() = _locationStatus
    
    private var _errorMessage by mutableStateOf("")
    val errorMessage: String get() = _errorMessage
    
    private var _isBluetoothLoading by mutableStateOf(false)
    val isBluetoothLoading: Boolean get() = _isBluetoothLoading
    
    private var _isLocationLoading by mutableStateOf(false)
    val isLocationLoading: Boolean get() = _isLocationLoading
    
    // Public update functions for MainActivity
    fun updateOnboardingState(state: OnboardingState) {
        _onboardingState = state
    }
    
    fun updateBluetoothStatus(status: BluetoothStatus) {
        _bluetoothStatus = status
    }
    
    fun updateLocationStatus(status: LocationStatus) {
        _locationStatus = status
    }
    
    fun updateErrorMessage(message: String) {
        _errorMessage = message
    }
    
    fun updateBluetoothLoading(loading: Boolean) {
        _isBluetoothLoading = loading
    }
    
    fun updateLocationLoading(loading: Boolean) {
        _isLocationLoading = loading
    }
}