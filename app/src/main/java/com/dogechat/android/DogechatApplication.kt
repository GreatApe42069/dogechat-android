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
        // Initialize favorites persistence early so MessageRouter/NostrTransport can use it on startup
        try {
            com.bitchat.android.favorites.FavoritesPersistenceService.initialize(this)
        } catch (_: Exception) { }

        // Warm up Nostr identity to ensure npub is available for favorite notifications
        try {
            com.dogechat.android.nostr.NostrIdentityBridge.getCurrentNostrIdentity(this)
        } catch (_: Exception) { }
    }
}
