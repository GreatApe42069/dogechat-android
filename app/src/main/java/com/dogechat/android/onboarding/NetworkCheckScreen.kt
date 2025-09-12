package com.dogechat.android.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Screen shown when checking network/internet/Tor status.
 * Follows the style of BluetoothCheckScreen/LocationCheckScreen.
 */
@Composable
fun NetworkCheckScreen(
    isInternetAvailable: Boolean,
    isDnsWorking: Boolean,
    isTorEnabled: Boolean,
    isTorUp: Boolean,
    onRetry: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    isLoading: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            !isInternetAvailable -> {
                NetworkProblemContent(
                    header = "No Internet Connection",
                    icon = Icons.Filled.CloudOff,
                    iconColor = Color.Red,
                    details = "Đogechat requires internet for Dogecoin wallet, Tor privacy, and online features. Please connect to Wi-Fi or mobile data.",
                    onRetry = onRetry,
                    onOpenNetworkSettings = onOpenNetworkSettings,
                    colorScheme = colorScheme,
                    isLoading = isLoading
                )
            }
            !isDnsWorking -> {
                NetworkProblemContent(
                    header = "DNS Not Working",
                    icon = Icons.Filled.CloudOff,
                    iconColor = Color(0xFFFFC107),
                    details = "Internet is up, but DNS lookups are failing. Check your network settings or try a different connection.",
                    onRetry = onRetry,
                    onOpenNetworkSettings = onOpenNetworkSettings,
                    colorScheme = colorScheme,
                    isLoading = isLoading
                )
            }
            isTorEnabled && !isTorUp -> {
                NetworkProblemContent(
                    header = "Tor Not Running",
                    icon = Icons.Filled.Lock,
                    iconColor = Color(0xFFFFC107),
                    details = "Tor enhanced privacy mode is enabled but Tor is not reachable. Please ensure Tor is running and try again.",
                    onRetry = onRetry,
                    onOpenNetworkSettings = onOpenNetworkSettings,
                    colorScheme = colorScheme,
                    isLoading = isLoading
                )
            }
            else -> {
                NetworkCheckingContent(
                    isTorEnabled = isTorEnabled,
                    isTorUp = isTorUp,
                    colorScheme = colorScheme
                )
            }
        }
    }
}

@Composable
private fun NetworkProblemContent(
    header: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    details: String,
    onRetry: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
    colorScheme: ColorScheme,
    isLoading: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = header,
            modifier = Modifier.size(64.dp),
            tint = iconColor
        )
        Text(
            text = header,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = iconColor
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text = details,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurface.copy(alpha = 0.8f)
            ),
            textAlign = TextAlign.Center
        )
        if (isLoading) {
            NetworkLoadingIndicator()
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Try Again")
                }
                OutlinedButton(
                    onClick = onOpenNetworkSettings,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Network Settings")
                }
            }
        }
    }
}

@Composable
private fun NetworkCheckingContent(
    isTorEnabled: Boolean,
    isTorUp: Boolean,
    colorScheme: ColorScheme
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CloudQueue,
            contentDescription = "Network OK",
            modifier = Modifier.size(64.dp),
            tint = colorScheme.primary
        )
        Text(
            text = if (isTorEnabled && isTorUp) "Internet & Tor Connected" else "Internet Connected",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary
            ),
            textAlign = TextAlign.Center
        )
        if (isTorEnabled) {
            Text(
                text = if (isTorUp) "Tor privacy mode is active. All wallet and chat traffic is routed through Tor for enhanced privacy."
                    else "Waiting for Tor connection...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.primary.copy(alpha = 0.8f)
                ),
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "Đogechat is online and ready for Dogecoin wallet, geohash channels, and updates.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface.copy(alpha = 0.8f)
                ),
                textAlign = TextAlign.Center
            )
        }
        NetworkLoadingIndicator()
    }
}

@Composable
private fun NetworkLoadingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "network_loading")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    Box(
        modifier = Modifier.size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotationAngle),
            color = Color(0xFF1976D2),
            strokeWidth = 3.dp
        )
    }
}