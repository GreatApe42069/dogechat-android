package com.dogechat.android.parsing

/**
 * Single source-of-truth for parsed message element types and token models.
 */

sealed class MessageElement {
    data class Text(val content: String) : MessageElement()
    data class DogePayment(val token: ParsedDogeToken) : MessageElement()
}

data class ParsedDogeToken(
    val originalString: String,
    /** amount in koinu (1 DOGE = 100_000_000 koinu) */
    val amountKoinu: Long,
    /** human friendly DOGE amount */
    val amountDoge: Double,
    val unit: String = "DOGE",
    val mintUrl: String? = null,
    val memo: String? = null,
    val proofCount: Int = 0
)
