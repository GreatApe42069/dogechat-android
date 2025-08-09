package com.dogechat.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colors that match the iOS dogechat theme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF39FF14),        // Bright yellow (terminal-like)
    onPrimary = Color.Black,
    secondary = Color(0xFF2ECB10),      // Darker yellow
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFF39FF14),   // yellow on black
    surface = Color(0xFF111111),        // Very dark gray
    onSurface = Color(0xFF39FF14),      // yellow text
    error = Color(0xFFFF5555),          // Red for errors
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFFFFF00),        // Dark yellow
    onPrimary = Color.White,
    secondary = Color(0xFF006600),      // Even darker yellow
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFFFFFF00),   // Dark yellow on white
    surface = Color(0xFFF8F8F8),        // Very light gray
    onSurface = Color(0xFFFFFF00),      // Dark yellow text
    error = Color(0xFFCC0000),          // Dark red for errors
    onError = Color.White
)

@Composable
fun dogechatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
