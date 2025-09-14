package com.dogechat.android.wallet.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dogechat.android.wallet.WalletManager
import com.dogechat.android.wallet.WalletManager.Companion.SpvController
import com.dogechat.android.wallet.WalletManager.Companion.instanceRef
import com.dogechat.android.wallet.viewmodel.UIStateManager
import com.dogechat.android.wallet.viewmodel.WalletViewModel
import com.dogechat.android.ui.AboutSheet
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: WalletViewModel = hiltViewModel(),
    uiStateManager: UIStateManager = remember { UIStateManager() },
    initialTokenAmountKoinu: Long? = null,
    initialTokenAddress: String? = null,
    initialTokenMemo: String? = null,
    initialTokenOriginal: String? = null
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val prefs = remember(context) { context.getSharedPreferences("dogechat_wallet", android.content.Context.MODE_PRIVATE) }

    // Brand accent color (same as ChatScreen header and permissions)
    val brandAccent = Color(0xFFFFFF00)

    // Observe SPV toggle state for import guidance
    val spvEnabled by SpvController.enabled.collectAsState(initial = false)

    // ViewModel state
    val balance by viewModel.balance.collectAsState(initial = "0 DOGE")
    val addressFlowValue by viewModel.address.collectAsState(initial = null)
    val syncPercent by viewModel.syncPercent.collectAsState(initial = 0)
    val history by viewModel.history.collectAsState(initial = emptyList())

    // Persisted address fallback (so UI shows immediately)
    var persistedAddress by remember { mutableStateOf(prefs.getString("receive_address", null)) }
    LaunchedEffect(addressFlowValue) {
        addressFlowValue?.let {
            persistedAddress = it
            prefs.edit().putString("receive_address", it).apply()
        }
    }
    val displayAddress = addressFlowValue ?: persistedAddress ?: "Not ready"

    // UI state (dialogs, errors)
    val showSendDialog by uiStateManager.showSendDialog.observeAsState(false)
    val showReceiveDialog by uiStateManager.showReceiveDialog.observeAsState(false)
    val showSuccess by uiStateManager.showSuccessAnimation.observeAsState(false)
    val successData by uiStateManager.successAnimationData.observeAsState()
    val showFailure by uiStateManager.showFailureAnimation.observeAsState(false)
    val failureData by uiStateManager.failureAnimationData.observeAsState()
    val isLoading by uiStateManager.isLoading.observeAsState(false)
    val errorMessage by uiStateManager.errorMessage.observeAsState()

    // Private key dialogs state
    var showPrivKeyDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var privateKeyWif by remember { mutableStateOf<String?>(null) }

    // About sheet and triple-tap wipe
    var showAbout by remember { mutableStateOf(false) }
    var tapCount by remember { mutableStateOf(0) }
    var lastTapMs by remember { mutableStateOf(0L) }

    // Start wallet
    LaunchedEffect(Unit) { viewModel.startWallet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Đogecoin Wallet",
                        color = brandAccent,
                        modifier = Modifier.clickable {
                            val now = System.currentTimeMillis()
                            tapCount = if (now - lastTapMs < 600) tapCount + 1 else 1
                            lastTapMs = now
                            if (tapCount == 3) {
                                tapCount = 0
                                if (instanceRef?.wipeWalletData() == true) {
                                    // Clear UI-cached values immediately
                                    persistedAddress = null
                                    privateKeyWif = null
                                    Toast.makeText(context, "Wallet data wiped", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to wipe wallet", Toast.LENGTH_SHORT).show()
                                }
                            } else if (tapCount == 1) {
                                showAbout = true
                            }
                        }
                    )
                },
                actions = {
                    IconButton(onClick = {
                        privateKeyWif = instanceRef?.getOrExportAndCacheWif()
                            ?: instanceRef?.getCachedWif()
                        showPrivKeyDialog = true
                    }) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Export Private Key", tint = brandAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = brandAccent,
                    actionIconContentColor = brandAccent
                )
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(12.dp)
            ) {
                WalletOverview(
                    brandAccent = brandAccent,
                    balance = balance,
                    shortAddress = displayAddress,
                    fullAddress = displayAddress,
                    syncPercent = syncPercent,
                    onSendClick = { uiStateManager.showSendDialog() },
                    onReceiveClick = { uiStateManager.showReceiveDialog() },
                    onRefreshClick = { viewModel.startWallet() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Much Transactions",
                    style = MaterialTheme.typography.titleMedium,
                    color = brandAccent
                )
                Divider(modifier = Modifier.padding(vertical = 6.dp))

                WalletTransactionHistory(
                    rows = history,
                    brandAccent = brandAccent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            if (showSendDialog) {
                WalletSendDialog(
                    defaultAddress = displayAddress.takeIf { it != "Not ready" } ?: "",
                    isLoading = isLoading,
                    onDismiss = { uiStateManager.hideSendDialog(); uiStateManager.clearError() },
                    onSend = { toAddr, amountWholeDoge ->
                        uiStateManager.setLoading(true)
                        viewModel.sendCoins(toAddr, amountWholeDoge) { ok, msg ->
                            uiStateManager.setLoading(false)
                            if (ok) {
                                uiStateManager.showSuccessAnimation(
                                    WalletViewModel.SuccessAnimationData(message = "Sent: $msg", txHash = msg)
                                )
                                uiStateManager.hideSendDialog()
                                Toast.makeText(context, "Sent: $msg", Toast.LENGTH_SHORT).show()
                            } else {
                                uiStateManager.showFailureAnimation(
                                    WalletViewModel.FailureAnimationData(message = "Send failed", reason = msg)
                                )
                            }
                        }
                    }
                )
            }

            if (showReceiveDialog) {
                WalletReceiveDialog(
                    currentAddress = displayAddress,
                    onRequestAddress = { /* optional: trigger new address later */ },
                    onCopy = {
                        clipboard.setText(AnnotatedString(displayAddress))
                        Toast.makeText(context, "Copied Shibes Address", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { uiStateManager.hideReceiveDialog() }
                )
            }

            if (showSuccess) {
                WalletSuccessDialog(
                    data = successData ?: WalletViewModel.SuccessAnimationData("Success"),
                    onDismiss = { uiStateManager.hideSuccessAnimation() }
                )
            }

            if (showFailure) {
                WalletFailureDialog(
                    data = failureData ?: WalletViewModel.FailureAnimationData("Failure"),
                    onDismiss = { uiStateManager.hideFailureAnimation() }
                )
            }

            if (showPrivKeyDialog) {
                PrivateKeyDialog(
                    brandAccent = brandAccent,
                    privateKeyWif = privateKeyWif,
                    spvEnabled = spvEnabled,
                    onImportClick = { showImportDialog = true },
                    onDismiss = { showPrivKeyDialog = false }
                )
            }

            if (showImportDialog) {
                PrivateKeyImportDialog(
                    brandAccent = brandAccent,
                    spvEnabled = spvEnabled,
                    onImport = { wif ->
                        instanceRef?.importPrivateKeyWif(wif) { ok, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            if (ok) {
                                privateKeyWif = instanceRef?.getCachedWif() ?: wif
                                showImportDialog = false
                                showPrivKeyDialog = true
                            }
                        }
                    },
                    onDismiss = { showImportDialog = false }
                )
            }

            if (showAbout) {
                AboutSheet(
                    isPresented = true,
                    onDismiss = { showAbout = false }
                )
            }
        }
    )
}

/**
 * Wallet overview card
 */
@Composable
private fun WalletOverview(
    brandAccent: Color,
    balance: String,
    shortAddress: String,
    fullAddress: String,
    syncPercent: Int,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Such Balance", style = MaterialTheme.typography.bodySmall, color = brandAccent)
                    Text(text = balance, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onRefreshClick) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = brandAccent)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = brandAccent.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, brandAccent),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Address: $fullAddress",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(
                    onClick = onReceiveClick,
                    border = BorderStroke(1.dp, brandAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = brandAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = "Receive", tint = brandAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Much Receive")
                }

                Button(
                    onClick = onSendClick,
                    colors = ButtonDefaults.buttonColors(containerColor = brandAccent, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Very Send")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = (syncPercent.coerceIn(0, 100) / 100f),
                modifier = Modifier.fillMaxWidth(),
                color = brandAccent
            )
            Text(
                text = "Sync: $syncPercent%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/**
 * Send dialog
 */
@Composable
private fun WalletSendDialog(
    defaultAddress: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSend: (to: String, amountWholeDoge: Long) -> Unit
) {
    var toAddress by remember { mutableStateOf(defaultAddress) }
    var amountText by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val amt = amountText.toLongOrNull()
                if (amt == null || amt <= 0L) {
                    Toast.makeText(context, "Enter amount (whole ĐOGE)", Toast.LENGTH_SHORT).show()
                    return@TextButton
                }
                onSend(toAddress, amt)
            }) {
                Text(if (isLoading) "Sending ĐOGE..." else "Send ĐOGE")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Very Cancel") }
        },
        title = { Text("Send ĐOGE") },
        text = {
            Column {
                OutlinedTextField(value = toAddress, onValueChange = { toAddress = it }, label = { Text("Recipient Shibes Address") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("Amount (whole ĐOGE)") }, singleLine = true)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Note: Such network fees So apply", style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

/**
 * Receive dialog
 */
@Composable
private fun WalletReceiveDialog(
    currentAddress: String,
    onRequestAddress: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row {
                TextButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Much Receive ĐOGE") },
        text = {
            Column {
                Text(text = "Address:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(text = currentAddress)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Show this QR code to the sender", style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

/**
 * Private Key dialog (masked by default + import hint)
 */
@Composable
private fun PrivateKeyDialog(
    brandAccent: Color,
    privateKeyWif: String?,
    spvEnabled: Boolean,
    onImportClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val wif = privateKeyWif ?: "Not available"
    var reveal by remember { mutableStateOf(false) }

    val displayText = when {
        wif == "Not available" -> wif
        reveal -> wif
        else -> "************"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row {
                TextButton(onClick = onImportClick) { Text("Import/Restore") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(wif))
                    Toast.makeText(context, "Copied Private Key (WIF)", Toast.LENGTH_SHORT).show()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Private Key (WIF)", color = brandAccent) },
        text = {
            Column {
                Text(
                    text = "PLEASE Keep this key VERY safe. Anyone with it can spend your ĐOGE.",
                    color = brandAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Masked key box with reveal toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(text = displayText, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { reveal = !reveal }) {
                        Text(if (reveal) "Hide" else "Reveal")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // SPV import hint
                Text(
                    text = if (spvEnabled)
                        "SPV is ON. You can import now."
                    else
                        "Note: Enable SPV in About Sheet before importing wif (by clicking Đogechat Wallet text in the header -> Scroll down to Wallet (SPV) Section to toggle on for immediate import. If SPV is OFF, the key will be cached and applied when the wallet starts.",
                    color = if (spvEnabled) Color(0xFF4CAF50) else Color(0xFFFFC107),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

/**
 * Private Key import dialog (with SPV hint)
 */
@Composable
private fun PrivateKeyImportDialog(
    brandAccent: Color,
    spvEnabled: Boolean,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var wifText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val trimmed = wifText.trim()
                if (trimmed.isNotEmpty()) onImport(trimmed)
            }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Import/Restore Wallet", color = brandAccent) },
        text = {
            Column {
                Text("Paste your private key (WIF) below.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = wifText,
                    onValueChange = { wifText = it },
                    label = { Text("WIF") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (spvEnabled)
                        "SPV is ON. Import will apply immediately."
                    else
                        "SPV is OFF. Enable SPV in About Sheet (by clicking Đogechat Wallet text in the header-> Scroll down to Wallet (SPV) Section to toggle on for immediate import. Otherwise, the key will be cached and applied when the wallet starts.",
                    color = if (spvEnabled) Color(0xFF4CAF50) else Color(0xFFFFC107),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}

/**
 * Success dialog
 */
@Composable
private fun WalletSuccessDialog(
    data: WalletViewModel.SuccessAnimationData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Much Success") },
        text = {
            Column {
                Text(text = data.message)
                data.txHash?.let { Text(text = "TX: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    )
}

/**
 * Failure dialog
 */
@Composable
private fun WalletFailureDialog(
    data: WalletViewModel.FailureAnimationData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Failure") },
        text = {
            Column {
                Text(text = data.message)
                data.reason?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    )
}

/**
 * Transaction history list
 */
@Composable
private fun WalletTransactionHistory(
    rows: List<WalletManager.TxRow>,
    brandAccent: Color,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No transactions yet", color = brandAccent)
        }
        return
    }

    LazyColumn(modifier = modifier) {
        items(rows) { row ->
            WalletTransactionRow(row)
            Divider()
        }
    }
}

@Composable
private fun WalletTransactionRow(row: WalletManager.TxRow) {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.value, style = MaterialTheme.typography.bodyLarge)
            Text(text = if (row.isIncoming) "Received" else "Sent", style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = row.confirmations.toString() + " conf", style = MaterialTheme.typography.bodySmall)
            Text(text = row.time?.let { fmt.format(it) } ?: "", style = MaterialTheme.typography.bodySmall)
        }
    }
}