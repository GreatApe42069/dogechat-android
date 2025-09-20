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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView

/**
 * Compose Material3 theme for Dogechat.
 * Uses ThemeColors as single source of truth for brand colors.
 * Light mode uses jasmine gold background with dark text for readability.
 */

// Dark theme palette (Dogechat brand)
private val DarkColorScheme = darkColorScheme(
    primary = ThemeColors.dogeGold,
    onPrimary = Color.Black,
    secondary = ThemeColors.DarkGold,
    onSecondary = Color.Black,
    background = ThemeColors.BackgroundDark,
    onBackground = ThemeColors.BackgroundLight,
    surface = ThemeColors.SurfaceDark,
    onSurface = ThemeColors.BackgroundLight,
    error = ThemeColors.ErrorRed,
    onError = Color.Black
)

// Light theme palette (jasmine gold background + dark text)
private val LightColorScheme = lightColorScheme(
    primary = ThemeColors.dogeGold,
    onPrimary = Color.Black,
    secondary = ThemeColors.DarkGold,
    onSecondary = Color.Black,
    background = ThemeColors.BackgroundLight, // 0xFFEBCA66
    onBackground = Color(0xFF000000),
    surface = ThemeColors.SurfaceLight,
    onSurface = Color(0xFF000000),
    error = ThemeColors.ErrorRed,
    onError = Color.White
)

@Composable
fun DogechatTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
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

    // Adjust status bar icon appearance (light/dark icons). Colors are set in MainActivity.
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Dark icons on bright yellow status bar (we always want light status bars)
                window.insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }

            // Keep nav bar contrast off to avoid forced scrims when transparent
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