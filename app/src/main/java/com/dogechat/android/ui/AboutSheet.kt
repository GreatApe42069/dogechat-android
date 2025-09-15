package com.dogechat.android.ui

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
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
import com.dogechat.android.wallet.WalletManager
import com.dogechat.android.wallet.WalletManager.Companion.SpvController
import com.dogechat.android.wallet.logging.SpvLogBuffer
import com.dogechat.android.wallet.net.TorManagerWallet
import com.dogechat.android.nostr.NostrProofOfWork
import com.dogechat.android.nostr.PoWPreferenceManager
import com.dogechat.android.wallet.logging.AppLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        com.dogechat.android.wallet.net.WalletTorPreferenceManager.init(context)
    }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            "0.9.5"
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val colorScheme = MaterialTheme.colorScheme
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    val standardGreen = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
    val warnOrange = Color(0xFFFF9500)
    val dogeGold = if (isDark) Color(0xFFFFD700) else Color(0xFFFFB300)

    // Flows
    val chatTorStatus by com.dogechat.android.net.TorManager.statusFlow.collectAsState()
    val chatTorMode by com.dogechat.android.net.TorPreferenceManager.modeFlow.collectAsState(
        initial = com.dogechat.android.net.TorPreferenceManager.get(context)
    )
    val spvEnabled by SpvController.enabled.collectAsState(initial = false)
    val spvStatus by SpvController.status.collectAsState()
    val spvLogs by SpvLogBuffer.lines.collectAsState()
    val walletTorMode by com.dogechat.android.wallet.net.WalletTorPreferenceManager.modeFlow.collectAsState(
        initial = com.dogechat.android.net.TorMode.OFF
    )
    val walletTorStatus by TorManagerWallet.status.collectAsState()

    if (!isPresented) return

    ModalBottomSheet(
        onDismissRequest = {
            AppLog.action("AboutSheet", "dismiss")
            onDismiss()
        },
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
                        iconColor = Color(0xFF007AFF),
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
                        icon = Icons.Filled.AccountBalanceWallet,
                        iconColor = dogeGold,
                        title = "Đogecoin wallet",
                        description = "light wallet (spv) with tor support for privacy-preserving node connectivity. manage addresses and send/receive doge.",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Appearance
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
                        ThemeChip("system", themePref == com.dogechat.android.ui.theme.ThemePreference.System) {
                            AppLog.action("AboutSheet", "theme", "system")
                            com.dogechat.android.ui.theme.ThemePreferenceManager.set(
                                context,
                                com.dogechat.android.ui.theme.ThemePreference.System
                            )
                        }
                        ThemeChip("light", themePref == com.dogechat.android.ui.theme.ThemePreference.Light) {
                            AppLog.action("AboutSheet", "theme", "light")
                            com.dogechat.android.ui.theme.ThemePreferenceManager.set(
                                context,
                                com.dogechat.android.ui.theme.ThemePreference.Light
                            )
                        }
                        ThemeChip("dark", themePref == com.dogechat.android.ui.theme.ThemePreference.Dark) {
                            AppLog.action("AboutSheet", "theme", "dark")
                            com.dogechat.android.ui.theme.ThemePreferenceManager.set(
                                context,
                                com.dogechat.android.ui.theme.ThemePreference.Dark
                            )
                        }
                    }
                }
            }

            // Proof of Work
            item {
                val ctx = LocalContext.current
                LaunchedEffect(Unit) {
                    PoWPreferenceManager.init(ctx)
                }
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
                            onClick = {
                                AppLog.action("AboutSheet", "pow", "OFF")
                                PoWPreferenceManager.setPowEnabled(false)
                            },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("pow off", fontFamily = FontFamily.Monospace)
                                    if (!powEnabled) IndicatorDot(Color.Red)
                                }
                            }
                        )
                        FilterChip(
                            selected = powEnabled,
                            onClick = {
                                AppLog.action("AboutSheet", "pow", "ON")
                                PoWPreferenceManager.setPowEnabled(true)
                            },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("pow on", fontFamily = FontFamily.Monospace)
                                    if (powEnabled) IndicatorDot(standardGreen)
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
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "difficulty: $powDifficulty bits (~${NostrProofOfWork.estimateMiningTime(powDifficulty)})",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Slider(
                                value = powDifficulty.toFloat(),
                                onValueChange = {
                                    PoWPreferenceManager.setPowDifficulty(it.toInt())
                                    AppLog.action("AboutSheet", "powDifficulty", it.toInt().toString())
                                },
                                valueRange = 0f..32f,
                                steps = 33
                            )
                        }
                    }
                }
            }

            // Network (chat Tor) — independent
            item {
                val indicatorColor = when {
                    !chatTorStatus.running -> Color.Red
                    chatTorStatus.bootstrapPercent < 100 -> warnOrange
                    else -> standardGreen
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Network", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface.copy(alpha = 0.8f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = chatTorMode == com.dogechat.android.net.TorMode.OFF,
                            onClick = {
                                AppLog.action("AboutSheet", "chatTor", "OFF")
                                com.dogechat.android.net.TorPreferenceManager.set(context, com.dogechat.android.net.TorMode.OFF)
                            },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("tor off", fontFamily = FontFamily.Monospace)
                                    if (chatTorMode == com.dogechat.android.net.TorMode.OFF && !chatTorStatus.running)
                                        IndicatorDot(Color.Red)
                                }
                            }
                        )
                        FilterChip(
                            selected = chatTorMode == com.dogechat.android.net.TorMode.ON,
                            onClick = {
                                AppLog.action("AboutSheet", "chatTor", "ON")
                                com.dogechat.android.net.TorPreferenceManager.set(context, com.dogechat.android.net.TorMode.ON)
                            },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("tor on", fontFamily = FontFamily.Monospace)
                                    if (chatTorMode == com.dogechat.android.net.TorMode.ON)
                                        IndicatorDot(indicatorColor)
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "tor status: " + (if (chatTorStatus.running) "running" else "stopped") +
                                        ", bootstrap=" + chatTorStatus.bootstrapPercent + "%",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            val last = chatTorStatus.lastLogLine
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

            // Wallet (SPV) — status + SPV logs (dns/peers/etc.)
            item {
                val spvIndicatorColor = when {
                    !spvStatus.running -> Color.Red
                    spvStatus.syncPercent < 100 -> warnOrange
                    else -> standardGreen
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wallet (SPV)", fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface.copy(alpha = 0.8f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !spvEnabled,
                            onClick = {
                                AppLog.action("AboutSheet", "spv", "OFF")
                                SpvController.set(context, false)
                            },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("spv off", fontFamily = FontFamily.Monospace)
                                    if (!spvEnabled && !spvStatus.running) IndicatorDot(Color.Red)
                                }
                            }
                        )
                        FilterChip(
                            selected = spvEnabled,
                            onClick = {
                                AppLog.action("AboutSheet", "spv", "ON")
                                SpvController.set(context, true)
                            },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("spv on", fontFamily = FontFamily.Monospace)
                                    if (spvEnabled) IndicatorDot(spvIndicatorColor)
                                }
                            }
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "spv: " + (if (spvStatus.running) "running" else "stopped") +
                                        ", peers=" + spvStatus.peerCount + ", sync=" + spvStatus.syncPercent + "%",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            if (spvLogs.isNotEmpty()) {
                                val last = spvLogs.last()
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

            // Wallet Tor (SPV only) — independent and real-time
            item {
                val app = context.applicationContext as Application
                val walletTorIndicatorColor = when {
                    !walletTorStatus.running -> Color.Red
                    walletTorStatus.bootstrapPercent < 100 -> warnOrange
                    else -> standardGreen
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wallet Tor (SPV only)", fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium, color = colorScheme.onSurface.copy(alpha = 0.8f))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = walletTorMode == com.dogechat.android.net.TorMode.OFF,
                            onClick = {
                                AppLog.action("AboutSheet", "walletTor", "OFF")
                                com.dogechat.android.wallet.net.WalletTorPreferenceManager.set(
                                    context,
                                    com.dogechat.android.net.TorMode.OFF
                                )
                                TorManagerWallet.stop()
                                WalletManager.instanceRef?.let {
                                    if (SpvController.enabled.value) {
                                        it.stopNetwork(); it.startNetwork()
                                    }
                                }
                            },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("wallet tor off", fontFamily = FontFamily.Monospace)
                                    if (walletTorMode == com.dogechat.android.net.TorMode.OFF && !walletTorStatus.running)
                                        IndicatorDot(Color.Red)
                                }
                            }
                        )
                        FilterChip(
                            selected = walletTorMode == com.dogechat.android.net.TorMode.ON,
                            onClick = {
                                AppLog.action("AboutSheet", "walletTor", "ON")
                                com.dogechat.android.wallet.net.WalletTorPreferenceManager.set(
                                    context,
                                    com.dogechat.android.net.TorMode.ON
                                )
                                TorManagerWallet.start(app)
                                WalletManager.instanceRef?.let {
                                    if (SpvController.enabled.value) {
                                        it.stopNetwork(); it.startNetwork()
                                    }
                                }
                            },
                            label = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("wallet tor on", fontFamily = FontFamily.Monospace)
                                    if (walletTorMode == com.dogechat.android.net.TorMode.ON)
                                        IndicatorDot(walletTorIndicatorColor)
                                }
                            }
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "wallet tor: " + (if (walletTorStatus.running) "running" else "stopped") +
                                        ", bootstrap=" + walletTorStatus.bootstrapPercent + "%",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                            val last = walletTorStatus.lastLogLine
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

            // Emergency
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

            // Footer
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

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontFamily = FontFamily.Monospace) }
    )
}

@Composable
private fun IndicatorDot(color: Color) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Box(Modifier.size(8.dp))
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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