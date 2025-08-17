package com.dogechat.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Central color definitions for Compose UI.
 * Mirror important colors with values in res/values/colors.xml for consistency.
 *
 * Use ThemeColors as the single source of truth from Compose code.
 */

object ThemeColors {
    // Brand / primary
    val Gold = Color(0xFFFFD700)       // Dogecoin Gold
    val DarkGold = Color(0xFFE6B800)   // Slightly darker gold

    // Error / alert
    val ErrorRed = Color(0xFFFF3B30)   // iOS-like red

    // Background / surfaces
    val BackgroundDark = Color(0xFF000000)
    val SurfaceDark = Color(0xFF111111)
    val BackgroundLight = Color(0xFFFFFFFF)
    val SurfaceLight = Color(0xFFF8F8F8)

    // Mentions / hashtags
    val MentionColor = Color(0xFF32CD32) // LimeGreen for mentions
    val HashtagColor = Color(0xFF0080FF) // Blue for hashtags

    // RSSI gradient (strong -> weak)
    val RssiStrong = Color(0xFF00FF00) // Bright Green
    val RssiGood   = Color(0xFFFFFF00) // Yellow
    val RssiMedium = Color(0xFFFFA500) // Orange
    val RssiWeak   = Color(0xFFFF8000) // Dark Orange
    val RssiBad    = Color(0xFFFF3B30) // Red

    // Username stable palette (distinct, terminal-friendly), fully opaque ARGB 0xFFRRGGBB
    val UsernamePalette = listOf(
        Color(0xFFFFD700), // Dogecoin Gold
        Color(0xFF00FF9F), // Spring Green / Mint
        Color(0xFF00FFFF), // Cyan
        Color(0xFF32CD32), // LimeGreen
        Color(0xFFFFA500), // Orange
        Color(0xFF0080FF), // Bright Blue
        Color(0xFFFF0080), // Hot Pink
        Color(0xFF8000FF), // Purple
        Color(0xFFFF8000), // Deep Orange
        Color(0xFFFF3B30), // Error Red
        Color(0xFF80FFFF), // Light Cyan
        Color(0xFFFF8080), // Light Pink
        Color(0xFF8080FF), // Light Blue
        Color(0xFFFFFF80), // Pale Yellow
        Color(0xFFFF80FF), // Light Magenta
        Color(0xFFFFFF00)  // Bright Yellow
    )

    /**
     * Deterministically choose a username color from the palette using a stable hash.
     * Returns a color that will be the same for the same identifier (peer ID or nickname).
     */
    fun getUsernameColor(identifier: String): Color {
        if (identifier.isEmpty()) return UsernamePalette[0]
        val idx = abs(identifier.hashCode()) % UsernamePalette.size
        return UsernamePalette[idx]
    }
}
