package com.dogechat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveDialog(
    currentAddress: String?,
    onRequestAddress: (amount: String?) -> Unit,
    onCopy: (address: String) -> Unit,
    onShare: (address: String) -> Unit,
    onNavigateBack: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
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
                    text = "RECEIVE",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(44.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Address / QR area
            if (!currentAddress.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "QR",
                            modifier = Modifier
                                .size(160.dp)
                                .padding(8.dp),
                            tint = Color(0xFF00C851)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = currentAddress,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            TextButton(onClick = {
                                clipboardManager.setText(AnnotatedString(currentAddress))
                                onCopy(currentAddress)
                            }) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Copy")
                            }

                            TextButton(onClick = { onShare(currentAddress) }) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Share")
                            }

                            TextButton(onClick = { onRequestAddress(null) }, enabled = !isLoading) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "New")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "New Address")
                            }
                        }
                    }
                }
            } else {
                // No address yet: allow user to request/generate one (optionally with amount)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(text = "Generate a receiving address", color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = amount,
                            onValueChange = { new -> if (new.isEmpty() || new.toBigDecimalOrNull() != null) amount = new },
                            label = { Text("Amount (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color(0xFF00C851),
                                unfocusedIndicatorColor = Color(0xFF2A2A2A),
                                cursorColor = Color(0xFF00C851),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onRequestAddress(if (amount.isBlank()) null else amount.trim()) },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C851))
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = "Generate")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Generate Address")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Share the QR or address with the sender. Receiving transactions are on-chain and irreversible.",
                color = Color.LightGray
            )
        }
    }
}