package com.dogechat.android.onboarding

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

/**
 * NetworkManager for checking internet connectivity and network status.
 * Also provides DNS and Tor checks if needed.
 */
class NetworkManager(private val context: Context) {

    companion object {
        private const val TAG = "NetworkManager"
    }

    /**
     * Checks if device has any internet connection (Wi-Fi, Mobile, etc.)
     */
    fun isInternetAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnected
        }
    }

    /**
     * Simple DNS check (does a lookup for google.com, returns true if successful)
     */
    fun isDnsWorking(): Boolean {
        return try {
            val address = java.net.InetAddress.getByName("google.com")
            !address.hostAddress.isNullOrEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "DNS check failed: ${e.message}")
            false
        }
    }

    /**
     * Checks if Tor is running and reachable on the expected local proxy.
     * Defaults to 127.0.0.1:9050 (SOCKS5).
     */
    fun isTorUp(torHost: String = "127.0.0.1", torPort: Int = 9050): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(torHost, torPort), 500)
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tor check failed: ${e.message}")
            false
        }
    }
}