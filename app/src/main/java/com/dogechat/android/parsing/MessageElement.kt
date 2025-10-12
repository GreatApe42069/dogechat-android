package com.dogechat.android.parsing

/**
 * Unified message element + token model used across the parsing/ui layers.
 *
 * ParsedDogeToken stores the raw amount in Koinu (1 DOGE = 100,000,000 koinu)
 * and provides a computed amountDoge for callers that expect a floating DOGE value.
 */

sealed class MessageElement {
    data class Text(val content: String) : MessageElement()
    data class DogePayment(val token: ParsedDogeToken) : MessageElement()
}

/**
 * Parsed representation of an inline DOGE payment mention found in a message.
 *
 * - amountKoinu: amount in koinu (smallest DOGE unit, long)
 * - address: recipient Dogecoin address (string)
 * - memo: optional memo
 * - originalString: the exact substring found in the message (for logging / fallback)
 * - mintUrl: optional param if you ever add mint/payment-request style tokens
 */
data class ParsedDogeToken(
    val amountKoinu: Long,
    val address: String,
    val memo: String?,
    val originalString: String,
    val mintUrl: String? = null
) {
    val amountDoge: Double get() = amountKoinu / 100_000_000.0
}
