package com.dogechat.android.wallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.dogechat.android.wallet.logging.SpvLogBuffer
import com.dogechat.android.wallet.logging.WalletTorLogBuffer
import com.dogechat.android.wallet.logging.UiLogBuffer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpvLogsSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit
) {
    if (!isPresented) return

    val tabs = listOf("SPV", "Wallet Tor", "UI")
    var selectedTab by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("") }

    val spvLogs by SpvLogBuffer.lines.collectAsState()
    val torLogs by WalletTorLogBuffer.lines.collectAsState()
    val uiLogs by UiLogBuffer.lines.collectAsState()
    val clipboard = LocalClipboardManager.current

    fun filtered(lines: List<String>) =
        if (filter.isBlank()) lines else lines.filter { it.contains(filter, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text("Filter") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                when (selectedTab) {
                    0 -> {
                        TextButton(onClick = { SpvLogBuffer.clear() }) { Text("Clear") }
                        TextButton(onClick = {
                            val text = filtered(spvLogs).joinToString("\n")
                            clipboard.setText(AnnotatedString(text))
                        }) { Text("Copy") }
                    }
                    1 -> {
                        TextButton(onClick = { WalletTorLogBuffer.clear() }) { Text("Clear") }
                        TextButton(onClick = {
                            val text = filtered(torLogs).joinToString("\n")
                            clipboard.setText(AnnotatedString(text))
                        }) { Text("Copy") }
                    }
                    2 -> {
                        TextButton(onClick = { UiLogBuffer.clear() }) { Text("Clear") }
                        TextButton(onClick = {
                            val text = filtered(uiLogs).joinToString("\n")
                            clipboard.setText(AnnotatedString(text))
                        }) { Text("Copy") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val lines = when (selectedTab) {
                0 -> filtered(spvLogs)
                1 -> filtered(torLogs)
                else -> filtered(uiLogs)
            }

            if (lines.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(140.dp)) {
                    Text("No logs yet", modifier = Modifier.padding(8.dp))
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 520.dp)
                ) {
                    items(lines) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                        Divider()
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}