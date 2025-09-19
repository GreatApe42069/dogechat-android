package com.dogechat.android

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import com.dogechat.android.nostr.RelayDirectory
import com.dogechat.android.ui.theme.ThemePreferenceManager
import com.dogechat.android.net.TorManager

@HiltAndroidApp
class DogechatApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. Tor (route early outbound network)
        runCatching { TorManager.init(this) }
            .onSuccess { Log.d("DogechatApp", "TorManager initialized") }
            .onFailure { Log.w("DogechatApp", "TorManager init failed: ${it.message}") }

        // 2. Relay directory (loads assets/nostr_relays.csv)
        runCatching { RelayDirectory.initialize(this) }
            .onSuccess { Log.d("DogechatApp", "RelayDirectory loaded") }
            .onFailure { Log.w("DogechatApp", "RelayDirectory load failed: ${it.message}") }

        // 3. Favorites persistence (needed for routing / Nostr embedding)
        runCatching { com.dogechat.android.favorites.FavoritesPersistenceService.initialize(this) }
            .onSuccess { Log.d("DogechatApp", "FavoritesPersistence initialized") }
            .onFailure { Log.w("DogechatApp", "FavoritesPersistence init failed: ${it.message}") }

        // 4. Warm Nostr identity (ensures npub ready for early favorite notifications)
        runCatching { com.dogechat.android.nostr.NostrIdentityBridge.getCurrentNostrIdentity(this) }
            .onFailure { Log.w("DogechatApp", "Nostr identity warmup failed (will retry lazily): ${it.message}") }

        // 5. Theme preference
        ThemePreferenceManager.init(this)

        // 6. Debug preference manager (audit/developer toggles)
        runCatching { com.dogechat.android.ui.debug.DebugPreferenceManager.init(this) }
            .onFailure { Log.w("DogechatApp", "DebugPreferenceManager init failed: ${it.message}") }

        // Hilt injection: @HiltAndroidApp sets up DI graph automatically
    }
}