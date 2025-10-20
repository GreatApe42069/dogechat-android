package com.dogechat.android.ui.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dogechat.android.R
import com.dogechat.android.mesh.BluetoothMeshService
import com.dogechat.android.model.DeliveryStatus
import com.dogechat.android.model.DogechatMessage
import com.dogechat.android.ui.formatMessageHeaderAnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat

/**
 * Composable for displaying a video message in the chat list.
 * It shows a thumbnail of the video with a play icon overlay.
 */
@Composable
fun VideoMessageItem(
    message: DogechatMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat,
    onNicknameClick: ((String) -> Unit)?,
    onMessageLongPress: ((DogechatMessage) -> Unit)?,
    onCancelTransfer: ((DogechatMessage) -> Unit)?,
    onVideoClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val path = message.content.trim()
    val file = remember(path) { File(path) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Message Header (sender, time, etc.)
        val headerText = formatMessageHeaderAnnotatedString(
            message = message,
            currentUserNickname = currentUserNickname,
            meshService = meshService,
            colorScheme = colorScheme,
            timeFormatter = timeFormatter
        )
        val haptic = LocalHapticFeedback.current
        var headerLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = headerText,
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurface,
            modifier = Modifier.pointerInput(message.id) {
                detectTapGestures(onTap = { pos ->
                    headerLayout?.let { layout ->
                        val offset = layout.getOffsetForPosition(pos)
                        headerText.getStringAnnotations("nickname_click", offset, offset)
                            .firstOrNull()?.let { ann ->
                                onNicknameClick?.invoke(ann.item)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                    }
                }, onLongPress = { onMessageLongPress?.invoke(message) })
            },
            onTextLayout = { headerLayout = it }
        )

        // Video Thumbnail
        var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(file.path) {
            if (file.exists()) {
                withContext(Dispatchers.IO) {
                    try {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(file.path)
                        thumbnail = retriever.getFrameAtTime(1000000) // Get frame at 1 second
                        retriever.release()
                    } catch (e: Exception) {
                        // Could not generate thumbnail
                    }
                }
            }
        }

        val progressFraction: Float? = when (val st = message.deliveryStatus) {
            is DeliveryStatus.PartiallyDelivered -> if (st.total > 0) st.reached.toFloat() / st.total.toFloat() else 0f
            else -> null
        }

        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .aspectRatio(16 / 9f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(Color.Black)
                .clickable(enabled = thumbnail != null) { onVideoClick?.invoke(path) },
            contentAlignment = Alignment.Center
        ) {
            thumbnail?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_video_thumbnail),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Play icon overlay
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play_video),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Cancel button overlay during sending
            if (message.sender == currentUserNickname && message.deliveryStatus is DeliveryStatus.PartiallyDelivered) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(Color.Gray.copy(alpha = 0.6f), CircleShape)
                        .clickable { onCancelTransfer?.invoke(message) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.cd_cancel), tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}