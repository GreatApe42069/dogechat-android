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
 * Format message as annotated string with iOS-style formatting
 * Timestamp at END, peer colors, hashtag suffix handling
 */
fun formatMessageAsAnnotatedString(
    message: dogechatMessage,
    currentUserNickname: String,
    meshService: BluetoothMeshService,
    colorScheme: ColorScheme,
    timeFormatter: SimpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
    
    // Determine if this message was sent by self
    val isSelf = message.senderPeerID == meshService.myPeerID || 
                 message.sender == currentUserNickname ||
                 message.sender.startsWith("$currentUserNickname#")
    
    if (message.sender != "system") {
        // Get base color for this peer (iOS-style color assignment)
        val baseColor = if (isSelf) {
            Color(0xFFFF9500) // Orange for self (iOS orange)
        } else {
            getPeerColor(message, isDark)
        }
        
        // Split sender into base name and hashtag suffix
        val (baseName, suffix) = splitSuffix(message.sender)
        
        // Sender prefix "<@"
        builder.pushStyle(SpanStyle(
            color = baseColor,
            fontSize = 14.sp,
            fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Medium
        ))
        builder.append("<@")
        builder.pop()
        
        // Base name
        builder.pushStyle(SpanStyle(
            color = baseColor,
            fontSize = 14.sp,
            fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Medium
        ))
        builder.append(baseName)
        builder.pop()
        
        // Hashtag suffix in lighter color (iOS style)
        if (suffix.isNotEmpty()) {
            builder.pushStyle(SpanStyle(
                color = baseColor.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Medium
            ))
            builder.append(suffix)
            builder.pop()
        }
        
        // Sender suffix "> "
        builder.pushStyle(SpanStyle(
            color = baseColor,
            fontSize = 14.sp,
            fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Medium
        ))
        builder.append("> ")
        builder.pop()
        
        // Message content with iOS-style hashtag and mention highlighting
        appendIOSFormattedContent(builder, message.content, message.mentions, currentUserNickname, baseColor, isSelf, isDark)
        
        // iOS-style timestamp at the END (smaller, grey)
        builder.pushStyle(SpanStyle(
            color = Color.Gray.copy(alpha = 0.7f),
            fontSize = 10.sp
        ))
        builder.append(" [${timeFormatter.format(message.timestamp)}]")
        builder.pop()
        
    } else {
        // System message - iOS style
        builder.pushStyle(SpanStyle(
            color = Color.Gray,
            fontSize = 12.sp,
            fontStyle = FontStyle.Italic
        ))
        builder.append("* ${message.content} *")
        builder.pop()
        
        // Timestamp for system messages too
        builder.pushStyle(SpanStyle(
            color = Color.Gray.copy(alpha = 0.5f),
            fontSize = 10.sp
        ))
        builder.append(" [${timeFormatter.format(message.timestamp)}]")
        builder.pop()
    }
    
    return builder.toAnnotatedString()
}

/**
 * Get color for peer based on peer ID (iOS-compatible deterministic colors)
 */
fun getPeerColor(message: dogechatMessage, isDark: Boolean): Color {
    val seed = message.senderPeerID ?: message.sender
    return colorForPeerSeed(seed, isDark)
}

/**
 * Split username into base and hashtag suffix (iOS-compatible)
 */
fun splitSuffix(name: String): Pair<String, String> {
    val parts = name.split("#", limit = 2)
    return if (parts.size > 1) {
        parts[0] to "#${parts[1]}"
    } else {
        name to ""
    }
}

/**
 * Append content with iOS-style formatting
 * Hashtags treated as normal text, mentions get special styling
 */
