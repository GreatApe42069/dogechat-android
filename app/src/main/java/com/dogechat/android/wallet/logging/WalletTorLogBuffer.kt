package com.dogechat.android.wallet.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolling log buffer for Wallet Tor (Arti) events/log lines.
 */
object WalletTorLogBuffer {
    private const val MAX_LINES = 400
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun append(raw: String) {
        val line = "${fmt.format(Date())} $raw"
        val cur = _lines.value
        val next =
            if (cur.size >= MAX_LINES) (cur.drop(cur.size - MAX_LINES + 1) + line)
            else (cur + line)
        _lines.value = next
    }

    fun clear() { _lines.value = emptyList() }
}