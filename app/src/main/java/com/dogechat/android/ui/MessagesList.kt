package com.dogechat.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.lazy.rememberLazyListState
import com.dogechat.android.model.DogechatMessage
import com.dogechat.android.mesh.BluetoothMeshService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Single authoritative MessagesList composable.
 *
 * Uses MessageParser.instance to find Doge payment tokens inside the message text
 * and calls onDogeReceive/onDogeSend with ParsedDogeToken.
 *
 * IMPORTANT: Remove any other MessagesList function declarations from your codebase to avoid overload ambiguity.
 */
@Composable
fun MessagesList(
    messages: List<DogechatMessage>,
    currentUserNickname: String,
    meshService: BluetoothMeshService? = null,
    modifier: Modifier = Modifier,
    forceScrollToBottom: Boolean = false,
    onScrolledUpChanged: (Boolean) -> Unit = {},
    onNicknameClick: (String) -> Unit = {},
    onMessageLongPress: (DogechatMessage) -> Unit = {},
    onDogeReceive: (com.dogechat.android.parsing.ParsedDogeToken) -> Unit = {},
    onDogeSend: (com.dogechat.android.parsing.ParsedDogeToken) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // compute "scrolled up" state
    val isScrolledUp by remember {
        derivedStateOf {
            if (messages.isEmpty()) return@derivedStateOf false
            val lastIndex = messages.lastIndex
            listState.firstVisibleItemIndex < lastIndex
        }
    }

    LaunchedEffect(isScrolledUp) {
        onScrolledUpChanged(isScrolledUp)
    }

    // scroll to bottom when requested
    LaunchedEffect(forceScrollToBottom, messages.size) {
        if (messages.isNotEmpty() && forceScrollToBottom) {
            try {
                listState.animateScrollToItem(messages.lastIndex)
            } catch (_: Exception) {
            }
        }
    }

    val urlPattern = remember { Pattern.compile("(https?://\\S+)", Pattern.CASE_INSENSITIVE) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val messageParser = com.dogechat.android.parsing.MessageParser.instance

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(messages, key = { index, m -> m.id ?: index }) { _, msg ->
            // Basic row with avatar + content
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .combinedClickable(
                        onClick = { /* no-op (message internal controls handle clicks) */ },
                        onLongClick = { onMessageLongPress(msg) }
                    ),
                verticalAlignment = Alignment.Top
            ) {
                // Avatar (first char of sender)
                val senderName = msg.sender
                val avatarChar = senderName.trim().firstOrNull()?.uppercaseChar() ?: '?'
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (senderName == currentUserNickname) Color.DarkGray else Color(0xFF1E88E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = avatarChar.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Header: name + timestamp
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = senderName,
                            modifier = Modifier
                                .clickable { onNicknameClick(senderName) }
                                .weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val tsLong: Long = when (val t = msg.timestamp) {
                            is Long -> t
                            is Int -> t.toLong()
                            is Number -> t.toLong()
                            else -> System.currentTimeMillis()
                        }
                        val tsText = try {
                            timeFmt.format(Date(tsLong))
                        } catch (_: Exception) {
                            ""
                        }
                        Text(text = tsText, style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, color = Color.LightGray)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Body: detect URLs and show clickable text
                    val body = msg.text ?: ""
                    val matcher = urlPattern.matcher(body)
                    if (!matcher.find()) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace
                        )
                    } else {
                        // build annotated string with URL tags
                        val builder = AnnotatedString.Builder()
                        var last = 0
                        val m = urlPattern.matcher(body)
                        while (m.find()) {
                            val s = m.start()
                            val e = m.end()
                            if (s > last) builder.append(body.substring(last, s))
                            val url = body.substring(s, e)
                            builder.pushStringAnnotation(tag = "URL", annotation = url)
                            builder.withStyle(SpanStyle(color = Color(0xFF64B5F6), textDecoration = TextDecoration.Underline)) {
                                append(url)
                            }
                            builder.pop()
                            last = e
                        }
                        if (last < body.length) builder.append(body.substring(last))

                        ClickableText(
                            text = builder.toAnnotatedString(),
                            style = MaterialTheme.typography.bodyLarge,
                            onClick = { offset ->
                                val annotation = builder.toAnnotatedString().getStringAnnotations(tag = "URL", start = offset, end = offset)
                                if (annotation.isNotEmpty()) {
                                    val url = annotation.first().item
                                    try {
                                        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(i)
                                    } catch (_: Exception) { }
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Token actions: parse message content to find a Doge payment token (MessageParser produces MessageElement.DogePayment)
                    val elements = try {
                        messageParser.parseMessage(body)
                    } catch (_: Exception) {
                        emptyList<com.dogechat.android.parsing.MessageElement>()
                    }

                    val firstToken = elements.firstOrNull { it is com.dogechat.android.parsing.MessageElement.DogePayment } as? com.dogechat.android.parsing.MessageElement.DogePayment
                    val parsed = firstToken?.token

                    if (parsed != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onDogeReceive(parsed) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Receive", color = Color.Black, fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { onDogeSend(parsed) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Send", fontSize = 12.sp)
                            }
                        }
                    } else {
                        // no token — nothing to show
                    }
                }
            }
        }
    }
}
