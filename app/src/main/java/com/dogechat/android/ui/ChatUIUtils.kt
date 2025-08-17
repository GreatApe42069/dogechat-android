package com.dogechat.android.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dogechat.android.model.dogechatMessage
import com.dogechat.android.mesh.BluetoothMeshService
import androidx.compose.material3.ColorScheme
import com.dogechat.android.ui.theme.ThemeColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility functions for ChatScreen UI components
 * Centralized color usage through ThemeColors for consistency.
 */

/**
 * RSSI -> color gradient (Green → Yellow → Orange → Darker Orange → Red)
 */
fun getRSSIColor(rssi: Int): Color {
    return when {
        rssi >= -50 -> ThemeColors.RssiStrong
        rssi >= -60 -> ThemeColors.RssiGood
        rssi >= -70 -> ThemeColors.RssiMedium
        rssi >= -80 -> ThemeColors.RssiWeak
        else -> ThemeColors.RssiBad
    }
}

/**
 * Format message as annotated string with proper styling
 * Uses Material3 colorScheme for base text and ThemeColors for accents.
 */
fun formatMessageAsAnnotatedString(
    message: dogechatMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
): AnnotatedString {
    val builder = AnnotatedString.Builder()

    // Timestamp - subtle tint of primary
    val timestampColor = if (message.sender == "system") Color.Gray else colorScheme.primary.copy(alpha = 0.7f)
    builder.pushStyle(SpanStyle(
        color = timestampColor,
        fontSize = 12.sp
    ))
    builder.append("[${timeFormatter.format(message.timestamp)}] ")
    builder.pop()

    if (message.sender != "system") {
        // Sender color: if it's me (local peer) use primary; otherwise deterministic username color
        val senderColor = when {
            message.senderPeerID == meshService.myPeerID -> colorScheme.primary
            else -> {
                val id = message.senderPeerID ?: message.sender
                ThemeColors.getUsernameColor(id)
            }
        }

        builder.pushStyle(SpanStyle(
            color = senderColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        ))
        builder.append("<@${message.sender}> ")
        builder.pop()

        // Message content with mentions and hashtags highlighted
        appendFormattedContent(builder, message.content, message.mentions, currentUserNickname, colorScheme)

    } else {
        // System message
        builder.pushStyle(SpanStyle(
            color = Color.Gray,
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic
        ))
        builder.append("* ${message.content} *")
        builder.pop()
    }

    return builder.toAnnotatedString()
}

/**
 * Append formatted content with hashtag and mention highlighting
 * - hashtags use ThemeColors.HashtagColor (but still readable with current Material color scheme)
 * - mentions use ThemeColors.MentionColor (distinct and visible)
 * - normal text uses colorScheme.primary for theme awareness
 */
private fun appendFormattedContent(
    builder: AnnotatedString.Builder,
    content: String,
    mentions: List<String>?,
    currentUserNickname: String,
    colorScheme: ColorScheme
) {
    val isMentioned = mentions?.contains(currentUserNickname) == true

    // Parse hashtags and mentions
    val hashtagPattern = "#([a-zA-Z0-9_]+)".toRegex()
    val mentionPattern = "@([a-zA-Z0-9_]+)".toRegex()

    val hashtagMatches = hashtagPattern.findAll(content).toList()
    val mentionMatches = mentionPattern.findAll(content).toList()

    // Combine and sort all matches
    val allMatches = (hashtagMatches.map { it.range to "hashtag" } +
            mentionMatches.map { it.range to "mention" })
        .sortedBy { it.first.first }

    var lastEnd = 0

    for ((range, type) in allMatches) {
        // Add text before the match
        if (lastEnd < range.first) {
            val beforeText = content.substring(lastEnd, range.first)
            builder.pushStyle(SpanStyle(
                color = colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = if (isMentioned) FontWeight.Bold else FontWeight.Normal
            ))
            builder.append(beforeText)
            builder.pop()
        }

        // Add the styled match
        val matchText = content.substring(range.first, range.last + 1)
        when (type) {
            "hashtag" -> {
                builder.pushStyle(SpanStyle(
                    color = ThemeColors.HashtagColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                ))
            }
            "mention" -> {
                builder.pushStyle(SpanStyle(
                    color = ThemeColors.MentionColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ))
            }
        }
        builder.append(matchText)
        builder.pop()

        lastEnd = range.last + 1
    }

    // Add remaining text
    if (lastEnd < content.length) {
        val remainingText = content.substring(lastEnd)
        builder.pushStyle(SpanStyle(
            color = colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = if (isMentioned) FontWeight.Bold else FontWeight.Normal
        ))
        builder.append(remainingText)
        builder.pop()
    }
}
