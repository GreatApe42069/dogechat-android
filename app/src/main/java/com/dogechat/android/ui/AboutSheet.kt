package com.dogechat.android.ui

import android.app.Application
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PaddingValues
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dogechat.android.net.TorManager
import com.dogechat.android.net.TorMode
import com.dogechat.android.net.TorPreferenceManager
import com.dogechat.android.nostr.NostrProofOfWork
import com.dogechat.android.nostr.PoWPreferenceManager
import com.dogechat.android.wallet.WalletManager
import com.dogechat.android.wallet.WalletManager.Companion.SpvController
import com.dogechat.android.wallet.logging.AppLog
import com.dogechat.android.wallet.logging.SpvLogBuffer
import com.dogechat.android.wallet.net.TorManagerWallet
import com.dogechat.android.wallet.net.WalletTorPreferenceManager

/**
 * About Sheet for dogechat app information
 * Matches the design language of LocationChannelsSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit,
    onShowDebug: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Get version name from package info
    val versionName by remember {
        mutableStateOf(
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (_: Exception) {
                "0.0.0" // fallback version
            }
        )
    }

    LaunchedEffect(Unit) {
        WalletTorPreferenceManager.init(context)
    }

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    // Theme helpers
    val colorScheme = MaterialTheme.colorScheme
    val isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f
    val standardGreen = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
    val warnOrange = Color(0xFFFF9500)
    val dogeGold = if (isDark) Color(0xFFFFD700) else Color(0xFFFFB300)

    // Flows
    val chatTorStatus by TorManager.statusFlow.collectAsState()
    val chatTorMode by TorPreferenceManager.modeFlow.collectAsState(
        initial = TorPreferenceManager.get(context)
    )
    val spvEnabled by SpvController.enabled.collectAsState(initial = false)
    val spvStatus by SpvController.status.collectAsState()
    val spvLogs by SpvLogBuffer.lines.collectAsState()
    val walletTorMode by WalletTorPreferenceManager.modeFlow.collectAsState(
        initial = TorMode.OFF
    )
    val walletTorStatus by TorManagerWallet.status.collectAsState()

    // Scroll state and animated top bar alpha
    val lazyListState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
        }
    }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.95f else 0f,
        label = "topBarAlpha"
    )

    if (isPresented) {
        ModalBottomSheet(
            modifier = modifier.statusBarsPadding(),
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
            dragHandle = null
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 80.dp, bottom = 20.dp)
                ) {
                    headerSection(versionName, colorScheme)
                    featureOfflineSection()
                    featureGeohashSection()
                    featureEncryptionSection()
                    featureWalletSection(dogeGold, colorScheme)

                    appearanceSection()

                    powSection(isDark)

                    networkSection(
                        chatTorMode = chatTorMode,
                        chatTorStatus = chatTorStatus,
                        isDark = isDark
                    )

                    walletSpvSection(standardGreen, warnOrange, colorScheme, spvEnabled, spvStatus, spvLogs)

                    walletTorSection(standardGreen, warnOrange, colorScheme, walletTorMode, walletTorStatus)

                    warningSection()

                    footerSection(onShowDebug)

                    // add bottom spacer
                    item { Spacer(Modifier.height(16.dp)) }
                }

                // TopBar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = topBarAlpha))
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Close",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

private fun LazyListScope.headerSection(versionName: String, colorScheme: androidx.compose.material3.ColorScheme) {
    item(key = "header") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Đogechat",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = colorScheme.onBackground
                )

                Text(
                    text = "v$versionName",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onBackground.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        baselineShift = BaselineShift(0.1f)
                    )
                )
            }

            Text(
                text = "Đecentralized mesh messaging with end-to-end encryption",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

private fun LazyListScope.featureOfflineSection() {
    item(key = "feature_offline") {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = "Offline Mesh Chat",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Offline Mesh Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Communicate directly via Bluetooth LE without a SIM card, internet or servers — messages relay through nearby devices to extend the mesh range.",
                    style = MaterialTheme.typTypography.bodySmall(),
                )
            }
        }
    }
}

private fun LazyListScope.featureGeohashSection() {
    item(key = "feature_geohash") {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = "Online Geohash Channels",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Online Geohash Channels",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect with shibes in your area using geohash-based channels. Extend the mesh using Nostr decentralized public internet relays.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun LazyListScope.featureEncryptionSection() {
    item(key = "feature_encryption") {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "End-to-End Encryption",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "End-to-End Encryption",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Private messages are encrypted. Channel messages can be public or encrypted per-channel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun LazyListScope.featureWalletSection(dogeGold: Color, colorScheme: androidx.compose.material3.ColorScheme) {
    item(key = "feature_wallet") {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AccountBalanceWallet,
                contentDescription = "Dogecoin Wallet (SPV) with Tor",
                tint = dogeGold,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Đogecoin Wallet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Light wallet (SPV) with Tor support for privacy-preserving node connectivity. Manage addresses and send/receive Đogecoin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onBackground.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun LazyListScope.appearanceSection() {
    item(key = "appearance_section") {
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 8.dp)
        )
        val context = LocalContext.current
        val themePref by com.dogechat.android.ui.theme.ThemePreferenceManager.themeFlow.collectAsState()
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = themePref.isSystem,
                onClick = { com.dogechat.android.ui.theme.ThemePreferenceManager.set(context, com.dogechat.android.ui.theme.ThemePreference.System) },
                label = { Text("system", fontFamily = FontFamily.Monospace) }
            )
            FilterChip(
                selected = themePref.isLight,
                onClick = { com.dogechat.android.ui.theme.ThemePreferenceManager.set(context, com.dogechat.android.ui.theme.ThemePreference.Light) },
                label = { Text("light", fontFamily = FontFamily.Monospace) }
            )
            FilterChip(
                selected = themePref.isDark,
                onClick = { com.dogechat.android.ui.theme.ThemePreferenceManager.set(context, com.dogechat.android.ui.theme.ThemePreference.Dark) },
                label = { Text("dark", fontFamily = FontFamily.Monospace) }
            )
        }
    }
}

private fun LazyListScope.powSection(isDark: Boolean) {
    item(key = "pow_section") {
        val context = LocalContext.current
        Text(
            text = "Such Proof of Work",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 8.dp)
        )
        LaunchedEffect(Unit) {
            PoWPreferenceManager.init(context)
        }

        val powEnabled by PoWPreferenceManager.powEnabled.collectAsState()
        val powDifficulty by PoWPreferenceManager.powDifficulty.collectAsState()

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !powEnabled,
                    onClick = { PoWPreferenceManager.setPowEnabled(false) },
                    label = { Text("pow off", fontFamily = FontFamily.Monospace) }
                )
                FilterChip(
                    selected = powEnabled,
                    onClick = { PoWPreferenceManager.setPowEnabled(true) },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("pow on", fontFamily = FontFamily.Monospace)
                            if (powEnabled) {
                                IndicatorDot(if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D))
                            }
                        }
                    }
                )
            }

            Text(
                text = "Add Proof of Work to geohash messages for spam deterrence.",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (powEnabled) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Difficulty: $powDifficulty bits (~${NostrProofOfWork.estimateMiningTime(powDifficulty)})",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )

                    Slider(
                        value = powDifficulty.toFloat(),
                        onValueChange = { PoWPreferenceManager.setPowDifficulty(it.toInt()) },
                        valueRange = 0f..32f,
                        steps = 33,
                        colors = SliderDefaults.colors(
                            thumbColor = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D),
                            activeTrackColor = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
                        )
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Difficulty $powDifficulty requires ~${NostrProofOfWork.estimateWork(powDifficulty)} hash attempts",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = when {
                                    powDifficulty == 0 -> "No Proof Of Work Required"
                                    powDifficulty <= 8 -> "Very low - minimal spam protection"
                                    powDifficulty <= 12 -> "Low - basic spam protection"
                                    powDifficulty <= 16 -> "Medium - good spam protection"
                                    powDifficulty <= 20 -> "High - strong spam protection"
                                    powDifficulty <= 24 -> "Very high - may cause delays"
                                    else -> "Extreme - significant computation required"
                                },
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.networkSection(
    chatTorMode: TorMode,
    chatTorStatus: com.dogechat.android.net.TorStatus,
    isDark: Boolean
) {
    item(key = "network_section") {
        val context = LocalContext.current
        var torMode by remember { mutableStateOf(chatTorMode) }
        Text(
            text = "Network",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = torMode == TorMode.OFF,
                    onClick = {
                        torMode = TorMode.OFF
                        TorPreferenceManager.set(context, torMode)
                    },
                    label = { Text("tor off", fontFamily = FontFamily.Monospace) }
                )
                FilterChip(
                    selected = torMode == TorMode.ON,
                    onClick = {
                        torMode = TorMode.ON
                        TorPreferenceManager.set(context, torMode)
                    },
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("tor on", fontFamily = FontFamily.Monospace)
                            val statusColor = when {
                                chatTorStatus.running && chatTorStatus.bootstrapPercent < 100 -> Color(0xFFFF9500)
                                chatTorStatus.running && chatTorStatus.bootstrapPercent >= 100 -> if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
                                else -> Color.Red
                            }
                            IndicatorDot(statusColor)
                        }
                    }
                )
            }
            Text(
                text = "Route internet over Tor for enhanced privacy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            if (torMode == TorMode.ON) {
                val statusText = if (chatTorStatus.running) "Running" else "Stopped"
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "tor Status: $statusText, bootstrap ${chatTorStatus.bootstrapPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                        val lastLog = chatTorStatus.lastLogLine
                        if (lastLog.isNotEmpty()) {
                            Text(
                                text = "Last: ${lastLog.take(160)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.walletSpvSection(
    standardGreen: Color,
    warnOrange: Color,
    colorScheme: androidx.compose.material3.ColorScheme,
    spvEnabled: Boolean,
    spvStatus: com.dogechat.android.wallet.net.SpvStatus,
    spvLogs: List<String>
) {
    item(key = "wallet_spv") {
        val spvIndicatorColor = when {
            !spvStatus.running -> Color.Red
            spvStatus.syncPercent < 100 -> warnOrange
            else -> standardGreen
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Wallet (SPV)",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !spvEnabled,
                    onClick = {
                        AppLog.action("AboutSheet", "spv", "OFF")
                        SpvController.set(LocalContext.current, false)
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
                        SpvController.set(LocalContext.current, true)
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
}

private fun LazyListScope.walletTorSection(
    standardGreen: Color,
    warnOrange: Color,
    colorScheme: androidx.compose.material3.ColorScheme,
    walletTorMode: TorMode,
    walletTorStatus: com.dogechat.android.wallet.net.TorStatus
) {
    item(key = "wallet_tor") {
        val context = LocalContext.current
        val app = context.applicationContext as Application
        val walletTorIndicatorColor = when {
            !walletTorStatus.running -> Color.Red
            walletTorStatus.bootstrapPercent < 100 -> warnOrange
            else -> standardGreen
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Wallet Tor (SPV only)",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = walletTorMode == TorMode.OFF,
                    onClick = {
                        AppLog.action("AboutSheet", "walletTor", "OFF")
                        WalletTorPreferenceManager.set(context, TorMode.OFF)
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
                            if (walletTorMode == TorMode.OFF && !walletTorStatus.running)
                                IndicatorDot(Color.Red)
                        }
                    }
                )
                FilterChip(
                    selected = walletTorMode == TorMode.ON,
                    onClick = {
                        AppLog.action("AboutSheet", "walletTor", "ON")
                        WalletTorPreferenceManager.set(context, TorMode.ON)
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
                            if (walletTorMode == TorMode.ON)
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
}

private fun LazyListScope.warningSection() {
    item(key = "warning_section") {
        val colorScheme = MaterialTheme.colorScheme
        val errorColor = colorScheme.error

        Surface(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .fillMaxWidth(),
            color = errorColor.copy(alpha = 0.1f),
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
                    tint = errorColor,
                    modifier = Modifier.size(16.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Emergency Data Deletion",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = errorColor
                    )
                    Text(
                        text = "Tip: triple‑click the app title to emergency delete all stored data including messages, keys, and settings.",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private fun LazyListScope.footerSection(onShowDebug: (() -> Unit)?) {
    item(key = "footer") {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onShowDebug != null) {
                TextButton(
                    onClick = onShowDebug,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = "Đebug Settings",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Text(
                text = "Open Sourced • Privacy First • Đecentralized",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun IndicatorDot(color: Color) {
    Surface(color = color, shape = CircleShape) {
        Box(Modifier.size(8.dp))
    }
}

/**
 * Password prompt dialog for password-protected channels
 * Kept as dialog since it requires user input
 */
@Composable
fun PasswordPromptDialog(
    show: Boolean,
    channelName: String?,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (show && channelName != null) {
        val colorScheme = MaterialTheme.colorScheme

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = "🔐 Enter Channel Password",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "Channel $channelName is password protected. Enter the password to join.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = onPasswordChange,
                        label = { Text("Password", style = MaterialTheme.typography.bodyMedium) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(
                        text = "Join",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                }
            },
            containerColor = colorScheme.surface,
            tonalElevation = 8.dp
        )
    }
}

// Small extension to avoid repeating style
@Composable
private fun MaterialTheme.typTypography() = this.typography

@Composable
private fun androidx.compose.material3.Typography.bodySmall(): androidx.compose.material3.TextStyle {
    return this.bodySmall
}