import androidx.compose.ui.graphics.Color

/**
 * Generate a consistent color for a username based on their peer ID or nickname
 * Returns colors that work well on both light and dark backgrounds
 */
fun getUsernameColor(identifier: String): Color {
    // Hash the identifier to get a consistent number
    val hash = identifier.hashCode().toUInt()
    
    // Terminal-friendly colors that work on both black and white backgrounds
    val colors = listOf(
        Color(0xFF00FF00), // Bright Green
        Color(0xFF00FFFF), // Cyan  
        Color(0xFFFFFF00), // Yellow
        Color(0xFFFF00FF), // Magenta
        Color(0xFF0080FF), // Bright Blue
        Color(0xFFFF8000), // Orange
        Color(0xFF80FF00), // Lime Green
        Color(0xFF8000FF), // Purple
        Color(0xFFFF0080), // Pink
        Color(0xFF00FF80), // Spring Green
        Color(0xFF80FFFF), // Light Cyan
        Color(0xFFFF8080), // Light Red
        Color(0xFF8080FF), // Light Blue
        Color(0xFFFFFF80), // Light Yellow
        Color(0xFFFF80FF), // Light Magenta
        Color(0xFF80FF80), // Light Green
    )
    
    // Use modulo to get consistent color for same identifier
    return colors[(hash % colors.size.toUInt()).toInt()]
}

fun main() {
    println("Testing username color function:")
    
    val testUsers = listOf("alice", "bob", "charlie", "diana", "eve")
    
    testUsers.forEach { user ->
        val color = getUsernameColor(user)
        println("User '$user' gets color: ${color.value.toString(16).uppercase()}")
    }
    
    // Test consistency - same user should always get same color
    println("\nTesting consistency:")
    repeat(3) {
        val aliceColor = getUsernameColor("alice")
        println("Alice color (test ${it + 1}): ${aliceColor.value.toString(16).uppercase()}")
    }
}
