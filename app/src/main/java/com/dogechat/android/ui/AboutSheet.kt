package com.dogechat.android.ui

import android.content.Context
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
import com.dogechat.android.wallet.WalletManager
import com.dogechat.android.wallet.WalletManager.Companion.SpvController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // ensure wallet tor pref initialized
    LaunchedEffect(Unit) {
        com.dogechat.android.wallet.net.WalletTorPreferenceManager.init(context)
    }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "0.9.5"
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
    val standardBlue = Color(0xFF007AFF)
    val standardGreen = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
    val dogeGold = if (isDark) Color(0xFFFFD700) else Color(0xFFFFB300)

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
                                text = "v$versionName",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    baselineShift = BaselineShift(0.1f)
                                )
                            )
                        }
                        Text(
                            text = "Đecentralized mesh messaging with Much end-to-end encryption",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // Features
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureCard(
                            icon = Icons.Filled.Bluetooth,
                            iconColor = standardBlue,
                            title = "offline mesh chat",
                            description = "communicate directly via bluetooth le without internet or servers. messages relay through nearby devices to extend range.",
                            modifier = Modifier.fillMaxWidth()
                        )
                        FeatureCard(
                            icon = Icons.Filled.Public,
                            iconColor = standardGreen,
                            title = "online geohash channels",
                            description = "connect with people in your area using geohash-based channels. extend the mesh using public internet relays.",
                            modifier = Modifier.fillMaxWidth()
                        )
                        FeatureCard(
                            icon = Icons.Filled.Lock,
                            iconColor = if (isDark) Color(0xFFFFD60A) else Color(0xFFF5A623),
                            title = "end-to-end encryption",
                            description = "private messages are encrypted. channel messages are public.",
                            modifier = Modifier.fillMaxWidth()
                        )
                        FeatureCard(
                            icon = Icons.Filled.Security,
                            iconColor = dogeGold,
                            title = "Đogecoin wallet",
                            description = "light wallet (spv) with tor support for privacy-preserving node connectivity. manage addresses and send/receive doge.",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Appearance (unchanged)
                item {
                    val themePref by com.dogechat.android.ui.theme.ThemePreferenceManager.themeFlow.collectAsState(
                        initial = com.dogechat.android.ui.theme.ThemePreference.System
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "appearance",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                // Proof of Work (unchanged)
                item {
                    val ctx = LocalContext.current
                    LaunchedEffect(Unit) { PoWPreferenceManager.init(ctx) }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = !powEnabled,
                                onClick = { PoWPreferenceManager.setPowEnabled(false) },
                                label = { Text("pow off", fontFamily = FontFamily.Monospace) }
                            )
                            FilterChip(
                                selected = powEnabled,
                                onClick = { PoWPreferenceManager.setPowEnabled(true) },
                                label = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("pow on", fontFamily = FontFamily.Monospace)
                                        if (powEnabled) {
                                            Surface(color = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D), shape = RoundedCornerShape(50)) {
                                                Box(Modifier.size(8.dp))
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        Text(
                            text = "Add Much Proof of Work to geohash messages for Such spam deterrence.",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        if (powEnabled) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "difficulty: $powDifficulty bits (~${NostrProofOfWork.estimateMiningTime(powDifficulty)})",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Slider(
                                    value = powDifficulty.toFloat(),
                                    onValueChange = { PoWPreferenceManager.setPowDifficulty(it.toInt()) },
                                    valueRange = 0f..32f,
                                    steps = 33
                                )
                            }
                        }
                    }
                }

                // Network (Tor) — chats/geohash (unchanged)
                item {
                    val ctx = LocalContext.current
                    val torMode = remember { mutableStateOf(com.dogechat.android.net.TorPreferenceManager.get(ctx)) }
                    val torStatus by com.dogechat.android.net.TorManager.statusFlow.collectAsState()
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Network",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = torMode.value == com.dogechat.android.net.TorMode.OFF,
                                onClick = {
                                    torMode.value = com.dogechat.android.net.TorMode.OFF
                                    com.dogechat.android.net.TorPreferenceManager.set(ctx, torMode.value)
                                },
                                label = { Text("tor off", fontFamily = FontFamily.Monospace) }
                            )
                            FilterChip(
                                selected = torMode.value == com.dogechat.android.net.TorMode.ON,
                                onClick = {
                                    torMode.value = com.dogechat.android.net.TorMode.ON
                                    com.dogechat.android.net.TorPreferenceManager.set(ctx, torMode.value)
                                },
                                label = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("tor on", fontFamily = FontFamily.Monospace)
                                        val statusColor = when {
                                            torStatus.running && torStatus.bootstrapPercent < 100 -> Color(0xFFFF9500)
                                            torStatus.running && torStatus.bootstrapPercent >= 100 -> if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
                                            else -> Color.Red
                                        }
                                        Surface(color = statusColor, shape = RoundedCornerShape(50)) { Box(Modifier.size(8.dp)) }
                                    }
                                }
                            )
                        }
                        Text(
                            text = "Such route internet over tor for Very Enhanced privacy.",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                // Wallet (SPV) preference & status
                item {
                    val prefs = remember(context) { context.getSharedPreferences("dogechat_wallet", Context.MODE_PRIVATE) }
                    var spvEnabled by remember { mutableStateOf(prefs.getBoolean("spv_enabled", false)) }
                    val spvStatus = SpvController.status.collectAsState().value
                    val walletTorMode = remember { mutableStateOf(com.dogechat.android.wallet.net.WalletTorPreferenceManager.get(context)) }

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Wallet (SPV)",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = !spvEnabled,
                                onClick = {
                                    spvEnabled = false
                                    prefs.edit().putBoolean("spv_enabled", false).apply()
                                },
                                label = { Text("spv off", fontFamily = FontFamily.Monospace) }
                            )
                            FilterChip(
                                selected = spvEnabled,
                                onClick = {
                                    spvEnabled = true
                                    prefs.edit().putBoolean("spv_enabled", true).apply()
                                },
                                label = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("spv on", fontFamily = FontFamily.Monospace)
                                        val statusColor = if (!spvEnabled) Color.Red else Color(0xFFFF9500)
                                        Surface(color = statusColor, shape = RoundedCornerShape(50)) { Box(Modifier.size(8.dp)) }
                                    }
                                }
                            )
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (spvEnabled) "spv status: running (toggle off to stop)" else "spv status: stopped",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = "Changes take effect immediately if the wallet service is running.",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Wallet Tor toggle (independent from chat Tor)
                        Text(
                            text = "Wallet Tor (SPV only)",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = walletTorMode.value == com.dogechat.android.net.TorMode.OFF,
                                onClick = {
                                    walletTorMode.value = com.dogechat.android.net.TorMode.OFF
                                    com.dogechat.android.wallet.net.WalletTorPreferenceManager.set(context, walletTorMode.value)
                                    // if SPV running, restart to apply
                                    WalletManager.instanceRef?.let {
                                        if (SpvController.enabled.value) {
                                            it.stopNetwork()
                                            it.startNetwork()
                                        }
                                    }
                                },
                                label = { Text("wallet tor off", fontFamily = FontFamily.Monospace) }
                            )
                            FilterChip(
                                selected = walletTorMode.value == com.dogechat.android.net.TorMode.ON,
                                onClick = {
                                    walletTorMode.value = com.dogechat.android.net.TorMode.ON
                                    com.dogechat.android.wallet.net.WalletTorPreferenceManager.set(context, walletTorMode.value)
                                    WalletManager.instanceRef?.let {
                                        if (SpvController.enabled.value) {
                                            it.stopNetwork()
                                            it.startNetwork()
                                        }
                                    }
                                },
                                label = { Text("wallet tor on", fontFamily = FontFamily.Monospace) }
                            )
                        }

                        // SPV Tor status
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "tor status: " + (if (spvStatus.torRunning) "running" else "stopped") + ", bootstrap=" + spvStatus.torBootstrap + "%",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                                val last = spvStatus.lastLogLine
                                if (last.isNotEmpty()) {
                                    Text(
                                        text = "last: " + last.take(160),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Emergency warning (unchanged)
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Red.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFFBF1A1A),
                                modifier = Modifier.size(16.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Emergency data deletion",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFBF1A1A)
                                )
                                Text(
                                    text = "Such tip: triple-click the app title to emergency delete all stored data including messages, keys, and settings...Very Wiped!",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // Footer (unchanged)
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Very Open Source • Such Privacy First • Much Đecentralized",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 15.sp
                )
            }
        }
    }
}