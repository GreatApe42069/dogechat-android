package com.dogechat.android.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.dogechat.android.mesh.BluetoothMeshService
import com.dogechat.android.model.DogechatMessage
import com.dogechat.android.model.MessageType
import java.io.File
import java.text.SimpleDateFormat

@Composable
fun MediaMessageRow(
    message: DogechatMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat,
    onMessageLongPress: ((DogechatMessage) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val baseColor = if (message.senderPeerID == meshService.myPeerID ||
        message.sender == currentUserNickname ||
        message.sender.startsWith("$currentUserNickname#")
    ) {
        androidx.compose.ui.graphics.Color(0xFFFFFF00)
    } else {
        getPeerColor(message, isDark = (colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue) < 1.5f)
    }

    Column(modifier = modifier) {
        Text(
            text = buildString {
                val (base, suffix) = splitSuffix(message.sender)
                append("<@"); append(base); append(suffix); append("> ")
            },
            color = baseColor,
            fontFamily = FontFamily.Monospace
        )

        when (message.messageType) {
            MessageType.IMAGE -> ImageRow(message, colorScheme)
            MessageType.AUDIO -> AudioRow(message, colorScheme)
            MessageType.VIDEO -> GenericFileRow(message, colorScheme, header = "Video")
            MessageType.FILE -> GenericFileRow(message, colorScheme, header = "File")
            MessageType.TEXT -> Text(text = message.content, color = baseColor, fontFamily = FontFamily.Monospace)
        }

        Text(
            text = " [${timeFormatter.format(message.timestamp)}]",
            color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.7f),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ImageRow(message: DogechatMessage, colorScheme: ColorScheme) {
    val context = LocalContext.current
    val incomingDir = com.dogechat.android.features.file.FileUtils.getIncomingFilesDir(context)
    val fileName = message.mediaFileName
    val file = remember(fileName) { if (fileName != null) File(incomingDir, fileName) else null }
    val hasFile = file?.exists() == true

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colorScheme.surfaceVariant).padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = if (hasFile) "Image" else "Image (downloading...)", color = colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.weight(1f))
            if (hasFile) {
                IconButton(onClick = {
                    runCatching {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file!!)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, message.mediaMimeType ?: "image/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }.onFailure { Log.w("MediaMessageRow", "Failed to open image: ${it.message}") }
                }) { Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = "Open") }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (hasFile) {
            val bmp = remember(file?.absolutePath) {
                try {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeFile(file!!.absolutePath, opts)
                } catch (_: Exception) { null }
            }
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "image", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)))
            }
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
        }
    }
}

@Composable
private fun AudioRow(message: DogechatMessage, colorScheme: ColorScheme) {
    val context = LocalContext.current
    val incomingDir = com.dogechat.android.features.file.FileUtils.getIncomingFilesDir(context)
    val fileName = message.mediaFileName
    val file = remember(fileName) { if (fileName != null) File(incomingDir, fileName) else null }
    val hasFile = file?.exists() == true

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var durationMs by remember { mutableStateOf(0) }

    val mediaPlayer = remember(file?.absolutePath) {
        if (hasFile) {
            try {
                MediaPlayer().apply {
                    setDataSource(file!!.absolutePath)
                    setOnPreparedListener { durationMs = it.duration }
                    setOnCompletionListener { isPlaying = false; progress = 0f; seekTo(0) }
                    prepareAsync()
                }
            } catch (_: Exception) { null }
        } else null
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying && mediaPlayer != null) {
            try {
                mediaPlayer.start()
                while (mediaPlayer.isPlaying) {
                    val pos = mediaPlayer.currentPosition
                    val dur = mediaPlayer.duration.takeIf { it > 0 } ?: 1
                    progress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    kotlinx.coroutines.delay(100L)
                }
            } catch (_: Exception) { }
        } else if (mediaPlayer != null) {
            runCatching { mediaPlayer.pause() }
        }
    }

    DisposableEffect(Unit) { onDispose { runCatching { mediaPlayer?.release() } } }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colorScheme.surfaceVariant).padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = if (hasFile) "Voice message" else "Voice message (downloading...)", color = colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.weight(1f))
            if (hasFile) {
                IconButton(onClick = { isPlaying = !isPlaying }) {
                    Icon(imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = if (hasFile) progress else 0f,
            modifier = Modifier.fillMaxWidth().height(4.dp)
        )
    }
}

@Composable
private fun GenericFileRow(message: DogechatMessage, colorScheme: ColorScheme, header: String) {
    val context = LocalContext.current
    val incomingDir = com.dogechat.android.features.file.FileUtils.getIncomingFilesDir(context)
    val fileName = message.mediaFileName
    val file = remember(fileName) { if (fileName != null) File(incomingDir, fileName) else null }
    val hasFile = file?.exists() == true

    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(colorScheme.surfaceVariant).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = header, color = colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            Text(text = message.mediaFileName ?: "file", color = colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            if (!message.mediaMimeType.isNullOrEmpty()) {
                Text(text = message.mediaMimeType ?: "", color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontFamily = FontFamily.Monospace)
            }
        }
        ElevatedButton(
            onClick = {
                if (hasFile) {
                    runCatching {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file!!)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, message.mediaMimeType ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }.onFailure { Log.w("MediaMessageRow", "Failed to open file: ${it.message}") }
                }
            },
            enabled = hasFile,
            colors = ButtonDefaults.elevatedButtonColors()
        ) {
            Icon(imageVector = Icons.Filled.OpenInNew, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = if (hasFile) "Open" else "Waiting…")
        }
    }
}