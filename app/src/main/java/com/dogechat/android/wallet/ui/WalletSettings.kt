package com.dogechat.android.wallet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dogechat.android.wallet.viewmodel.WalletViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletSettings(
    viewModel: WalletViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // collect simple pieces of state from the ViewModel
    val address by viewModel.address.collectAsState()
    val history by viewModel.history.collectAsState()
    val syncPercent by viewModel.syncPercent.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Wallet Info Section
        item {
            SettingsSection(title = "Wallet Information") {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        InfoRow("Wallet Version", "0.1.0-dogechat")
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Sync Progress", "$syncPercent%")
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Receive Address", address ?: "Not generated")
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Total Transactions", "${history.size}")
                    }
                }
            }
        }

        // Security Section
        item {
            SettingsSection(title = "Security") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsItem(
                        icon = Icons.Filled.FileDownload,
                        title = "Export Wallet Data",
                        description = "Export transaction history to JSON",
                        onClick = { showExportDialog = true }
                    )

                    SettingsItem(
                        icon = Icons.Filled.Key,
                        title = "View Seed Phrase",
                        description = "Display wallet recovery information (if available)",
                        onClick = { /* TODO: implement seed display */ }
                    )

                    SettingsItem(
                        icon = Icons.Filled.Warning,
                        title = "Clear Wallet Data",
                        description = "Remove local wallet files (irreversible)",
                        onClick = { showClearDataDialog = true },
                        textColor = Color(0xFFFF5722)
                    )
                }
            }
        }

        // Network Section
        item {
            SettingsSection(title = "Network") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsItem(
                        icon = Icons.Filled.Public,
                        title = "Start Node",
                        description = "Start the Dogecoin SPV node",
                        onClick = { viewModel.startWallet() }
                    )

                    SettingsItem(
                        icon = Icons.Filled.Pause,
                        title = "Stop Node",
                        description = "Stop the Dogecoin SPV node",
                        onClick = { viewModel.stopWallet() }
                    )

                    SettingsItem(
                        icon = Icons.Filled.Refresh,
                        title = "Refresh Address",
                        description = "Generate or refresh receive address",
                        onClick = { viewModel.refreshAddress() }
                    )
                }
            }
        }

        // About Section
        item {
            SettingsSection(title = "About") {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Dogechat Wallet",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lightweight Dogecoin SPV wallet integrated with Dogechat.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Built with bitcoinj/libdohj for Dogecoin network support.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }

    // Clear Data Confirmation Dialog (no-op placeholder for now)
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text(text = "Clear Wallet Data?", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This will delete local wallet files and history.\nThis action cannot be undone.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // TODO: implement actual clear; currently a no-op placeholder
                    showClearDataDialog = false
                }) {
                    Text(text = "Clear Data", fontFamily = FontFamily.Monospace, color = Color(0xFFFF5722), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text(text = "Cancel", fontFamily = FontFamily.Monospace) }
            }
        )
    }

    // Export Data Dialog (no-op placeholder)
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(text = "Export Wallet Data", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "This will export transaction history to a JSON file in the app storage. Private keys are NOT exported.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    // TODO: implement export; currently closes dialog
                    showExportDialog = false
                }) {
                    Text(text = "Export", fontFamily = FontFamily.Monospace, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(text = "Cancel", fontFamily = FontFamily.Monospace) }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column { 
        Text(
            text = title,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00C851),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        content()
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A))
    ) { content() }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    textColor: Color = Color.White
) {
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor)
                Text(text = description, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = textColor.copy(alpha = 0.7f))
            }
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = "$label:", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        Text(text = value, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}
