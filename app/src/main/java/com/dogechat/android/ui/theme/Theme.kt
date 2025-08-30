package com.dogechat.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

/**
 * Compose Material3 theme for Dogechat.
 * Uses ThemeColors as single source of truth for color values.
 */

private val DarkColorScheme = darkColorScheme(
    primary = ThemeColors.Gold,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    secondary = ThemeColors.DarkGold,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    background = ThemeColors.BackgroundDark,
    onBackground = ThemeColors.BackgroundLight, // text on dark background -> white
    surface = ThemeColors.SurfaceDark,
    onSurface = ThemeColors.BackgroundLight,
    error = ThemeColors.ErrorRed,
    onError = androidx.compose.ui.graphics.Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = ThemeColors.Gold,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    secondary = ThemeColors.DarkGold,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    background = ThemeColors.BackgroundLight,
    onBackground = ThemeColors.SurfaceLight, // text on light background -> black
    surface = ThemeColors.SurfaceLight,
    onSurface = androidx.compose.ui.graphics.Color.Black,
    error = ThemeColors.ErrorRed,
    onError = androidx.compose.ui.graphics.Color.White
)

@Composable
fun DogechatTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
