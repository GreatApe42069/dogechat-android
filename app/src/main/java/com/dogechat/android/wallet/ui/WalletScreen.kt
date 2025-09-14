package com.dogechat.android.wallet.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.dogechat.android.wallet.WalletManager.Companion.instanceRef
import com.dogechat.android.wallet.viewmodel.UIStateManager
import com.dogechat.android.wallet.viewmodel.WalletViewModel
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

    // Dogecoin accent color
    val dogeGold = Color(0xFFFFB300)

    // State flows from viewmodel
    val balance by viewModel.balance.collectAsState(initial = "0 DOGE")
    val address by viewModel.address.collectAsState(initial = null)
    val syncPercent by viewModel.syncPercent.collectAsState(initial = 0)
    val history by viewModel.history.collectAsState(initial = emptyList())

    // Dialog / animation state handled by UIStateManager (LiveData)
    val showSendDialog by uiStateManager.showSendDialog.observeAsState(false)
    val showReceiveDialog by uiStateManager.showReceiveDialog.observeAsState(false)
    val showSuccess by uiStateManager.showSuccessAnimation.observeAsState(false)
    val successData by uiStateManager.successAnimationData.observeAsState()
    val showFailure by uiStateManager.showFailureAnimation.observeAsState(false)
    val failureData by uiStateManager.failureAnimationData.observeAsState()
    val isLoading by uiStateManager.isLoading.observeAsState(false)
    val errorMessage by uiStateManager.errorMessage.observeAsState()

    // Private key dialog state
    var showPrivKeyDialog by remember { mutableStateOf(false) }
    var privateKeyWif by remember { mutableStateOf<String?>(null) }

    // start wallet when screen appears
    LaunchedEffect(Unit) {
        viewModel.startWallet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đogecoin Wallet") },
                actions = {
                    // Replace header refresh with KEY icon
                    IconButton(onClick = {
                        privateKeyWif = instanceRef?.exportCurrentReceivePrivateKeyWif()
                        showPrivKeyDialog = true
                    }) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Export Private Key", tint = dogeGold)
                    }
                }
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
                    dogeGold = dogeGold,
                    balance = balance,
                    shortAddress = address?.let { shortenAddress(it) } ?: "—",
                    fullAddress = address ?: "Not ready",
                    syncPercent = syncPercent,
                    onSendClick = { uiStateManager.showSendDialog() },
                    onReceiveClick = { uiStateManager.showReceiveDialog() },
                    onRefreshClick = { viewModel.startWallet() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Much Transactions", style = MaterialTheme.typography.titleMedium)
                Divider(modifier = Modifier.padding(vertical = 6.dp))

                // Renamed to avoid collisions with another TransactionHistory in the project
                WalletTransactionHistory(
                    rows = history,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            // Send Dialog
            if (showSendDialog) {
                SendDialog(
                    defaultAddress = address ?: "",
                    isLoading = isLoading,
                    onDismiss = { uiStateManager.hideSendDialog(); uiStateManager.clearError() },
                    onSend = { toAddr, amount ->
                        uiStateManager.setLoading(true)
                        viewModel.sendCoins(toAddr, amount) { ok, msg ->
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

            // Receive Dialog
            if (showReceiveDialog) {
                ReceiveDialog(
                    address = address ?: "Not ready",
                    onDismiss = { uiStateManager.hideReceiveDialog() }
                )
            }

            // Success Dialog (renamed to avoid clashes)
            if (showSuccess) {
                WalletSuccessDialog(
                    data = successData ?: WalletViewModel.SuccessAnimationData("Success"),
                    onDismiss = {
                        uiStateManager.hideSuccessAnimation()
                    }
                )
            }

            // Failure Dialog (renamed to avoid clashes)
            if (showFailure) {
                WalletFailureDialog(
                    data = failureData ?: WalletViewModel.FailureAnimationData("Failure"),
                    onDismiss = {
                        uiStateManager.hideFailureAnimation()
                    }
                )
            }

            // Error message toast
            errorMessage?.let { err ->
                LaunchedEffect(err) {
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                    uiStateManager.clearError()
                }
            }

            // Private key dialog
            if (showPrivKeyDialog) {
                PrivateKeyDialog(
                    dogeGold = dogeGold,
                    privateKeyWif = privateKeyWif,
                    onDismiss = { showPrivKeyDialog = false }
                )
            }
        }
    )
}

/**
 * Wallet overview card: balance, address snippet, sync, quick actions.
 * Tweaked layout to separate address from buttons (more polished).
 */
@Composable
private fun WalletOverview(
    dogeGold: Color,
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
            // Top row: balance + refresh
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Such Balance", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Text(text = balance, style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = onRefreshClick) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = dogeGold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Address chip - its own row
            Surface(
                color = Color.Transparent,
                border = BorderStroke(1.dp, dogeGold),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Address: $shortAddress",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions row: Receive and Send
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(
                    onClick = onReceiveClick,
                    border = BorderStroke(1.dp, dogeGold),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = dogeGold),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = "Receive", tint = dogeGold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Much Receive")
                }

                Button(
                    onClick = onSendClick,
                    colors = ButtonDefaults.buttonColors(containerColor = dogeGold, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Very Send")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sync indicator
            LinearProgressIndicator(
                progress = (syncPercent.coerceIn(0, 100) / 100f),
                modifier = Modifier.fillMaxWidth(),
                color = dogeGold
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
 * Send dialog: simple address + amount (whole DOGE)
 */
@Composable
private fun SendDialog(
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
 * Receive dialog: shows address (and offers copy)
 */
@Composable
private fun ReceiveDialog(
    address: String,
    onDismiss: () -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(address))
                Toast.makeText(context, "Copied Shibes Address", Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
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
                    Text(text = address)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Show this QR code to the sender", style = MaterialTheme.typography.bodySmall)
            }
        }
    )
}

/**
 * Private Key dialog (WIF export) with copy
 */
@Composable
private fun PrivateKeyDialog(
    dogeGold: Color,
    privateKeyWif: String?,
    onDismiss: () -> Unit
) {
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val wif = privateKeyWif ?: "Not available"

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(wif))
                Toast.makeText(context, "Copied Private Key (WIF)", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Export Private Key (WIF)") },
        text = {
            Column {
                Text(
                    text = "Warning: Anyone with this key can spend your DOGE. Store it securely.",
                    color = dogeGold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(text = wif)
                }
            }
        }
    )
}

/**
 * Success dialog (renamed to avoid name clash with any other SuccessAnimation in project)
 */
@Composable
private fun WalletSuccessDialog(
    data: WalletViewModel.SuccessAnimationData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Success") },
        text = {
            Column {
                Text(text = data.message)
                data.txHash?.let { Text(text = "TX: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    )
}

/**
 * Failure dialog (renamed to avoid name clash)
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
 * Transaction history list (renamed to avoid collisions)
 */
@Composable
private fun WalletTransactionHistory(
    rows: List<WalletManager.TxRow>,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No transactions yet")
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

/** Utility to shorten long addresses for display */
private fun shortenAddress(addr: String, prefix: Int = 6, suffix: Int = 6): String {
    if (addr.length <= prefix + suffix + 3) return addr
    return addr.take(prefix) + "…" + addr.takeLast(suffix)
}