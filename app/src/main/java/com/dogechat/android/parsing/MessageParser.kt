package com.dogechat.android.parsing

import android.net.Uri
import android.util.Log
import org.bitcoinj.core.Coin

/**
 * Main message parser for Dogechat messages
 * Detects Dogecoin payment requests and normal text
 */
class MessageParser {

    companion object {
        private const val TAG = "MessageParser"

        // Singleton instance
        val instance = MessageParser()

        private fun logDebug(message: String) {
            try {
                Log.d(TAG, message)
            } catch (e: RuntimeException) {
                println("[$TAG] $message")
            }
        }

        private fun logWarning(message: String, throwable: Throwable? = null) {
            try {
                if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
            } catch (e: RuntimeException) {
                println("[$TAG] WARNING: $message")
                throwable?.printStackTrace()
            }
        }

        private fun logError(message: String, throwable: Throwable? = null) {
            try {
                if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
            } catch (e: RuntimeException) {
                println("[$TAG] ERROR: $message")
                throwable?.printStackTrace()
            }
        }
    }

    /**
     * Parse message content and return list of MessageElement
     */
    fun parseMessage(content: String): List<MessageElement> {
        val elements = mutableListOf<MessageElement>()

        try {
            var currentIndex = 0

            // Regex: "Đ<amount>@<address> [#memo]"
            val dogePattern = """Đ([0-9]+(?:\.[0-9]+)?)@([DLM][A-Za-z0-9]+)(?:\s+#?(.+))?""".toRegex()
            val matches = dogePattern.findAll(content).toList()

            for (match in matches) {
                // Add text before match
                if (match.range.first > currentIndex) {
                    val beforeText = content.substring(currentIndex, match.range.first)
                    if (beforeText.isNotEmpty()) elements.add(MessageElement.Text(beforeText))
                }

                // Parse details
                val amountDoge = match.groups[1]?.value?.toDoubleOrNull() ?: 0.0
                val amount = Coin.valueOf((amountDoge * 100_000_000).toLong())
                val address = match.groups[2]?.value ?: ""
                val memo = match.groups[3]?.value

                val payment = ParsedDogePayment(
                    amount = amount,
                    address = address,
                    memo = memo,
                    originalString = match.value
                )

                elements.add(MessageElement.DogePayment(payment))
                logDebug("Parsed Doge payment: $amountDoge Ð to $address, memo=$memo")

                currentIndex = match.range.last + 1
            }

            // Handle BIP21 dogecoin: URIs
            val bip21Pattern = """dogecoin:([DLM][A-Za-z0-9]+)(\?[^\s]+)?""".toRegex()
            bip21Pattern.findAll(content).forEach { match ->
                val address = match.groups[1]?.value ?: return@forEach
                val query = match.groups[2]?.value ?: ""
                val uri = Uri.parse("dogecoin:$address$query")

                val amountParam = uri.getQueryParameter("amount")
                val memo = uri.getQueryParameter("message")

                val amount = try {
                    Coin.valueOf(((amountParam?.toDoubleOrNull() ?: 0.0) * 100_000_000).toLong())
                } catch (e: Exception) {
                    Coin.ZERO
                }

                val payment = ParsedDogePayment(
                    amount = amount,
                    address = address,
                    memo = memo,
                    originalString = match.value
                )

                elements.add(MessageElement.DogePayment(payment))
                logDebug("Parsed Dogecoin URI: ${amount.toFriendlyString()} to $address, memo=$memo")
            }

            // Add trailing text
            if (currentIndex < content.length) {
                val remainingText = content.substring(currentIndex)
                if (remainingText.isNotEmpty()) elements.add(MessageElement.Text(remainingText))
            }

            if (elements.isEmpty()) elements.add(MessageElement.Text(content))

        } catch (e: Exception) {
            logError("Error parsing message", e)
            elements.clear()
            elements.add(MessageElement.Text(content))
        }

        return elements
    }
}

/**
 * Sealed class representing different types of message elements
 */
sealed class MessageElement {
    data class Text(val content: String) : MessageElement()
    data class DogePayment(val payment: ParsedDogePayment) : MessageElement()
}

/**
 * Represents a parsed Dogecoin payment
 */
data class ParsedDogePayment(
    val amount: Coin,
    val address: String,
    val memo: String?,
    val originalString: String
)
