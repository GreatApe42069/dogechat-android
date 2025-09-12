package com.dogechat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

/**
 * WalletOverview composable for Dogechat (DOGE only).
 * Now includes status indicator for peer/SPV sync state.
 *
 * Parameters:
 *  - balance: user-friendly balance string (e.g. "12.34 DOGE")
 *  - shortAddress: shortened address for display
 *  - syncPercent: sync progress 0..100
 *  - peerCount: number of connected peers
 *  - spvStatus: string ("Not Connected", "Syncing", "Synced")
 *  - torEnabled: whether Tor is enabled for wallet connections
 *  - torStatus: optional string ("Running", "Connecting", "Off", etc.)
 *  - onSendClick/onReceiveClick/onRefreshClick: callbacks from parent
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
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
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
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(onClick = onRefreshClick) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF00C851)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Address + actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Address: $shortAddress",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    color = Color.LightGray
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Receive button (QR)
                Button(
                    onClick = onReceiveClick,
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00C851)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "Receive",
                        tint = Color(0xFF00C851)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Receive", color = Color(0xFF00C851))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Send button
                Button(
                    onClick = onSendClick,
                    modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C851)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Send", color = Color.Black)
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
                        .height(6.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sync: $syncPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        peerCount == 0 -> Color.Red
                        syncPercent < 100 -> Color(0xFFFFC107)
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
                        syncPercent < 100 -> Color(0xFFFFC107)
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
                                torStatus?.lowercase()?.contains("connecting") == true -> Color(0xFFFFC107)
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
        spvStatus.equals("Syncing", true) -> Color(0xFFFFC107)
        else -> Color(0xFF4CAF50)
    }
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
    )
}