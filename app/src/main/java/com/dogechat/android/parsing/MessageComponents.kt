package com.dogechat.android.parsing

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.bitcoinj.core.Coin
import com.dogechat.android.wallet.WalletManager

@Composable
fun ParsedMessageContent(
    elements: List<MessageElement>,
    modifier: Modifier = Modifier,
    onDogePaymentClick: ((ParsedDogeToken) -> Unit)? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val textRow = mutableListOf<MessageElement.Text>()

        fun flushTextRow() {
            if (textRow.isNotEmpty()) {
                TextRow(elements = textRow.toList())
                textRow.clear()
            }
        }

        for (el in elements) {
            when (el) {
                is MessageElement.Text -> textRow.add(el)
                is MessageElement.DogePayment -> {
                    flushTextRow()
                    DogePaymentChip(token = el.token, onPaymentClick = onDogePaymentClick)
                }
            }
        }
        flushTextRow()
    }
}

@Composable
private fun TextRow(elements: List<MessageElement.Text>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        elements.forEach { t ->
            Text(
                text = t.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun DogePaymentChip(
    token: ParsedDogeToken,
    modifier: Modifier = Modifier,
    onPaymentClick: ((ParsedDogeToken) -> Unit)? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPaymentClick?.invoke(token) ?: handlePaymentClick(token, isSend = false) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E0E)),
        border = androidx.compose.foundation.BorderStroke(0.25.dp, Color(0xFFFFD54F))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFFFD54F), shape = androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ð", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text("dogecoin", fontSize = 16.sp, color = Color(0xFFFFD54F), fontFamily = FontFamily.Monospace)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val amountCoin = Coin.valueOf(token.amountKoinu)
                    Text(
                        text = amountCoin.toPlainString(),
                        fontSize = 20.sp,
                        color = Color(0xFFFFD54F),
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("DOGE", fontSize = 12.sp, color = Color(0xFFFFD54F), fontFamily = FontFamily.Monospace)
                }

                token.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                    Text("\"$memo\"", fontSize = 10.sp, color = Color(0xFFBDBDBD), fontFamily = FontFamily.Monospace)
                }
            }

            Row {
                // Receive button
                Button(
                    onClick = { onPaymentClick?.invoke(token) ?: handlePaymentClick(token, isSend = false) },
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Download, contentDescription = "Receive", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Receive", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send button
                Button(
                    onClick = { onPaymentClick?.invoke(token) ?: handlePaymentClick(token, isSend = true) },
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Handles payment click, routing to WalletManager for either sending or requesting (receiving) DOGE.
 */
private fun handlePaymentClick(token: ParsedDogeToken, isSend: Boolean) {
    val amountCoin = Coin.valueOf(token.amountKoinu)
    Log.d("DogePayment", (if (isSend) "Sending" else "Requesting") +
        " ${amountCoin.toPlainString()} DOGE ${if (isSend) "→" else "from"} ${token.address} mem=”${token.memo}”"
    )
    try {
        if (isSend) {
            WalletManager.instance.sendCoins(
                address = token.address,
                amount = amountCoin,
                memo = token.memo
            )
        } else {
            WalletManager.instance.requestCoins(
                amount = amountCoin,
                memo = token.memo
            )
        }
    } catch (e: Exception) {
        Log.e("DogePayment", "Failed to ${if (isSend) "send" else "request"} DOGE", e)
    }
}
