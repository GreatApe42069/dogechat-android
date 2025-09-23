package com.dogechat.android.ui

import com.dogechat.android.model.DogechatMessage
import com.dogechat.android.model.MessageType

/**
 * Utilities for building human-friendly notification text/previews.
 */
object NotificationTextUtils {
    /**
     * Build a user-friendly notification preview for private messages, especially attachments.
     * Examples:
     * - Image: "📷 sent an image"
     * - Audio: "🎤 sent a voice message"
     * - File (pdf): "📄 file.pdf"
     * - Text: original message content
     */
    fun buildPrivateMessagePreview(message: DogechatMessage): String {
        return try {
            when (message.messageType) {
                MessageType.IMAGE -> "📷 sent an image"
                MessageType.AUDIO -> "🎤 sent a voice message"
                MessageType.FILE -> {
                    val name = try { message.mediaFileName ?: java.io.File(message.content).name } catch (_: Exception) { message.mediaFileName }
                    if (!name.isNullOrBlank()) {
                        val lower = name.lowercase()
                        val icon = when {
                            lower.endsWith(".pdf") -> "📄"
                            lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z") -> "🗜️"
                            lower.endsWith(".doc") || lower.endsWith(".docx") -> "📄"
                            lower.endsWith(".xls") || lower.endsWith(".xlsx") -> "📊"
                            lower.endsWith(".ppt") || lower.endsWith(".pptx") -> "📈"
                            else -> "📎"
                        }
                        "$icon $name"
                    } else {
                        "📎 sent a file"
                    }
                }
                MessageType.VIDEO -> "🎬 sent a video"
                MessageType.TEXT -> message.content
            }
        } catch (_: Exception) {
            message.content
        }
    }
}