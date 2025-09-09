package com.dogechat.android.parsing

sealed class MessageElement {
    data class Text(val content: String) : MessageElement()
    data class DogePayment(val token: ParsedDogeToken) : MessageElement()
}
