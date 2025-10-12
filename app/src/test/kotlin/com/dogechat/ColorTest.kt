package com.dogechat

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Generate a consistent color for a username based on their peer ID or nickname
 * Returns colors that work well on both light and dark backgrounds.
 *
 * Notes:
 * - Compose Color hex literals use ARGB: 0xAARRGGBB (we use FF for fully opaque)
 * - Keep this palette small, distinct and stable across themes.
 */

class ColorTest {
    fun getUsernameColor(identifier: String): Color {
        // Stable hash for the identifier
        val hash = identifier.hashCode().toUInt()

        // Terminal-friendly / theme-friendly palette (all fully opaque, ARGB = 0xFFRRGGBB)
        // Purposeful variety: Dogecoin gold, greens, blues, orange, pink, purples, cyan, reds...
        val colors = listOf(
            Color(0xFFFFD700), // Dogecoin Gold
            Color(0xFF00FF9F), // Spring Green / Mint
            Color(0xFF00FFFF), // Cyan
            Color(0xFF32CD32), // LimeGreen
            Color(0xFFFFA500), // Orange
            Color(0xFF0080FF), // Bright Blue
            Color(0xFFFF0080), // Hot Pink
            Color(0xFF8000FF), // Purple
            Color(0xFFFF8000), // Deep Orange
            Color(0xFFFF3B30), // iOS-like Error Red
            Color(0xFF80FFFF), // Light Cyan
            Color(0xFFFF8080), // Light Red/Pink
            Color(0xFF8080FF), // Light Blue
            Color(0xFFFFFF80), // Pale Yellow
            Color(0xFFFF80FF), // Light Magenta
            Color(0xFFFFFF00)  // Bright Yellow
        )

        return colors[(hash % colors.size.toUInt()).toInt()]
    }

    @Test
    fun is_username_derived_color_consistent() {
        println("Testing username color function:")

        val testUsers = listOf("alice", "bob", "charlie", "diana", "eve")

        testUsers.forEach { user ->
            val color = getUsernameColor(user)
            // print ARGB hex string (leading FF if fully opaque)
            println("User '$user' gets color: ${color.value.toULong().toString(16).uppercase()}")
        }

        val `alice'sColor` = getUsernameColor(testUsers[0])
        val `bob'sColor` = getUsernameColor(testUsers[1])
        val `charlie'sColor` = getUsernameColor(testUsers[2])
        val `diana'sColor` = getUsernameColor(testUsers[3])
        val `eve'sColor` = getUsernameColor(testUsers[4])

        // Test consistency - same user should always get same color
        println("\nTesting consistency:")
        repeat(3) {
            val `alice's_color` = getUsernameColor(testUsers[0])
            val `bob's_color` = getUsernameColor(testUsers[1])
            val `charlie's_color` = getUsernameColor(testUsers[2])
            val `diana's_color` = getUsernameColor(testUsers[3])
            val `eve's_color` = getUsernameColor(testUsers[4])

            assertEquals(`alice'sColor`, `alice's_color`)
            assertEquals(`bob'sColor`, `bob's_color`)
            assertEquals(`charlie'sColor`, `charlie's_color`)
            assertEquals(`diana'sColor`, `diana's_color`)
            assertEquals(`eve'sColor`, `eve's_color`)

            println("Alice color (test ${it + 1}): ${`alice'sColor`.value.toULong().toString(16).uppercase()}")
        }
    }
}
