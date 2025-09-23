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
 * No direct DogechatFilePacket usage; progress is reported via BluetoothMeshDelegate callbacks.
 */
class MediaSendingManager(
    private val state: ChatState,
    private val messageManager: MessageManager,
    private val channelManager: ChannelManager,
    private val meshService: BluetoothMeshService
) {
    companion object {
        private const val TAG = "MediaSendingManager"
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB limit (local guard; FileUtils has broader limits)
    }

    // Track in-flight transfer: fileId (from meshService) -> messageId
    private val transferMessageMap = mutableMapOf<String, String>()
    private val messageTransferMap = mutableMapOf<String, String>()

    /**
     * Send a voice note (audio file path)
     */
    fun sendVoiceNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
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

            val mimeType = try {
                com.dogechat.android.features.file.FileUtils.getMimeTypeFromExtension(file.name)
            } catch (_: Exception) {
                "audio/m4a"
            }

            if (toPeerIDOrNull != null) {
                sendPrivateFile(toPeerIDOrNull, file, file.name, mimeType, MessageType.AUDIO)
            } else {
                sendPublicFile(channelOrNull, file, file.name, mimeType, MessageType.AUDIO)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send voice note: ${e.message}")
        }
    }

    /**
     * Send an image file from a path
     */
    fun sendImageNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
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

            val mimeType = try {
                com.dogechat.android.features.file.FileUtils.getMimeTypeFromExtension(file.name)
            } catch (_: Exception) {
                "image/jpeg"
            }

            if (toPeerIDOrNull != null) {
                sendPrivateFile(toPeerIDOrNull, file, file.name, mimeType, MessageType.IMAGE)
            } else {
                sendPublicFile(channelOrNull, file, file.name, mimeType, MessageType.IMAGE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Image send failed completely: ${e.message}", e)
        }
    }

    /**
     * Send a generic file (by path)
     */
    fun sendFileNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
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

            val mimeType = try {
                com.dogechat.android.features.file.FileUtils.getMimeTypeFromExtension(file.name)
            } catch (_: Exception) {
                "application/octet-stream"
            }

            val originalName = file.name // keep as-is
            val messageType = when {
                mimeType.lowercase().startsWith("image/") -> MessageType.IMAGE
                mimeType.lowercase().startsWith("audio/") -> MessageType.AUDIO
                mimeType.lowercase().startsWith("video/") -> MessageType.VIDEO
                else -> MessageType.FILE
            }

            if (toPeerIDOrNull != null) {
                sendPrivateFile(toPeerIDOrNull, file, originalName, mimeType, messageType)
            } else {
                sendPublicFile(channelOrNull, file, originalName, mimeType, messageType)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ File send failed completely: ${e.message}", e)
        }
    }

    private fun sendPrivateFile(
        toPeerID: String,
        file: File,
        displayName: String,
        mimeType: String,
        messageType: MessageType
    ) {
        val fileId = meshService.sendFile(file, recipientPeerID = toPeerID, channel = null)
        if (fileId == null) {
            Log.e(TAG, "❌ Failed to start private file send (meshService returned null fileId)")
            return
        }

        val previewText = when (messageType) {
            MessageType.IMAGE -> "📷 ${displayName}"
            MessageType.AUDIO -> "🎤 voice message"
            MessageType.VIDEO -> "🎬 ${displayName}"
            MessageType.FILE -> "📎 ${displayName}"
            MessageType.TEXT -> displayName
        }

        val msg = DogechatMessage(
            sender = state.getNicknameValue() ?: meshService.myPeerID,
            content = previewText,
            timestamp = Date(),
            isRelay = false,
            isPrivate = true,
            recipientNickname = try { meshService.getPeerNicknames()[toPeerID] } catch (_: Exception) { null },
            senderPeerID = meshService.myPeerID,
            messageType = messageType,
            mediaFileName = displayName,
            mediaMimeType = mimeType,
            mediaFileSize = file.length(),
            mediaFileId = fileId
        )

        messageManager.addPrivateMessage(toPeerID, msg)
        seedProgress(msg.id, 0, 100)

        synchronized(transferMessageMap) {
            transferMessageMap[fileId] = msg.id
            messageTransferMap[msg.id] = fileId
        }

        Log.d(TAG, "📤 Started private file send to ${toPeerID.take(8)} ($displayName, $mimeType, id=$fileId)")
    }

    private fun sendPublicFile(
        channelOrNull: String?,
        file: File,
        displayName: String,
        mimeType: String,
        messageType: MessageType
    ) {
        val fileId = meshService.sendFile(file, recipientPeerID = null, channel = channelOrNull)
        if (fileId == null) {
            Log.e(TAG, "❌ Failed to start public file send (meshService returned null fileId)")
            return
        }

        val previewText = when (messageType) {
            MessageType.IMAGE -> "📷 ${displayName}"
            MessageType.AUDIO -> "🎤 voice message"
            MessageType.VIDEO -> "🎬 ${displayName}"
            MessageType.FILE -> "📎 ${displayName}"
            MessageType.TEXT -> displayName
        }

        val message = DogechatMessage(
            sender = state.getNicknameValue() ?: meshService.myPeerID,
            content = previewText,
            timestamp = Date(),
            isRelay = false,
            senderPeerID = meshService.myPeerID,
            channel = channelOrNull,
            messageType = messageType,
            mediaFileName = displayName,
            mediaMimeType = mimeType,
            mediaFileSize = file.length(),
            mediaFileId = fileId
        )

        if (!channelOrNull.isNullOrBlank()) {
            channelManager.addChannelMessage(channelOrNull, message, meshService.myPeerID)
        } else {
            messageManager.addMessage(message)
        }

        seedProgress(message.id, 0, 100)

        synchronized(transferMessageMap) {
            transferMessageMap[fileId] = message.id
            messageTransferMap[message.id] = fileId
        }

        Log.d(TAG, "📤 Started broadcast file send ($displayName, $mimeType, id=$fileId)")
    }

    /**
     * Cancel a media transfer by message ID
     */
    fun cancelMediaSend(messageId: String) {
        val transferId = synchronized(transferMessageMap) { messageTransferMap[messageId] }
        if (transferId != null) {
            try {
                meshService.cancelFileTransfer(transferId)
            } catch (_: Exception) { }
            synchronized(transferMessageMap) {
                transferMessageMap.remove(transferId)
                messageTransferMap.remove(messageId)
            }
        }
    }

    /**
     * Called by UI or delegate when progress updates are available.
     * This is optional; actual progress callbacks are handled via BluetoothMeshDelegate in MeshDelegateHandler.
     */
    fun updateProgressByFileId(fileId: String, sentPercent: Int) {
        val msgId = synchronized(transferMessageMap) { transferMessageMap[fileId] } ?: return
        messageManager.updateMessageDeliveryStatus(
            msgId,
            com.dogechat.android.model.DeliveryStatus.PartiallyDelivered(sentPercent, 100)
        )
        if (sentPercent >= 100) {
            // Mark delivered
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

    private fun seedProgress(messageId: String, reached: Int, total: Int) {
        messageManager.updateMessageDeliveryStatus(
            messageId,
            com.dogechat.android.model.DeliveryStatus.PartiallyDelivered(reached, total)
        )
    }

    private fun sha256Hex(bytes: ByteArray): String = try {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        bytes.size.toString(16)
    }
}