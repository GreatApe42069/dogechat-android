package com.dogechat.android.wallet.net

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Independent Tor preference for the wallet (SPV) only.
 * Defaults to OFF so it doesn't affect chat Tor.
 */
object WalletTorPreferenceManager {
    private const val PREFS_NAME = "dogechat_wallet"
    private const val KEY_WALLET_TOR_MODE = "wallet_tor_mode" // values: OFF, ON

    private val _modeFlow = MutableStateFlow(com.dogechat.android.net.TorMode.OFF)
    val modeFlow: StateFlow<com.dogechat.android.net.TorMode> = _modeFlow

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_WALLET_TOR_MODE, com.dogechat.android.net.TorMode.OFF.name)
        val mode = runCatching { com.dogechat.android.net.TorMode.valueOf(saved ?: com.dogechat.android.net.TorMode.OFF.name) }
            .getOrDefault(com.dogechat.android.net.TorMode.OFF)
        _modeFlow.value = mode
    }

    fun set(context: Context, mode: com.dogechat.android.net.TorMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_WALLET_TOR_MODE, mode.name).apply()
        _modeFlow.value = mode
    }

    fun get(context: Context): com.dogechat.android.net.TorMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_WALLET_TOR_MODE, com.dogechat.android.net.TorMode.OFF.name)
        return runCatching { com.dogechat.android.net.TorMode.valueOf(saved ?: com.dogechat.android.net.TorMode.OFF.name) }
            .getOrDefault(com.dogechat.android.net.TorMode.OFF)
    }
}