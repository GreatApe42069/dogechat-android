package com.dogechat.android.ui.media

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dogechat.android.R
import com.dogechat.android.features.file.FileUtils
import com.dogechat.android.model.DogechatFilePacket
import java.io.File

/**
 * Dialog for handling received file messages in modern chat style
 */
@Composable
fun FileViewerDialog(
    packet: DogechatFilePacket,
    onDismiss: () -> Unit,
    onOpenFile: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // File received header
                Text(
                    text = stringResource(R.string.file_viewer_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // File info
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = stringResource(R.string.file_viewer_name, packet.fileName),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.file_viewer_size, FileUtils.formatFileSize(packet.fileSize)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.file_viewer_type, packet.mimeType),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Open/Save button with smart logic
                    Button(
                        onClick = {
                            if (FileUtils.isFileViewable(packet.fileName)) {
                                onOpenFile()
                            } else {
                                saveFileToDownloads(context, packet)
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(stringResource(R.string.file_viewer_open_save))
                    }

                    // Dismiss button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text(stringResource(R.string.close_with_emoji))
                    }
                }
            }
        }
    }
}

/**
 * Saves a file packet's content to the device's public "Downloads" directory.
 */
private fun saveFileToDownloads(context: Context, packet: DogechatFilePacket) {
    runCatching {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, packet.fileName)
            put(MediaStore.Downloads.MIME_TYPE, packet.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(packet.content)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            Toast.makeText(context, context.getString(R.string.toast_file_saved, packet.fileName), Toast.LENGTH_SHORT).show()
        } else {
            throw Exception("Content resolver returned a null URI")
        }
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.toast_failed_to_save_file), Toast.LENGTH_SHORT).show()
        android.util.Log.e("FileViewerDialog", "Failed to save file to downloads", it)
    }
}


/**
 * Attempts to open a file using system viewers.
 * It saves the file to a temporary location and uses a FileProvider to grant access.
 */
fun tryOpenFile(context: Context, packet: DogechatFilePacket) {
    try {
        // Save the file to a temporary file in the cache directory
        val tempFile = File.createTempFile("dogechat_share_", ".${FileUtils.getExtension(packet.fileName)}", context.cacheDir).apply {
            writeBytes(packet.content)
            deleteOnExit() // Ensure the file is cleaned up when the app closes
        }

        // Get a content URI for the temp file using the FileProvider
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )

        // Create an Intent to view the file
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, packet.mimeType)
            // Grant read permission to the app that handles the intent
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // No app can handle this file type, fallback to saving it
            android.util.Log.w("FileViewerDialog", "No activity found to handle file type: ${packet.mimeType}. Saving instead.")
            saveFileToDownloads(context, packet)
        }
    } catch (e: Exception) {
        // Handle any other errors gracefully
        android.util.Log.e("FileViewerDialog", "Failed to open file", e)
    }
}