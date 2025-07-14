package com.bitchat.android

enum class OnboardingState {
    CHECKING,
    BLUETOOTH_CHECK,
    LOCATION_CHECK,
    PERMISSION_EXPLANATION,
    PERMISSION_REQUESTING,
    INITIALIZING,
    COMPLETE,
    ERROR
}