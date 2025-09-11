package com.dogechat.android.wallet.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dogechat.android.wallet.viewmodel.UIStateManager
import com.dogechat.android.wallet.viewmodel.WalletViewModel
import kotlinx.coroutines.launch
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
    // In viewModel.startWallet() you can check these and prefill send/receive dialogs
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

    // start wallet when screen appears
    LaunchedEffect(Unit) {
        viewModel.startWallet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đogecoin Wallet") },
                actions = {
                    IconButton(onClick = {
                        viewModel.startWallet()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                    balance = balance,
                    shortAddress = address?.let { shortenAddress(it) } ?: "—",
                    syncPercent = syncPercent,
                    onSendClick = { uiStateManager.showSendDialog() },
                    onReceiveClick = { uiStateManager.showReceiveDialog() },
                    onRefreshClick = { viewModel.startWallet() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Much Transactions", style = MaterialTheme.typography.titleMedium)
                Divider(modifier = Modifier.padding(vertical = 6.dp))

                TransactionHistory(
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

            // Success Animation/Dialog
            if (showSuccess) {
                SuccessAnimation(
                    data = successData ?: WalletViewModel.SuccessAnimationData("Success"),
                    onDismiss = {
                        uiStateManager.hideSuccessAnimation()
                    }
                )
            }

            // Failure Animation/Dialog
            if (showFailure) {
                FailureDialog(
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
        }
    )
}

/**
 * Wallet overview card: balance, address snippet, sync, quick actions.
 */
@Composable
private fun WalletOverview(
    balance: String,
    shortAddress: String,
    syncPercent: Int,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Such Balance", style = MaterialTheme.typography.bodySmall)
                    Text(text = balance, style = MaterialTheme.typography.titleLarge)
                }
                Column(horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onRefreshClick) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Address: $shortAddress", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Button(onClick = onReceiveClick) {
                    Icon(Icons.Default.QrCode, contentDescription = "Receive")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Much Receive")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSendClick) {
                    Text("Very Send")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // sync indicator
            LinearProgressIndicator(progress = (syncPercent.coerceIn(0, 100) / 100f), modifier = Modifier.fillMaxWidth())
            Text(text = "Sync: $syncPercent%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.End))
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
                Box(modifier = Modifier
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
 * Transaction history list
 */
@Composable
private fun TransactionHistory(rows: List<com.dogechat.android.wallet.WalletManager.TxRow>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No transactions yet")
        }
        return
    }

    LazyColumn(modifier = modifier) {
        items(rows) { row ->
            TransactionRow(row)
            Divider()
        }
    }
}

@Composable
private fun TransactionRow(row: com.dogechat.android.wallet.WalletManager.TxRow) {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
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

/**
 * Simple success animation/dialog placeholder
 */
@Composable
private fun SuccessAnimation(data: WalletViewModel.SuccessAnimationData, onDismiss: () -> Unit) {
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
 * Simple failure dialog
 */
@Composable
private fun FailureDialog(data: WalletViewModel.FailureAnimationData, onDismiss: () -> Unit) {
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

/** Utility to shorten long addresses for display */
private fun shortenAddress(addr: String, prefix: Int = 6, suffix: Int = 6): String {
    if (addr.length <= prefix + suffix + 3) return addr
    return addr.take(prefix) + "…" + addr.takeLast(suffix)
}
