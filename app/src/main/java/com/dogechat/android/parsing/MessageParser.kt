package com.dogechat.android.parsing

import android.util.Log
import java.util.regex.Pattern

/**
 * Main message parser for Dogechat messages
 * Detects Dogecoin payment tokens and normal text
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

            // Pattern: Đ<amount>@<address> optionally followed by memo after a space or #
            val dogePattern = """Đ([0-9]+(?:\.[0-9]+)?)@([DLM][A-Za-z0-9]+)(?:\s+#?(.+))?""".toRegex()
            val matches = dogePattern.findAll(content).toList()

            for (match in matches) {
                // Add text before the match
                if (match.range.first > currentIndex) {
                    val beforeText = content.substring(currentIndex, match.range.first)
                    if (beforeText.isNotEmpty()) elements.add(MessageElement.Text(beforeText))
                }

                // Extract token details
                val amountDoge = match.groups[1]?.value?.toDoubleOrNull() ?: 0.0
                val amountKoinu = (amountDoge * 100_000_000).toLong() // Convert to Koinu
                val address = match.groups[2]?.value ?: ""
                val memo = match.groups[3]?.value

                val token = ParsedDogeToken(
                    amountKoinu = amountKoinu,
                    address = address,
                    memo = memo,
                    originalString = match.value
                )

                elements.add(MessageElement.DogePayment(token))
                logDebug("Parsed Doge token: $amountDoge Đ to $address, memo=$memo")

                currentIndex = match.range.last + 1
            }

            // Add remaining text
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
    data class DogePayment(val token: ParsedDogeToken) : MessageElement()
}

/**
 * Represents a parsed Dogecoin payment token
 */
data class ParsedDogeToken(
    val amountKoinu: Long, // smallest unit
    val address: String,
    val memo: String?,
    val originalString: String
)