private fun appendIOSFormattedContent(
    builder: AnnotatedString.Builder,
    content: String,
    mentions: List<String>?,
    currentUserNickname: String,
    baseColor: Color,
    isSelf: Boolean,
    isDark: Boolean
) {
    // Patterns for #hashtags and @mentions (supports hashtags as suffixes)
    val hashtagPattern = """#[\w]+""".toRegex()
    val mentionPattern = """@[\w#]+""".toRegex()
    
    val hashtagMatches = hashtagPattern.findAll(content).toList()
    val mentionMatches = mentionPattern.findAll(content).toList()
    
    // Combine and sort matches, but exclude hashtags that overlap with mentions
    val mentionRanges = mentionMatches.map { it.range }
    fun overlapsMention(range: IntRange): Boolean {
        return mentionRanges.any { mentionRange ->
            range.first < mentionRange.last && range.last > mentionRange.first
        }
    }
    
    val allMatches = mutableListOf<Pair<IntRange, String>>()
    
    // Add hashtag matches that don't overlap with mentions
    for (match in hashtagMatches) {
        if (!overlapsMention(match.range)) {
            allMatches.add(match.range to "hashtag")
        }
    }
    
    // Add all mention matches
    for (match in mentionMatches) {
        allMatches.add(match.range to "mention") 
    }
    
    allMatches.sortBy { it.first.first }
    
    var lastEnd = 0
    val isMentioned = mentions?.contains(currentUserNickname) == true
    
    for ((range, type) in allMatches) {
        // Add text before match
        if (lastEnd < range.first) {
            val beforeText = content.substring(lastEnd, range.first)
            if (beforeText.isNotEmpty()) {
                builder.pushStyle(SpanStyle(
                    color = baseColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Normal
                ))
                if (isMentioned) {
                    // Make entire message bold if user is mentioned
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(beforeText)
                    builder.pop()
                } else {
                    builder.append(beforeText)
                }
                builder.pop()
            }
        }
        
        // Add styled match
        val matchText = content.substring(range.first, range.last + 1)
        when (type) {
            "mention" -> {
                // iOS-style mention with hashtag suffix support
                val mentionWithoutAt = matchText.removePrefix("@")
                val (mBase, mSuffix) = splitSuffix(mentionWithoutAt)
                
                // Check if this mention targets current user
                val isMentionToMe = mBase == currentUserNickname
                val mentionColor = if (isMentionToMe) Color(0xFFFF9500) else baseColor
                
                // "@" symbol
                builder.pushStyle(SpanStyle(
                    color = mentionColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelf) FontWeight.Bold else FontWeight.SemiBold
                ))
                builder.append("@")
                builder.pop()
                
                // Base name
                builder.pushStyle(SpanStyle(
                    color = mentionColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelf) FontWeight.Bold else FontWeight.SemiBold
                ))
                builder.append(mBase)
                builder.pop()
                
                // Hashtag suffix in lighter color
                if (mSuffix.isNotEmpty()) {
                    builder.pushStyle(SpanStyle(
                        color = mentionColor.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = if (isSelf) FontWeight.Bold else FontWeight.SemiBold
                    ))
                    builder.append(mSuffix)
                    builder.pop()
                }
            }
            "hashtag" -> {
                // iOS-style: render hashtags like normal content (no special styling)
                builder.pushStyle(SpanStyle(
                    color = baseColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Normal
                ))
                if (isMentioned) {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(matchText)
                    builder.pop()
                } else {
                    builder.append(matchText)
                }
                builder.pop()
            }
        }
        
        lastEnd = range.last + 1
    }

    // Add remaining text
    if (lastEnd < content.length) {
        val remainingText = content.substring(lastEnd)
        builder.pushStyle(SpanStyle(
            color = baseColor,
            fontSize = 14.sp,
            fontWeight = if (isSelf) FontWeight.Bold else FontWeight.Normal
        ))
        if (isMentioned) {
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            builder.append(remainingText)
            builder.pop()
        } else {
            builder.append(remainingText)
        }
        builder.pop()
    }
}

/**
 * Deterministic color generation for peers (iOS-compatible)
 */
fun colorForPeerSeed(seed: String, isDark: Boolean): Color {
    // Simple hash-based color selection (matches iOS hue/saturation)
    val hash = seed.hashCode()
    val hue = (hash % 360).toFloat()
    val saturation = 0.6f + (hash % 10) / 20f  // 0.6 to 0.7
    val brightness = if (isDark) 0.8f else 0.6f
    
    return Color.hsl(hue, saturation, brightness)
}
