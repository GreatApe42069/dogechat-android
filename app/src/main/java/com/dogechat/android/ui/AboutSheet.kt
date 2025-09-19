package com.dogechat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dogechat.android.nostr.NostrProofOfWork
import com.dogechat.android.nostr.PoWPreferenceManager

/**
 * About Sheet for dogechat app information
 * Simple working version with wallet/Tor separation and proper flow usage
 * Matches the design language of LocationChannelsSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Get version name from package info
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "0.9.5" // fallback version
        }
    }

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )

    // Color scheme matching LocationChannelsSheet
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
    val standardBlue = Color(0xFF007AFF) // iOS blue
    val standardGreen = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D) // iOS green

    if (isPresented) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = modifier
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = "Đogechat",
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = versionName,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface.copy(alpha = 0.6f),
                                style = LocalTextStyle.current.copy(
                                    baselineShift = BaselineShift(-0.1f)
                                )
                            )
                        }
                        Text(
                            text = "Đecentralized mesh messaging over Bluetooth",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // Theme selection
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Theme",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val themePref by com.dogechat.android.ui.theme.ThemePreferenceManager.themeFlow.collectAsState(
                                initial = com.dogechat.android.ui.theme.ThemePreference.System
                            )
                            FilterChip(
                                selected = themePref == com.dogechat.android.ui.theme.ThemePreference.System,
                                onClick = { com.dogechat.android.ui.theme.ThemePreferenceManager.set(context, com.dogechat.android.ui.theme.ThemePreference.System) },
                                label = { Text("system", fontFamily = FontFamily.Monospace) }
                            )
                            FilterChip(
                                selected = themePref == com.dogechat.android.ui.theme.ThemePreference.Light,
                                onClick = { com.dogechat.android.ui.theme.ThemePreferenceManager.set(context, com.dogechat.android.ui.theme.ThemePreference.Light) },
                                label = { Text("light", fontFamily = FontFamily.Monospace) }
                            )
                            FilterChip(
                                selected = themePref == com.dogechat.android.ui.theme.ThemePreference.Dark,
                                onClick = { com.dogechat.android.ui.theme.ThemePreferenceManager.set(context, com.dogechat.android.ui.theme.ThemePreference.Dark) },
                                label = { Text("dark", fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }
                }

                // Proof of Work section
                item {
                    val ctx = LocalContext.current

                    // Initialize PoW preferences if not already done
                    LaunchedEffect(Unit) {
                        PoWPreferenceManager.init(ctx)
                    }

                    // provide initial values to avoid delegate errors
                    val powEnabled by PoWPreferenceManager.powEnabled.collectAsState(initial = false)
                    val powDifficulty by PoWPreferenceManager.powDifficulty.collectAsState(initial = 8)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Such Proof of Work",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = powEnabled,
                                onCheckedChange = { enabled ->
                                    PoWPreferenceManager.setPowEnabled(ctx, enabled)
                                }
                            )
                            Text(
                                text = if (powEnabled) "enabled" else "disabled",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        if (powEnabled) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "difficulty: $powDifficulty",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Slider(
                                    value = powDifficulty.toFloat(),
                                    onValueChange = { newValue ->
                                        PoWPreferenceManager.setPowDifficulty(ctx, newValue.toInt())
                                    },
                                    valueRange = 1f..16f,
                                    steps = 14,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Status indicators section
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Network Status",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )

                        StatusItem(
                            icon = Icons.Default.Bluetooth,
                            label = "Bluetooth Mesh",
                            status = "Active",
                            color = standardGreen
                        )

                        StatusItem(
                            icon = Icons.Default.Security,
                            label = "Network Tor",
                            status = "Independent",
                            color = standardBlue
                        )

                        StatusItem(
                            icon = Icons.Default.Lock,
                            label = "Wallet SPV",
                            status = "Separated",
                            color = standardGreen
                        )
                    }
                }

                // Footer
                item {
                    Text(
                        text = "Open Sourced • Privacy First • Đecentralized",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusItem(
    icon: ImageVector,
    label: String,
    status: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}