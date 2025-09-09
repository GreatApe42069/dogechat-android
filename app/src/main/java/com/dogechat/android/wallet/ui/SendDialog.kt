package com.dogechat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dogechat - Send Dialog (DOGE only)
 *
 * A simplified, DOGE-only replacement for the original SendView from bitchat.
 * This composable intentionally uses callbacks (onSend) instead of Cashu/Lightning
 * concepts. It is styled to match the WalletScreen/WalletOverview look-and-feel.
 *
 * Parameters:
 *  - balanceText: display-ready balance string (e.g. "12.345 DOGE")
 *  - onNavigateBack: callback when user presses back
 *  - onSend: callback invoked with (address, amountString) when user taps Send
 *  - isLoading: optional flag to show a loading state
 */
@Composable
fun SendDialog(
    balanceText: String,
    onNavigateBack: () -> Unit,
    onSend: (address: String, amount: String) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var address by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }

    // Helper: extract numeric portion of balanceText (e.g. "12.34 DOGE" -> "12.34")
    fun balanceNumeric(): String {
        val regex = Regex("[0-9]+(?:\\.[0-9]+)?")
        return regex.find(balanceText)?.value ?: "0"
    }

    Card(
        modifier = modifier
            .fillMaxSize(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1A1A))
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "SEND",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.weight(1f))

                // placeholder to keep title centered
                Spacer(modifier = Modifier.width(44.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Available balance card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "AVAILABLE",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = balanceText,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    TextButton(onClick = { amount = balanceNumeric() }) {
                        Text(text = "Max", color = Color(0xFF00C851))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Address input
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Recipient Address (DOGE)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                trailingIcon = {
                    IconButton(onClick = { /* TODO: paste from clipboard if desired */ }) {
                        Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFF00C851),
                    unfocusedBorderColor = Color(0xFF2A2A2A),
                    textColor = Color.White,
                    cursorColor = Color(0xFF00C851)
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Amount input + unit
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { new ->
                        // allow only digits and dot
                        if (new.matches(Regex("^\\d*\\\.?\\d*\$"))) amount = new
                    },
                    label = { Text("Amount (DOGE)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = Color(0xFF00C851),
                        unfocusedBorderColor = Color(0xFF2A2A2A),
                        textColor = Color.White,
                        cursorColor = Color(0xFF00C851)
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "DOGE",
                    modifier = Modifier.padding(end = 4.dp),
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Memo (optional)
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("Memo (optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color(0xFF00C851),
                    unfocusedBorderColor = Color(0xFF2A2A2A),
                    textColor = Color.White,
                    cursorColor = Color(0xFF00C851)
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Action buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = {
                        // clear fields and return
                        address = ""
                        amount = ""
                        memo = ""
                        focusManager.clearFocus()
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .weight(0.45f),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Cancel")
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onSend(address.trim(), amount.trim())
                    },
                    enabled = !isLoading && address.isNotBlank() && amount.isNotBlank(),
                    modifier = Modifier
                        .height(48.dp)
                        .weight(0.55f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C851)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Send", color = Color.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Small helper text
            Text(
                text = "Double-check recipient address. Transactions are irreversible.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
    }
}
