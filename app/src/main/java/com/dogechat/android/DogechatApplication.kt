package com.dogechat.android

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import com.dogechat.android.nostr.RelayDirectory
import com.dogechat.android.ui.theme.ThemePreferenceManager
import com.dogechat.android.net.ArtiTorManager

@HiltAndroidApp
class DogechatApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Tor first so any early network goes over Tor
        try {
            val torProvider = ArtiTorManager.getInstance()
            torProvider.init(this)
        } catch (_: Exception){}

        // Initialize relay directory (loads assets/nostr_relays.csv)
        RelayDirectory.initialize(this)

        // Initialize LocationNotesManager dependencies early so sheet subscriptions can start immediately
        try { com.dogechat.android.nostr.LocationNotesInitializer.initialize(this) } catch (_: Exception) { }

        // Initialize favorites persistence early so MessageRouter/NostrTransport can use it on startup
        try {
            com.dogechat.android.favorites.FavoritesPersistenceService.initialize(this)
        } catch (_: Exception) { }

        // Warm up Nostr identity to ensure npub is available for favorite notifications
        try {
            com.dogechat.android.nostr.NostrIdentityBridge.getCurrentNostrIdentity(this)
        } catch (_: Exception) { }

        // Initialize theme preference
        ThemePreferenceManager.init(this)

        // Initialize debug preference manager (persists debug toggles)
        try { com.dogechat.android.ui.debug.DebugPreferenceManager.init(this) } catch (_: Exception) { }

        // Initialize mesh service preferences
        try { com.dogechat.android.service.MeshServicePreferences.init(this) } catch (_: Exception) { }

        // Proactively start the foreground service to keep mesh alive
        try { com.dogechat.android.service.MeshForegroundService.start(this) } catch (_: Exception) { }

        // Map warm-up: ensure the heat bus is hot from app launch (keeps warm start instant)
        runCatching {
            com.dogechat.android.ui.HeatStreamBus.setTTL(300_000L) // 5 minutes default TTL
        }.onFailure {
            Log.w("DogechatApp", "HeatStreamBus warm-up failed: ${it.message}")
        }

        // Wallet Tor proxy check - ensure Tor proxy settings are ready for wallet use
        runCatching {
            // Warm up wallet Tor manager to ensure proxy configuration is ready
            com.dogechat.android.wallet.net.WalletTorPreferenceManager.init(this)
            Log.d("DogechatApp", "Wallet Tor preferences initialized")
        }.onFailure {
            Log.w("DogechatApp", "Wallet Tor preferences init failed: ${it.message}")
        }
        // TorManager already initialized above
        // Hilt injection: @HiltAndroidApp sets up DI graph automatically
    }
}
