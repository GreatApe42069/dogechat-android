package com.dogechat.android.parsing

import android.util.Log

/**
 * Lightweight parser that detects dogecoin inline tokens of the form:
 *   Đ<amount>@<address> [optional memo after whitespace or #]
 *
 * Examples:
 *   Đ1.23@DXXXXXXXXXXXXXXXXX
 *   Đ10@DNx... #thanks
 *
 * Returns a ParsedDogeToken or null if the string is not recognized.
 */
class DogeTokenParser {
    companion object {
        private const val TAG = "DogeTokenParser"
        private fun logD(msg: String) = try { Log.d(TAG, msg) } catch (_: Throwable) {}
    }

    private val pattern =
        // raw string for readability: Đ<amount> (optionally decimal) @ <address starting with D or L or M> optional memo
        """Đ([0-9]+(?:\.[0-9]+)?)@([DLM][A-Za-z0-9]{20,45})(?:\s+#?(.+))?""".toRegex()

    fun parseToken(tokenString: String): ParsedDogeToken? {
        try {
            val m = pattern.find(tokenString) ?: return null

            val amountDoge = m.groups[1]?.value?.toDoubleOrNull() ?: return null
            val amountKoinu = (amountDoge * 100_000_000.0).toLong()
            val address = m.groups[2]?.value ?: return null
            val memo = m.groups[3]?.value

            logD("Parsed DOGE: $amountDoge DOGE -> $amountKoinu koinu to $address memo=$memo")
            return ParsedDogeToken(
                amountKoinu = amountKoinu,
                address = address,
                memo = memo,
                originalString = tokenString
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse doge token: $tokenString", e)
            return null
        }
    }
}
