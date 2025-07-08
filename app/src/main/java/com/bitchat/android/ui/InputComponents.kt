package com.bitchat.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Input components for ChatScreen
 * Extracted from ChatScreen.kt for better organization
 */

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp), // Reduced padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Prompt
        Text(
            text = when {
                selectedPrivatePeer != null -> "<@$nickname> →"
                currentChannel != null -> "<@$nickname> →"  // Could show if channel is encrypted
                else -> "<@$nickname>"
            },
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = when {
                selectedPrivatePeer != null -> Color(0xFFFF8C00) // Orange for private
                currentChannel != null -> Color(0xFFFF8C00) // Orange if encrypted channel
                else -> colorScheme.primary
            },
            fontFamily = FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Text input
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = colorScheme.primary,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.width(8.dp)) // Reduced spacing
        
        // Send button - smaller with light green background
        IconButton(
            onClick = onSend,
            modifier = Modifier.size(32.dp) // Reduced from 40dp
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp) // Reduced size
                    .background(
                        color = Color(0xFF00C851).copy(alpha = 0.15f), // Light green background
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "↑",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), // Smaller arrow
                    color = Color(0xFF00C851) // Green arrow
                )
            }
        }
    }
}

@Composable
fun CommandSuggestionsBox(
    suggestions: List<CommandSuggestion>,
    onSuggestionClick: (CommandSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Column(
        modifier = modifier
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(vertical = 8.dp)
    ) {
        suggestions.forEach { suggestion: CommandSuggestion ->
            CommandSuggestionItem(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
fun CommandSuggestionItem(
    suggestion: CommandSuggestion,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(Color.Gray.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show all aliases together
        val allCommands = if (suggestion.aliases.isNotEmpty()) {
            listOf(suggestion.command) + suggestion.aliases
        } else {
            listOf(suggestion.command)
        }
        
        Text(
            text = allCommands.joinToString(", "),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            ),
            color = colorScheme.primary,
            fontSize = 11.sp
        )
        
        // Show syntax if any
        suggestion.syntax?.let { syntax ->
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = syntax,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = colorScheme.onSurface.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Show description
        Text(
            text = suggestion.description,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = colorScheme.onSurface.copy(alpha = 0.7f),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
