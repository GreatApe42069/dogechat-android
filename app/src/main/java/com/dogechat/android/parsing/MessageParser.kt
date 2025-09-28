package com.dogechat.android.parsing

import android.util.Log

/**
 * Parse message content and return list of MessageElement.
 * Looks for inline DOGE tokens and returns MessageElement.DogePayment.
 */
class MessageParser {
    private val dogeParser = DogeTokenParser()

    companion object {
        private const val TAG = "MessageParser"
        val instance = MessageParser()

        private fun logD(msg: String) = try { Log.d(TAG, msg) } catch (_: Throwable) {}
        private fun logW(msg: String, t: Throwable? = null) = try {
            if (t != null) Log.w(TAG, msg, t) else Log.w(TAG, msg)
        } catch (_: Throwable) { println("[$TAG] WARN: $msg"); t?.printStackTrace() }
        private fun logE(msg: String, t: Throwable? = null) = try {
            if (t != null) Log.e(TAG, msg, t) else Log.e(TAG, msg)
        } catch (_: Throwable) { println("[$TAG] ERROR: $msg"); t?.printStackTrace() }
    }

    fun parseMessage(content: String): List<MessageElement> {
        val elements = mutableListOf<MessageElement>()
        try {
            var currentIndex = 0
            val matches = """Đ([0-9]+(?:\.[0-9]+)?)@([DLM][A-Za-z0-9]{20,45})(?:\s+#?(.+))?""".toRegex().findAll(content).toList()

            for (m in matches) {
                if (m.range.first > currentIndex) {
                    val before = content.substring(currentIndex, m.range.first)
                    if (before.isNotEmpty()) elements.add(MessageElement.Text(before))
                }

                val tokenStr = m.value
                val parsed = dogeParser.parseToken(tokenStr)
                if (parsed != null) {
                    elements.add(MessageElement.DogePayment(parsed))
                    logD("Parsed DOGE token: ${parsed.amountDoge} DOGE to ${parsed.address}")
                } else {
                    elements.add(MessageElement.Text(tokenStr))
                    logW("Failed to parse token as doge: $tokenStr")
                }
                currentIndex = m.range.last + 1
            }

            if (currentIndex < content.length) {
                val tail = content.substring(currentIndex)
                if (tail.isNotEmpty()) elements.add(MessageElement.Text(tail))
            }

            if (elements.isEmpty()) elements.add(MessageElement.Text(content))
        } catch (e: Exception) {
            logE("Error parsing message", e)
            elements.clear()
            elements.add(MessageElement.Text(content))
        }

        return elements
    }
}
