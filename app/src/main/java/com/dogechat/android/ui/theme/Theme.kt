package com.dogechat.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

/**
 * Compose Material3 theme for Dogechat.
 * Uses ThemeColors as single source of truth for color values.
 *
 * Merged with upstream bitchat system UI logic:
 * - Adjusts system bar icon appearance based on dark/light mode
 * - Sets navigation bar color to match background and disables contrast enforcement on Q+
 * - Keeps Dogechat brand color palette (ThemeColors)
 */

// Dark theme palette (Dogechat brand)
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

// Light theme palette (Dogechat brand)
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

    // Upstream parity: adjust system UI chrome based on theme
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Light status bar icons when in light theme
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else {
                    0
                }
            }

            // Match nav bar to background for a seamless look
            window.navigationBarColor = colorScheme.background.toArgb()

            // Disable contrast enforcement for nav bar (prevents forced scrims)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}