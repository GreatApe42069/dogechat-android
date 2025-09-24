package com.dogechat.android.ui

import android.util.Log
import com.dogechat.android.mesh.BluetoothMeshService
import com.dogechat.android.model.DogechatMessage
import com.dogechat.android.model.MessageType
import java.io.File
import java.util.Date
import java.security.MessageDigest

/**
 * Handles media file sending operations (voice notes, images, generic files)
 * Updated to integrate with BluetoothMeshService.sendFile (chunked via FileSharingManager).
 * Progress is reported via BluetoothMeshDelegate callbacks.
 */
class MediaSendingManager(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val channelManager: ChannelManager,
    private val meshService: BluetoothMeshService
) {
    companion object {
        private const val TAG = "MediaSendingManager"
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB local guard
    }

    // Track in-flight transfer: fileId (from meshService) -> messageId
    private val transferMessageMap = mutableMapOf<String, String>()
    private val messageTransferMap = mutableMapOf<String, String>()

    fun sendVoiceNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
        sendFileInternal(toPeerIDOrNull, channelOrNull, filePath, defaultMime = "audio/m4a", kind = MessageType.AUDIO)
    }

    fun sendImageNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
        sendFileInternal(toPeerIDOrNull, channelOrNull, filePath, defaultMime = "image/jpeg", kind = MessageType.IMAGE)
    }

    fun sendFileNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
        // mime guessed later
        sendFileInternal(toPeerIDOrNull, channelOrNull, filePath, defaultMime = "application/octet-stream", kind = null)
    }

    private fun sendFileInternal(
        toPeerIDOrNull: String?,
        channelOrNull: String?,
        filePath: String,
        defaultMime: String,
        kind: MessageType?
    ) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "❌ File does not exist: $filePath")
                return
            }
            if (file.length() > MAX_FILE_SIZE) {
                Log.e(TAG, "❌ File too large: ${file.length()} bytes (max: $MAX_FILE_SIZE)")
                return
            }

            val mimeType = runCatching {
                com.dogechat.android.features.file.FileUtils.getMimeTypeFromExtension(file.name)
            }.getOrElse { defaultMime }

            val messageType = kind ?: when {
                mimeType.lowercase().startsWith("image/") -> MessageType.IMAGE
                mimeType.lowercase().startsWith("audio/") -> MessageType.AUDIO
                mimeType.lowercase().startsWith("video/") -> MessageType.VIDEO
                else -> MessageType.FILE
            }

            // Copy file to temp directory so MediaMessageRow can find it for sender's view
            val tempDir = com.dogechat.android.features.file.FileUtils.getTempFilesDir(meshService.getContext())
            val fileName = file.name
            val tempFile = File(tempDir, fileName)
            
            try {
                if (!tempFile.exists()) {
                    file.copyTo(tempFile, overwrite = false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to copy file to temp directory: ${e.message}")
                // Continue anyway, MediaMessageRow will handle missing file
            }

            val fileId = if (toPeerIDOrNull != null) {
                meshService.sendFile(file, recipientPeerID = toPeerIDOrNull, channel = null)
            } else {
                meshService.sendFile(file, recipientPeerID = null, channel = channelOrNull)
            }

            if (fileId == null) {
                Log.e(TAG, "❌ Failed to start file send (meshService returned null fileId)")
                return
            }

            val previewText = when (messageType) {
                MessageType.IMAGE -> "📷 sent an image"
                MessageType.AUDIO -> "🎤 sent a voice message"
                MessageType.VIDEO -> "🎬 sent a video"
                MessageType.FILE -> "📎 sent a file"
                MessageType.TEXT -> "sent a message"
            }

            val msg = DogechatMessage(
                sender = state.getNicknameValue() ?: meshService.myPeerID,
                content = previewText,
                timestamp = Date(),
                isRelay = false,
                isPrivate = toPeerIDOrNull != null,
                recipientNickname = toPeerIDOrNull?.let { meshService.getPeerNicknames()[it] },
                senderPeerID = meshService.myPeerID,
                channel = channelOrNull,
                messageType = messageType,
                mediaFileName = fileName,            // Use just filename, not full path
                mediaMimeType = mimeType,
                mediaFileSize = file.length(),
                mediaFileId = fileId
            )

            if (toPeerIDOrNull != null) {
                messageManager.addPrivateMessage(toPeerIDOrNull, msg)
            } else if (!channelOrNull.isNullOrBlank()) {
                channelManager.addChannelMessage(channelOrNull, msg, meshService.myPeerID)
            } else {
                messageManager.addMessage(msg)
            }

            // seed progress
            messageManager.updateMessageDeliveryStatus(
                msg.id,
                com.dogechat.android.model.DeliveryStatus.PartiallyDelivered(0, 100)
            )

            synchronized(transferMessageMap) {
                transferMessageMap[fileId] = msg.id
                messageTransferMap[msg.id] = fileId
            }

            Log.d(TAG, "📤 Started ${messageType.name.lowercase()} send (id=$fileId) to " +
                    (toPeerIDOrNull ?: channelOrNull ?: "mesh"))

        } catch (e: Exception) {
            Log.e(TAG, "❌ Media send failed: ${e.message}", e)
        }
    }

    fun cancelMediaSend(messageId: String) {
        val transferId = synchronized(transferMessageMap) { messageTransferMap[messageId] }
        if (transferId != null) {
            runCatching { meshService.cancelFileTransfer(transferId) }
            synchronized(transferMessageMap) {
                transferMessageMap.remove(transferId)
                messageTransferMap.remove(messageId)
            }
        }
    }

    fun updateProgressByFileId(fileId: String, sentPercent: Int) {
        val msgId = synchronized(transferMessageMap) { transferMessageMap[fileId] } ?: return
        messageManager.updateMessageDeliveryStatus(
            msgId,
            com.dogechat.android.model.DeliveryStatus.PartiallyDelivered(sentPercent, 100)
        )
        if (sentPercent >= 100) {
            messageManager.updateMessageDeliveryStatus(
                msgId,
                com.dogechat.android.model.DeliveryStatus.Delivered(to = "mesh", at = Date())
            )
            synchronized(transferMessageMap) {
                val removedMsgId = transferMessageMap.remove(fileId)
                if (removedMsgId != null) messageTransferMap.remove(removedMsgId)
            }
        }
    }

    @Suppress("unused")
    private fun sha256Hex(bytes: ByteArray): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        bytes.size.toString(16)
    }
}