package com.dogechat.android.wallet

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dogechat.android.wallet.ui.WalletScreen

class WalletActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // read extras using the canonical keys
        val amountKoinu = intent?.getLongExtra("token_amount_koinu", -1L)
        val address = intent?.getStringExtra("token_address")
        val memo = intent?.getStringExtra("token_memo")
        val original = intent?.getStringExtra("token_original")
        val raw = intent?.getStringExtra("token_raw") // optional fallback

        // Log for debugging
        Log.d("WalletActivity", "start with amountKoinu=$amountKoinu address=$address memo=$memo original=$original raw=$raw")

        // Pass initial parameters into WalletScreen if you want; the WalletScreen/viewmodel can also fetch them from intent.
        setContent {
            WalletScreen(
                initialTokenAmountKoinu = if (amountKoinu != null && amountKoinu >= 0L) amountKoinu else null,
                initialTokenAddress = address,
                initialTokenMemo = memo,
                initialTokenOriginal = original ?: raw
            )
        }
    }
}
