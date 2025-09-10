package com.dogechat.android.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dogechat.android.wallet.ui.WalletScreen

class WalletActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We intentionally do NOT wrap Compose call in try/catch.
        // Let WalletScreen/ViewModel handle missing/invalid extras.
        setContent {
            WalletScreen()
        }
    }
}
