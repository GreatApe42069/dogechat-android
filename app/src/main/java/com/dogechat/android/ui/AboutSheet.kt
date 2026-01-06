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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dogechat.android.nostr.NostrProofOfWork
import com.dogechat.android.nostr.PoWPreferenceManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dogechat.android.R
import com.dogechat.android.core.ui.component.button.CloseButton
import com.dogechat.android.net.TorMode
import com.dogechat.android.net.TorPreferenceManager
import com.dogechat.android.net.ArtiTorManager
import com.dogechat.android.wallet.WalletManager
import com.dogechat.android.wallet.WalletManager.Companion.SpvController
import com.dogechat.android.wallet.logging.AppLog
import com.dogechat.android.wallet.logging.SpvLogBuffer
import com.dogechat.android.wallet.net.TorManagerWallet

/**
 * Feature row for displaying app capabilities
 */
@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Theme selection chip with Apple-like styling
 */
@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
        } else {
            colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Color.White else colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Unified settings toggle row with icon, title, subtitle, and switch
 * Apple-like design with proper spacing
 */
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    statusIndicator: (@Composable () -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.4f)
                )
                statusIndicator?.invoke()
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurface.copy(alpha = if (enabled) 0.6f else 0.3f),
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Switch(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * Apple-like About/Settings Sheet with high-quality design
 * Professional UX optimized for checkout scenarios
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

    // Colors requested
    val dogeGold = Color(0xFFFFD700)
    val brandAccent = Color(0xFFFFFF00) // bright doge yellow

    // Init wallet Tor prefs for wallet section
    LaunchedEffect(Unit) {
        com.dogechat.android.wallet.net.WalletTorPreferenceManager.init(context)
    }

    // Get version name from package info
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "1.0.0" // fallback version
        }
    }

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    val lazyListState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0
        }
    }
    val topBarAlpha by animateFloatAsState(
        targetValue = if (isScrolled) 0.98f else 0f,
        label = "topBarAlpha"
    )

    // Color scheme
    val colorScheme = MaterialTheme.colorScheme
    val isDark =
        colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
    val standardGreen = if (isDark) Color(0xFF32D74B) else Color(0xFF248A3D)
    val warnOrange = Color(0xFFFF9500)

    if (isPresented) {
        ModalBottomSheet(
            modifier = modifier.statusBarsPadding(),
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = colorScheme.background,
            dragHandle = null
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 80.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Header Section - App Identity
                    item(key = "header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp
                                    ),
                                    color = dogeGold // Title color -> dogeGold
                                )

                                Text(
                                    text = stringResource(R.string.version_prefix, versionName?:""),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onBackground.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        baselineShift = BaselineShift(0.1f)
                                    )
                                )
                            }

                            Text(
                                text = "Đecentralized Mesh messaging • Much end-to-end encryption",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Features section
                    item(key = "feature_offline") {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = stringResource(R.string.cd_offline_mesh_chat),
                                tint = Color(0xFF007AFF), // Bluetooth icon color as requested
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.about_offline_mesh_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = brandAccent
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Communicate directly via Bluetooth LE without internet or servers. Messages relay through nearby devices to extend range.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    item(key = "feature_geohash") {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = stringResource(R.string.cd_online_geohash_channels),
                                tint = standardGreen, // Geohash icon -> standardGreen
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.about_online_geohash_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = brandAccent
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Connect with people in your area using geohash-based channels. Extend the mesh using public internet relays.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    item(key = "feature_encryption") {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "End-to-End Encryption",
                                tint = brandAccent, // E2EE icon -> brandAccent
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
                                    color = brandAccent
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Private messages are encrypted. Channel messages are public.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                    // New: Dogecoin Wallet feature card (below encryption)
                    item(key = "feature_wallet") {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = "Đogecoin Wallet",
                                tint = dogeGold, // wallet icon -> dogeGold
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
                                    color = brandAccent
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Light doge wallet (spv) with tor support for privacy-preserving node connectivity. Manage addresses and send/receive dogecoin.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Appearance Section
                    item(key = "appearance_section") {
                        Text(
                            text = stringResource(R.string.about_appearance),
                            style = MaterialTheme.typography.labelLarge,
                            color = brandAccent, // requested section title color
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp, bottom = 8.dp)
                        )
                        val themePref by com.dogechat.android.ui.theme.ThemePreferenceManager.themeFlow.collectAsState()
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = themePref.isSystem,
                                onClick = {
                                    AppLog.action("AboutSheet", "theme", "system")
                                    com.dogechat.android.ui.theme.ThemePreferenceManager.set(
                                        context,
                                        com.dogechat.android.ui.theme.ThemePreference.System
                                    )
                                },
                                label = { Text(stringResource(R.string.about_system), fontFamily = FontFamily.Monospace) }
                            )
                            FilterChip(
                                selected = themePref.isLight,
                                onClick = {
                                        AppLog.action("AboutSheet", "theme", "light")
                                        com.dogechat.android.ui.theme.ThemePreferenceManager.set(
                                            context,
                                            com.dogechat.android.ui.theme.ThemePreference.Light
                                        )
                                },
                                label = { Text("light", fontFamily = FontFamily.Monospace) }
                            )
                            FilterChip(
                                selected = themePref.isDark,
                                onClick = {
                                    AppLog.action("AboutSheet", "theme", "dark")
                                    com.dogechat.android.ui.theme.ThemePreferenceManager.set(
                                        context,
                                        com.dogechat.android.ui.theme.ThemePreference.Dark
                                    )
                                },
                                label = { Text("dark", fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }

                    // Proof of Work Section
                    item(key = "pow_section") {
                        Text(
                            text = "Such Proof of Work",
                            style = MaterialTheme.typography.labelLarge,
                            color = brandAccent, // requested section title color
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 24.dp, bottom = 8.dp)
                        )
                        LaunchedEffect(Unit) {
                            PoWPreferenceManager.init(context)
                        }

                        val powEnabled by PoWPreferenceManager.powEnabled.collectAsState()
                        val powDifficulty by PoWPreferenceManager.powDifficulty.collectAsState()
                        var backgroundEnabled by remember { mutableStateOf(com.dogechat.android.service.MeshServicePreferences.isBackgroundEnabled(true)) }
                        val torMode = remember { mutableStateOf(TorPreferenceManager.get(context)) }
                        val torProvider = remember { ArtiTorManager.getInstance() }
                        val torStatus by torProvider.statusFlow.collectAsState()
                        val torAvailable = remember { torProvider.isTorAvailable() }

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
                                    onClick = {
                                        AppLog.action("AboutSheet", "pow", "OFF")
                                        PoWPreferenceManager.setPowEnabled(false)
                                    },
                                    label = { Text("pow off", fontFamily = FontFamily.Monospace) }
                                )
                                FilterChip(
                                    selected = powEnabled,
                                    onClick = {
                                        AppLog.action("AboutSheet", "pow", "ON")
                                        PoWPreferenceManager.setPowEnabled(true)
                                    },
                                    label = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("pow on", fontFamily = FontFamily.Monospace)
                                            if (powEnabled) {
                                                Surface(
                                                    color = standardGreen,
                                                    shape = RoundedCornerShape(50)
                                                ) { Box(Modifier.size(8.dp)) }
                                            }
                                        }
                                    }
                                )
                            }

                            Text(
                                text = "Add Much Proof of Work to geohash messages for Such spam Đeterrence",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            if (powEnabled) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Much Đifficulty: $powDifficulty bits (~${NostrProofOfWork.estimateMiningTime(powDifficulty)})",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )

                                    // Slider track/thumb -> dogeGold
                                    Slider(
                                        value = powDifficulty.toFloat(),
                                        onValueChange = {
                                            PoWPreferenceManager.setPowDifficulty(it.toInt())
                                            AppLog.action("AboutSheet", "powDifficulty", it.toInt().toString())
                                        },
                                        valueRange = 0f..32f,
                                        steps = 33,
                                        colors = SliderDefaults.colors(
                                            thumbColor = dogeGold,
                                            activeTrackColor = dogeGold
                                        )
                                    )

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Much Đifficulty $powDifficulty requires ~${NostrProofOfWork.estimateWork(powDifficulty)} hash attempts",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = when {
                                                    powDifficulty == 0 -> "No Proof Of Work required"
                                                    powDifficulty <= 8 -> "very low - minimal spam protection"
                                                    powDifficulty <= 12 -> "low - basic spam protection"
                                                    powDifficulty <= 16 -> "medium - good spam protection"
                                                    powDifficulty <= 20 -> "High - Such Strong spam Protection"
                                                    powDifficulty <= 24 -> "Very High - may cause delays"
                                                    else -> "Such Đegenerate - significant computation required"
                                                },
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

    // Flows for Network (chat Tor)
    val torMode = remember { mutableStateOf(com.dogechat.android.net.TorPreferenceManager.get(context)) }
    val torStatus by com.dogechat.android.net.TorManager.statusFlow.collectAsState()

    // Flows for Wallet
    val spvEnabled by SpvController.enabled.collectAsState(initial = false)
    val spvStatus by SpvController.status.collectAsState()
    val spvLogs by SpvLogBuffer.lines.collectAsState()
    val walletTorMode by com.dogechat.android.wallet.net.WalletTorPreferenceManager.modeFlow.collectAsState(
        initial = com.dogechat.android.net.TorMode.OFF
    )
    val walletTorStatus by TorManagerWallet.status.collectAsState()
                    // Network (Tor) section
                    item(key = "network_section") {
                        Text(
                            text = "Network",
                            style = MaterialTheme.typography.labelLarge,
                            color = brandAccent, // requested section title color
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
                                    selected = torMode.value == com.dogechat.android.net.TorMode.OFF,
                                    onClick = {
                                        AppLog.action("AboutSheet", "chatTor", "OFF")
                                        torMode.value = com.dogechat.android.net.TorMode.OFF
                                        com.dogechat.android.net.TorPreferenceManager.set(context, torMode.value)
                                    },
                                    label = { Text("tor off", fontFamily = FontFamily.Monospace) }
                                )
                                FilterChip(
                                    selected = torMode.value == com.dogechat.android.net.TorMode.ON,
                                    onClick = {
                                        AppLog.action("AboutSheet", "chatTor", "ON")
                                        torMode.value = com.dogechat.android.net.TorMode.ON
                                        com.dogechat.android.net.TorPreferenceManager.set(context, torMode.value)
                                    },
                                    label = {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("tor on", fontFamily = FontFamily.Monospace)
                                            val statusColor = when {
                                                torStatus.running && torStatus.bootstrapPercent < 100 -> warnOrange
                                                torStatus.running && torStatus.bootstrapPercent >= 100 -> standardGreen
                                                else -> Color.Red
                                            }
                                            Surface(color = statusColor, shape = CircleShape) {
                                                Box(Modifier.size(8.dp))
                                            }
                                        }
                                    }
                                )
                            }
                            Text(
                                text = "Such route internet over tor for Very Enhanced privacy",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            if (torMode.value == com.dogechat.android.net.TorMode.ON) {
                                val statusText = if (torStatus.running) "Running" else "Stopped"
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "tor status: $statusText, bootstrap ${torStatus.bootstrapPercent}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurface.copy(alpha = 0.75f)
                                        )
                                        val lastLog = torStatus.lastLogLine
                                        if (lastLog.isNotEmpty()) {
                                            Text(
                                                text = "last: ${lastLog.take(160)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Wallet (SPV) section
                    item(key = "wallet_spv_section") {
                        Text(
                            text = "Đoge Wallet (spv)",
                            style = MaterialTheme.typography.labelLarge,
                            color = brandAccent, // requested section title color
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
                                    selected = !spvEnabled,
                                    onClick = {
                                        AppLog.action("AboutSheet", "spv", "OFF")
                                        SpvController.set(context, false)
                                    },
                                    label = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("spv off", fontFamily = FontFamily.Monospace)
                                            // Only show red when OFF is selected AND the service is confirmed stopped
                                            if (!spvEnabled && !spvStatus.running) {
                                                Surface(color = Color.Red, shape = CircleShape) {
                                                    Box(Modifier.size(8.dp))
                                                }
                                            }
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
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("spv on", fontFamily = FontFamily.Monospace)
                                            // Show indicator only when ON is selected
                                            if (spvEnabled) {
                                                val indColor = when {
                                                    !spvStatus.running -> Color.Red // Red only if ON selected but not running
                                                    spvStatus.syncPercent < 100 -> warnOrange // Orange for syncing
                                                    else -> standardGreen // Green for fully synced
                                                }
                                                Surface(color = indColor, shape = CircleShape) {
                                                    Box(Modifier.size(8.dp))
                                                }
                                            }
                                        }
                                    }
                                )
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "spv: " + (if (spvStatus.running) "running" else "stopped") +
                                                ", peers=" + spvStatus.peerCount + ", sync=" + spvStatus.syncPercent + "%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                    val last = spvLogs.lastOrNull()
                                    if (!last.isNullOrEmpty()) {
                                        Text(
                                            text = "last: " + last.take(160),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Wallet Tor (SPV only) section
                    item(key = "wallet_tor_section") {
                        val app = context.applicationContext as Application
                        Text(
                            text = "Wallet tor (spv only)",
                            style = MaterialTheme.typography.labelLarge,
                            color = brandAccent, // requested section title color
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .padding(top = 16.dp, bottom = 8.dp)
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
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("wallet tor off", fontFamily = FontFamily.Monospace)
                                            // Only show red when OFF is selected AND the service is confirmed stopped
                                            if (walletTorMode == com.dogechat.android.net.TorMode.OFF && !walletTorStatus.running) {
                                                Surface(color = Color.Red, shape = CircleShape) {
                                                    Box(Modifier.size(8.dp))
                                                }
                                            }
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
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("wallet tor on", fontFamily = FontFamily.Monospace)
                                            // Show indicator only when ON is selected
                                            if (walletTorMode == com.dogechat.android.net.TorMode.ON) {
                                                val indColor = when {
                                                    !walletTorStatus.running -> Color.Red // Red only if ON selected but not running
                                                    walletTorStatus.bootstrapPercent < 100 -> warnOrange // Orange for bootstrapping
                                                    else -> standardGreen // Green for fully connected
                                                }
                                                Surface(color = indColor, shape = CircleShape) {
                                                    Box(Modifier.size(8.dp))
                                                }
                                            }
                                        }
                                    }
                                )
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "wallet tor: " + (if (walletTorStatus.running) "running" else "stopped") +
                                                ", bootstrap=" + walletTorStatus.bootstrapPercent + "%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurface.copy(alpha = 0.75f)
                                    )
                                    val last = walletTorStatus.lastLogLine
                                    if (last.isNotEmpty()) {
                                        Text(
                                            text = "last: " + last.take(160),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Emergency Warning Section
                    item(key = "warning_section") {
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
                                    contentDescription = "Much Warning",
                                    tint = errorColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Emergency Đata Đeletion",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = errorColor
                                    )
                                    Text(
                                        text = "Such tip: triple-click the app title to emergency delete all stored data including messages, keys, and settings...Very Wiped!",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // Footer Section
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
                                        contentColor = Color(0xFF8D6E63)
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
                                text = "Very Open Source • Such Privacy First • Much Đecentralized",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = dogeGold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }

                // TopBar
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = topBarAlpha))
                ) {
                    CloseButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = dogeGold), // Close button color -> dogeGold
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.close_plain),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
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
                    text = "Enter Channel Password",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "Such Channel $channelName is So Password Protected. Enter the Secret Password to Very join.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.pwd_label), style = MaterialTheme.typography.bodyMedium) },
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
                        text = stringResource(R.string.join),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.cancel),
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
