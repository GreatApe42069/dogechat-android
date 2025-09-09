package com.dogechat.android.parsing

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data class representing a parsed Doge token embedded in a message
 */
data class ParsedDogeToken(
    val amountKoinu: Long,  // smallest unit, Koinu
    val address: String,
    val memo: String? = null,
    val originalString: String
)

/**
 * Sealed class for message elements
 */
sealed class MessageElement {
    data class Text(val content: String) : MessageElement()
    data class DogePayment(val token: ParsedDogeToken) : MessageElement()
}

/**
 * Render a list of message elements with proper inline layout
 */
@Composable
fun ParsedMessageContent(
    elements: List<MessageElement>,
    modifier: Modifier = Modifier,
    onDogePaymentClick: ((ParsedDogeToken) -> Unit)? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        var currentTextRow = mutableListOf<MessageElement>()

        for (element in elements) {
            when (element) {
                is MessageElement.Text -> currentTextRow.add(element)
                is MessageElement.DogePayment -> {
                    if (currentTextRow.isNotEmpty()) {
                        TextRow(elements = currentTextRow.toList())
                        currentTextRow.clear()
                    }
                    DogePaymentChip(
                        token = element.token,
                        onPaymentClick = onDogePaymentClick,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        if (currentTextRow.isNotEmpty()) {
            TextRow(elements = currentTextRow.toList())
        }
    }
}

/**
 * Render a row of text elements
 */
@Composable
fun TextRow(elements: List<MessageElement>) {
    Row(
        modifier = Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        elements.forEach { element ->
            when (element) {
                is MessageElement.Text -> {
                    Text(
                        text = element.content,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                }
                else -> { /* Skip non-text elements */ }
            }
        }
    }
}

/**
 * Chip component for displaying Doge payments inline
 */
@Composable
fun DogePaymentChip(
    token: ParsedDogeToken,
    modifier: Modifier = Modifier,
    onPaymentClick: ((ParsedDogeToken) -> Unit)? = null
) {
    Card(
        modifier = modifier
            .clickable {
                onPaymentClick?.invoke(token) ?: handleDogePayment(token)
            },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.25.dp, Color(0xFFFF9900))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top row: Dogecoin icon and label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF9900)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Đ",
                            fontSize = 10.sp,
                            color = Color.Black,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    }

                    Text(
                        text = "dogecoin",
                        fontSize = 16.sp,
                        color = Color(0xFFFF9900),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Middle row: Amount
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val amountDoge = token.amountKoinu.toDouble() / 100_000_000
                    Text(
                        text = "$amountDoge",
                        fontSize = 24.sp,
                        color = Color(0xFFFF9900),
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Đ",
                        fontSize = 16.sp,
                        color = Color(0xFFFF9900),
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Bottom row: Memo
                token.memo?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "\"$it\"",
                        fontSize = 10.sp,
                        color = Color(0xFFCCCCCC),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Right side: Receive button
            Button(
                onClick = { onPaymentClick?.invoke(token) ?: handleDogePayment(token) },
                modifier = Modifier.height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9900),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Receive",
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Receive",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

/**
 * Handle Dogecoin payment interaction
 * TODO: Integrate with DogecoinJ wallet (libdohj)
 */
private fun handleDogePayment(token: ParsedDogeToken) {
    Log.d("DogePayment", "Clicked Doge payment: ${token.originalString}")
    Log.d("DogePayment", "Amount (Koinu): ${token.amountKoinu}")
    Log.d("DogePayment", "Address: ${token.address}")
    token.memo?.let { Log.d("DogePayment", "Memo: $it") }

    // Example: libdohj integration placeholder
    // val amount = Coin.valueOf(token.amountKoinu)
    // val recipient = LegacyAddress.fromBase58(MainNetParams.get(), token.address)
    // val sendRequest = SendRequest.to(recipient, amount)
    // sendRequest.feePerKb = Transaction.DEFAULT_TX_FEE
    // val result = wallet.sendCoins(walletAppKit.peerGroup(), sendRequest)
}
