package com.dogechat.android.ui

import android.content.Intent
import android.net.Uri
import android.text.util.Linkify
import android.util.Patterns
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState // <-- IMPORT
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import com.dogechat.android.model.DogechatMessage
import com.dogechat.android.parsing.ParsedDogeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun MessagesList(
    messages: List<com.dogechat.android.model.DogechatMessage>,
    currentUserNickname: String,
    meshService: com.dogechat.android.mesh.BluetoothMeshService? = null,
    modifier: Modifier = Modifier,
    forceScrollToBottom: Boolean = false,
    onScrolledUpChanged: (Boolean) -> Unit = {},
    onNicknameClick: (String) -> Unit = {},
    onMessageLongPress: (com.dogechat.android.model.DogechatMessage) -> Unit = {},
    onDogeReceive: (com.dogechat.android.parsing.ParsedDogeToken) -> Unit = {},
    onDogeSend: (com.dogechat.android.parsing.ParsedDogeToken) -> Unit = {}
) {
    val listState = rememberLazyListState() // <- now resolved
    val coroutine = rememberCoroutineScope()
    val context = LocalContext.current

    // Scroll when toggled or new messages
    LaunchedEffect(forceScrollToBottom, messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // notify when user scrolls up
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val isScrolledUp = !(listState.firstVisibleItemIndex == messages.lastIndex && listState.firstVisibleItemScrollOffset == 0)
        onScrolledUpChanged(isScrolledUp)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 10.dp),
        reverseLayout = false
    ) {
        itemsIndexed(messages, key = { _, item -> item.hashCode() }) { _, msg ->
            val isMine = msg.sender == currentUserNickname
            MessageRow(
                message = msg,
                isMine = isMine,
                onNicknameClick = onNicknameClick,
                onLongPress = { onMessageLongPress(msg) },
                onOpenLink = { url ->
                    try {
                        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(i)
                    } catch (_: Exception) { }
                },
                onDogeReceive = onDogeReceive,
                onDogeSend = onDogeSend
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/** MessageRow: keep timestamp handling robust to String/Long */
@Composable
private fun MessageRow(
    message: com.dogechat.android.model.DogechatMessage,
    isMine: Boolean,
    onNicknameClick: (String) -> Unit,
    onLongPress: (Offset) -> Unit,
    onOpenLink: (String) -> Unit,
    onDogeReceive: (ParsedDogeToken) -> Unit,
    onDogeSend: (ParsedDogeToken) -> Unit
) {
    val tsText: String = run {
        val tsMillis: Long = when (val t = message.timestamp) {
            is Long -> t
            is Int -> t.toLong()
            is String -> t.toLongOrNull() ?: 0L
            else -> 0L
        }
        try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(tsMillis))
        } catch (e: Exception) {
            ""
        }
    }

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        // ... avatar, header, bubble as in earlier example ...
        // keep your existing UI or use the previously provided MessageRow implementation,
        // which uses tsText for the time string.
    }
}
