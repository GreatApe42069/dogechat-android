package com.dogechat.android.parsing

import android.util.Log
import org.dogecoinj.core.Address
import org.dogecoinj.core.NetworkParameters
import org.dogecoinj.params.MainNetParams
import org.dogecoinj.core.Coin

/**
 * Parser for Dogecoin payment tokens embedded in messages.
 * Example format: Đ<amount>@<address> #optional memo
 */
class DogeTokenParser {

    companion object {
        private const val TAG = "DogeTokenParser"

        private val params: NetworkParameters = MainNetParams.get()

        // Dogecoin constants
        const val DUST_LIMIT_KOINU: Long = 100_000_000 // 1 DOGE
        const val FEE_PER_KB_KOINU: Long = 1_000_000   // 0.01 DOGE

        private fun logDebug(message: String) {
            try { Log.d(TAG, message) } catch (e: RuntimeException) { println("[$TAG] $message") }
        }

        private fun logWarning(message: String, throwable: Throwable? = null) {
            try { if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message) }
            catch (e: RuntimeException) { println("[$TAG] WARNING: $message"); throwable?.printStackTrace() }
        }

        private fun logError(message: String, throwable: Throwable? = null) {
            try { if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message) }
            catch (e: RuntimeException) { println("[$TAG] ERROR: $message"); throwable?.printStackTrace() }
        }
    }

    /**
     * Parse a Doge payment token string
     * Returns ParsedDogeToken or null if invalid
     */
    fun parseToken(tokenString: String): ParsedDogeToken? {
        try {
            // Match format Đ<amount>@<address> #optional memo
            val regex = """Đ([0-9]+(?:\.[0-9]+)?)@([DLM][A-Za-z0-9]+)(?:\s+#?(.+))?""".toRegex()
            val match = regex.matchEntire(tokenString.trim())

            if (match != null) {
                val amountDoge = match.groups[1]?.value?.toDoubleOrNull() ?: return null
                val amountKoinu = (amountDoge * 100_000_000).toLong()
                val addressStr = match.groups[2]?.value ?: return null
                val memo = match.groups[3]?.value

                // Validate address
                try {
                    Address.fromBase58(params, addressStr)
                } catch (e: Exception) {
                    logWarning("Invalid Dogecoin address: $addressStr", e)
                    return null
                }

                // Ensure dust limit
                if (amountKoinu < DUST_LIMIT_KOINU) {
                    logWarning("Amount below dust limit: $amountKoinu Koinu")
                    return null
                }

                logDebug("Parsed Doge token: $amountDoge Đ to $addressStr, memo=$memo")

                return ParsedDogeToken(
                    originalString = tokenString,
                    amountKoinu = amountKoinu,
                    address = addressStr,
                    memo = memo
                )
            } else {
                logWarning("Token string does not match Doge token format: $tokenString")
                return null
            }
        } catch (e: Exception) {
            logError("Error parsing Doge token", e)
            return null
        }
    }
}

/**
 * Represents a parsed Dogecoin payment token
 */
data class ParsedDogeToken(
    val originalString: String,
    val amountKoinu: Long,
    val address: String,
    val memo: String?
)
