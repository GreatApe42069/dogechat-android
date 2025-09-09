package com.dogechat.android.wallet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dogechat.android.wallet.WalletManager
import com.dogechat.android.wallet.viewmodel.WalletViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

/** Simple UI model used inside this file. */
data class DogeTransaction(
    val id: String,
    val type: TxType,
    val status: TxStatus,
    val timestamp: Date,
    val amountDoge: Double,
    val description: String? = null,
    val details: String? = null
)

enum class TxType { SEND, RECEIVE }
enum class TxStatus { PENDING, CONFIRMED, FAILED, EXPIRED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistory(
    viewModel: WalletViewModel,
    modifier: Modifier = Modifier,
    onTransactionClick: (DogeTransaction) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    // Collect the raw TxRow list from the ViewModel
    val rows by viewModel.history.collectAsState()

    // Map WalletManager.TxRow -> DogeTransaction for display
    val transactions = rows.map { row ->
        val numeric = Regex("""[0-9]+(?:\.[0-9]+)?""")
            .find(row.value)?.value?.toDoubleOrNull() ?: 0.0

        val status = when {
            row.confirmations <= 0 -> TxStatus.PENDING
            row.confirmations >= 1 -> TxStatus.CONFIRMED
            else -> TxStatus.PENDING
        }

        DogeTransaction(
            id = row.hash,
            type = if (row.isIncoming) TxType.RECEIVE else TxType.SEND,
            status = status,
            timestamp = row.time ?: Date(),
            amountDoge = numeric,
            description = null,
            details = row.hash
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = "Transaction History",
                tint = Color(0xFF00C851),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Transaction History",
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        if (transactions.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Receipt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No transactions yet",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your transaction history will appear here",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { tx ->
                    TransactionItem(transaction = tx, onClick = { onTransactionClick(tx) })
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: DogeTransaction,
    onClick: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A),
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (transaction.type) {
                            TxType.SEND -> Icons.Filled.Send
                            TxType.RECEIVE -> Icons.Filled.CallReceived
                        },
                        contentDescription = null,
                        tint = when (transaction.type) {
                            TxType.SEND -> Color(0xFFFF5722)
                            TxType.RECEIVE -> Color(0xFF4CAF50)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (transaction.type) {
                            TxType.SEND -> "Send"
                            TxType.RECEIVE -> "Receive"
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }

                TransactionStatusChip(status = transaction.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDateTime(transaction.timestamp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Text(
                    text = formatAmount(transaction.amountDoge, transaction.type),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (transaction.type) {
                        TxType.SEND -> Color(0xFFFF5722)
                        TxType.RECEIVE -> Color(0xFF4CAF50)
                    }
                )
            }

            transaction.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "\"$desc\"",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            transaction.details?.let { details ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Details",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (details.length > 12) details.take(8) + "...${details.takeLast(4)}" else details,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )

                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(details)) }, modifier = Modifier.size(20.dp)) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy Details",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionStatusChip(status: TxStatus) {
    val (backgroundColor, textColor, text) = when (status) {
        TxStatus.PENDING -> Triple(Color(0xFFFFC107).copy(alpha = 0.16f), Color(0xFFFFC107), "PENDING")
        TxStatus.CONFIRMED -> Triple(Color(0xFF4CAF50).copy(alpha = 0.16f), Color(0xFF4CAF50), "CONFIRMED")
        TxStatus.FAILED -> Triple(Color(0xFFFF5722).copy(alpha = 0.16f), Color(0xFFFF5722), "FAILED")
        TxStatus.EXPIRED -> Triple(Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.6f), "EXPIRED")
    }

    Box(
        modifier = Modifier
            .background(color = backgroundColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

private fun formatDateTime(date: Date): String {
    val now = Date()
    val diffMillis = max(0L, now.time - date.time)
    val diffMinutes = diffMillis / (1000 * 60)
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "${diffMinutes}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        diffDays < 7 -> "${diffDays}d ago"
        else -> SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(date)
    }
}

private fun formatAmount(amountDoge: Double, type: TxType): String {
    val prefix = when (type) {
        TxType.SEND -> "-"
        TxType.RECEIVE -> "+"
    }
    // show up to 4 decimal places
    val formatted = String.format(Locale.getDefault(), "%.4f", amountDoge)
    return "${prefix}Đ$formatted"
}
