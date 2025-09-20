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
    val dogeGold = Color(0xFFFFD700)      // Dogecoin Gold
    val DarkGold = Color(0xFFE6B800)      // Slightly darker gold
    val BrandAccent = Color(0xFFFFFF00)   // Bright yellow accent (requested)

    // Error / alert
    val ErrorRed = Color(0xFFFF3B30)      // iOS-like red

    // Background / surfaces
    val BackgroundDark = Color(0xFF000000)
    val SurfaceDark = Color(0xFF111111)
    // Updated light background to jasmine gold (requested)
    val BackgroundLight = Color(0xFFEBCA66)
    // Slightly darker surface for cards on light background
    val SurfaceLight = Color(0xFFE1B751)

    // Mentions / hashtags (kept)
    val MentionColor = Color(0xFF32CD32) // LimeGreen for mentions
    val HashtagColor = Color(0xFF0080FF) // Blue for hashtags

    // RSSI gradient (strong -> weak) (kept)
    val RssiStrong = Color(0xFF00FF00) // Bright Green
    val RssiGood   = Color(0xFFFFFF00) // Yellow
    val RssiMedium = Color(0xFFFFA500) // Orange
    val RssiWeak   = Color(0xFFFF8000) // Dark Orange
    val RssiBad    = Color(0xFFFF3B30) // Red

    // Username palette optimized for visibility on light (jasmine gold) background:
    // Darker, saturated colors across gold/yellow/blue/purple/green/orange/red families.
    // Username stable palette Golds / Yellows (variants, distinct, terminal-friendly), fully opaque ARGB 0xFFRRGGBB

    val UsernamePalette = listOf(
        Color(0xFFFFD700), // Dogecoin Gold
        Color(0xFF00FFFF), // Cyan
        Color(0xFF0080FF), // Bright Blue
        Color(0xFFFF0080), // Hot Pink
        Color(0xFF8000FF), // Purple
        Color(0xFF80FFFF), // Light Cyan
        Color(0xFFFF8080), // Light Pink
        Color(0xFF8080FF), // Light Blue
        Color(0xFFFFFF80), // Pale Yellow
        Color(0xFFFF80FF), // Light Magenta
        Color(0xFFFFFF00), // Bright Yellow
        Color(0xFFE6B800), // Dark Gold
        Color(0xFFB8860B), // DarkGoldenRod
        Color(0xFF8B7500), // Dark Yellow-Brown
        Color(0xFF9C7A00), // Deep Yellow Ochre

        // Blues
        Color(0xFF003366), // Dark Midnight Blue
        Color(0xFF004C99), // Strong Azure
        Color(0xFF005BBB), // Deep Blue

        // Purples
        Color(0xFF4B0082), // Indigo
        Color(0xFF6A0DAD), // Royal Purple
        Color(0xFF5E2A7E), // Grape

        // Greens
        Color(0xFF32CD32), // LimeGreen
        Color(0xFF00FF9F), // Spring Green / Mint
        Color(0xFF006400), // Dark Green
        Color(0xFF0B6623), // Forest Green
        Color(0xFF006D77), // Deep Teal

        // Oranges
        Color(0xFFFF9800), // Warning Orange
        Color(0xFFFF8000), // Deep Orange
        Color(0xFFCC5500), // Burnt Orange
        Color(0xFFB34700), // Dark Orange
        Color(0xFFFFA500), // Orange

        // Reds
        Color(0xFF8B0000), // Dark Red
        Color(0xFFA00034), // Deep Crimson

        // Accents
        Color(0xFF008080), // Teal
        Color(0xFF2F4F4F), // Dark Slate Gray
        Color(0xFF3B3B98)  // Dark Slate Blue
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