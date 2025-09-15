package com.dogechat.android.wallet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dogechat.android.wallet.logging.SpvLogBuffer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpvLogsSheet(
    isPresented: Boolean,
    onDismiss: () -> Unit
) {
    if (!isPresented) return
    val logs by SpvLogBuffer.lines.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SPV Logs", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { SpvLogBuffer.clear() }) { Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))
            if (logs.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("No logs yet")
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 480.dp)) {
                    items(logs) { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall)
                        Divider()
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}