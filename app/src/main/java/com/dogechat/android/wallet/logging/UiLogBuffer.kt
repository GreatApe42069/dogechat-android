package com.dogechat.android.wallet.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rolling UI action log buffer (button clicks, toggles, results).
 */
object UiLogBuffer {
    private const val MAX_LINES = 300
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun append(msg: String) {
        val line = "${fmt.format(Date())} $msg"
        val cur = _lines.value
        val next =
            if (cur.size >= MAX_LINES) (cur.drop(cur.size - MAX_LINES + 1) + line)
            else (cur + line)
        _lines.value = next
    }

    fun clear() {
        _lines.value = emptyList()
    }
}