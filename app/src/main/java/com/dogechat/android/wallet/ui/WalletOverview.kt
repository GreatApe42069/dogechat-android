package com.dogechat.android.wallet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * WalletOverview composable for Dogechat (DOGE only).
 * Dogecoin gold accents.
 */
@Composable
fun WalletOverview(
    balance: String,
    shortAddress: String,
    syncPercent: Int,
    peerCount: Int,
    spvStatus: String,
    torEnabled: Boolean,
    torStatus: String?,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dogeGold = Color(0xFFFFB300)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top row: Balance + refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Balance",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = balance,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(onClick = onRefreshClick) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = dogeGold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Address + actions (separated for polish)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Address: $shortAddress",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(
                        onClick = onReceiveClick,
                        border = BorderStroke(1.dp, dogeGold),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = dogeGold),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Receive",
                            tint = dogeGold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Receive")
                    }

                    Button(
                        onClick = onSendClick,
                        colors = ButtonDefaults.buttonColors(containerColor = dogeGold, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Send")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sync bar and percent with status indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(
                    spvStatus = spvStatus,
                    peerCount = peerCount
                )
                Spacer(modifier = Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = (syncPercent.coerceIn(0, 100) / 100f),
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                    color = dogeGold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sync: $syncPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        peerCount == 0 -> Color.Red
                        syncPercent < 100 -> dogeGold
                        else -> Color(0xFF4CAF50)
                    }
                )
            }

            // Status text row (peers, SPV, Tor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = when {
                        peerCount == 0 -> "Not connected"
                        syncPercent < 100 -> "Syncing… ($peerCount peer${if (peerCount == 1) "" else "s"})"
                        else -> "Synced! ($peerCount peer${if (peerCount == 1) "" else "s"})"
                    },
                    color = when {
                        peerCount == 0 -> Color.Red
                        syncPercent < 100 -> dogeGold
                        else -> Color(0xFF4CAF50)
                    },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )

                if (torEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Tor",
                            tint = when {
                                torStatus?.lowercase()?.contains("running") == true -> Color(0xFF4CAF50)
                                torStatus?.lowercase()?.contains("connecting") == true -> dogeGold
                                else -> Color.Red
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tor: ${torStatus ?: "Unknown"}",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * StatusDot - Shows red/yellow/green depending on SPV/peer state
 */
@Composable
private fun StatusDot(
    spvStatus: String,
    peerCount: Int,
    modifier: Modifier = Modifier
) {
    val color = when {
        peerCount == 0 || spvStatus.equals("Not Connected", true) -> Color.Red
        spvStatus.equals("Syncing", true) -> Color(0xFFFFB300)
        else -> Color(0xFF4CAF50)
    }
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
    )
}