package com.dogechat.android.wallet.logging

import android.util.Log

/**
 * Central logging hub. Mirrors to Logcat + in-app rolling buffers.
 */
object AppLog {
    enum class Channel { SPV, WALLET_TOR, UI }

    private fun toBuffer(channel: Channel, text: String) {
        when (channel) {
            Channel.SPV -> SpvLogBuffer.append(text)
            Channel.WALLET_TOR -> WalletTorLogBuffer.append(text)
            Channel.UI -> UiLogBuffer.append(text)
        }
    }

    fun d(channel: Channel, tag: String, msg: String) {
        Log.d(tag, msg)
        toBuffer(channel, "[D][$tag] $msg")
    }

    fun i(channel: Channel, tag: String, msg: String) {
        Log.i(tag, msg)
        toBuffer(channel, "[I][$tag] $msg")
    }

    fun w(channel: Channel, tag: String, msg: String, t: Throwable? = null) {
        Log.w(tag, msg, t)
        toBuffer(channel, "[W][$tag] $msg" + (t?.let { " cause=${it.message}" } ?: ""))
    }

    fun e(channel: Channel, tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
        toBuffer(channel, "[E][$tag] $msg" + (t?.let { " cause=${it.message}" } ?: ""))
    }

    fun crash(tag: String, msg: String, t: Throwable?) {
        val stack = t?.stackTraceToString()
        e(Channel.SPV, tag, "CRASH: $msg", t)
        CrashLogBuffer.append(tag, msg, stack)
    }

    fun action(source: String, event: String, details: String? = null) {
        val msg = "UI[$source] $event" + (details?.let { " | $it" } ?: "")
        i(Channel.UI, source, msg)
    }

    fun state(channel: Channel, tag: String, key: String, value: Any?) {
        d(channel, tag, "state $key=$value")
    }
}