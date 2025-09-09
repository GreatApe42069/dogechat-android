package com.dogechat.android.wallet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dogechat.android.wallet.ui.WalletScreen

class WalletActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenOrAddress = intent?.getStringExtra("token_or_address")
        setContent {
            WalletScreen(tokenOrAddress = tokenOrAddress) // implement WalletScreen to accept this
        }
    }
}
