package com.dogechat.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.dogechat.android.nostr.RelayDirectory

@HiltAndroidApp
class DogechatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize relay directory (loads assets/nostr_relays.csv)
        RelayDirectory.initialize(this)
        // Hilt initialization happens automatically
    }
}
