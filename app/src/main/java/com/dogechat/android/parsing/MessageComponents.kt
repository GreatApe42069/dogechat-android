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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compose UI for parsed messages. Shows inline DOGE chips with both Receive and Send actions.
 *
 * Note: ParsedDogeToken uses amountKoinu (Long) as the smallest unit. Convert to DOGE double for display.
 */

@Composable
fun ParsedMessageContent(
    elements: List<MessageElement>,
    modifier: Modifier = Modifier,
    onDogeReceive: ((ParsedDogeToken) -> Unit)? = null,
    onDogeSend: ((ParsedDogeToken) -> Unit)? = null
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
                    DogePaymentChip(
                        token = el.token,
                        onReceive = onDogeReceive,
                        onSend = onDogeSend
                    )
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
    onReceive: ((ParsedDogeToken) -> Unit)? = null,
    onSend: ((ParsedDogeToken) -> Unit)? = null
) {
    // Convert koinu -> DOGE (koinu = 10^-8 DOGE)
    val amountDoge = token.amountKoinu / 100_000_000.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onReceive?.invoke(token) ?: handleDogeClick(token) },
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
                        Text("Ð", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black, textAlign = TextAlign.Center)
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text("dogecoin", fontSize = 16.sp, color = Color(0xFFFFD54F), fontFamily = FontFamily.Monospace)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format("%.8f", amountDoge),
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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    Button(
                        onClick = { onReceive?.invoke(token) ?: handleDogeClick(token) },
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Download, contentDescription = "Receive", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Receive", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onSend?.invoke(token) ?: handleDogeSend(token) },
                        modifier = Modifier.height(40.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCCCCCC), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Send, contentDescription = "Send", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun handleDogeClick(token: ParsedDogeToken) {
    Log.d("DogePayment", "Clicked token: ${token.originalString} (${token.amountKoinu} koinu) -> ${token.address}")
}

private fun handleDogeSend(token: ParsedDogeToken) {
    Log.d("DogePayment", "Send action for token: ${token.originalString} (${token.amountKoinu} koinu) -> invoke wallet send UI")
}
