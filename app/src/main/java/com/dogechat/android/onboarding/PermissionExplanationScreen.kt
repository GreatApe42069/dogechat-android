package com.dogechat.android.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Permission explanation screen shown before requesting permissions
 * Explains why Đogechat needs each permission and reassures users about privacy
 */
@Composable
fun PermissionExplanationScreen(
    permissionCategories: List<PermissionCategory>,
    onContinue: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 88.dp) // Leave space for the fixed button
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            // Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome to Đogechat",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Đecentralized mesh messaging over Bluetooth",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurface.copy(alpha = 0.7f)
                    ),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Privacy assurance section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🔒",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Your Privacy is Protected",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        )
                    }

                    Text(
                        text = "• Đogechat doesn't track you or collect any personal data\n" +
                                "• Bluetooth mesh chats are fully offline and require no internet\n" +
                                "• Geohash chats use the internet but your location is generalized\n" +
                                "• Your messages stay locally on your device and peer devices only",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "To work properly, Đogechat needs these permissions:",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
            )

            // Permission categories
            // Remove the top "Internet & Network" category to avoid duplication;
            // we keep the dedicated section below.
            permissionCategories
                .filter { it.type != PermissionType.NETWORK }
                .forEach { category ->
                    PermissionCategoryCard(
                        category = category,
                        colorScheme = colorScheme
                    )
                }

            // --- Network/Internet Permission Explanation Section (kept) ---
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceVariant.copy(alpha = 0.22f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🌐",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Internet & Network Permissions",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                        )
                    }
                    Text(
                        text = "Đogechat only uses internet/network permissions for advanced features:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    )
                    Text(
                        text = "• Dogecoin wallet (SPV) sync and blockchain access\n" +
                                "• Tor Enhanced privacy mode (routes wallet and chat traffic over Tor)\n" +
                                "• Geohash channels and relays (optional online chat)\n\n" +
                                "Offline Bluetooth mesh chat does NOT require internet. You can always use Đogechat fully offline unless you want blockchain or Tor features.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = colorScheme.onSurface.copy(alpha = 0.75f)
                        )
                    )
                }
            }
            // --- End Network/Internet Section ---

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Fixed button at bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary
                )
            ) {
                Text(
                    text = "Grant Permissions",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionCategoryCard(
    category: PermissionCategory,
    colorScheme: ColorScheme
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = getPermissionEmoji(category.type),
                    style = MaterialTheme.typography.titleLarge,
                    color = getPermissionIconColor(category.type),
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = category.type.nameValue,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                )
            }

            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            )

            if (category.type == PermissionType.PRECISE_LOCATION) {
                // Extra emphasis for location permission
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Đogechat does NOT track your location",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFF9800)
                        )
                    )
                }
            }
        }
    }
}

private fun getPermissionEmoji(permissionType: PermissionType): String {
    return when (permissionType) {
        PermissionType.NEARBY_DEVICES -> "📱"
        PermissionType.PRECISE_LOCATION -> "📍"
        PermissionType.NOTIFICATIONS -> "🔔"
        PermissionType.BATTERY_OPTIMIZATION -> "🔋"
        PermissionType.NETWORK -> "🌐"
        PermissionType.STORAGE -> "💾"
        PermissionType.OTHER -> "🔧"
    }
}

private fun getPermissionIconColor(permissionType: PermissionType): Color {
    return when (permissionType) {
        PermissionType.NEARBY_DEVICES -> Color(0xFF2196F3) // Blue
        PermissionType.PRECISE_LOCATION -> Color(0xFF000000) // Yellow (note: currently black)
        PermissionType.NOTIFICATIONS -> Color(0xFFFFD700) // Gold
        PermissionType.BATTERY_OPTIMIZATION -> Color(0xFFF44336) // Red
        PermissionType.NETWORK -> Color(0xFF1976D2) // Deep blue for network
        PermissionType.STORAGE -> Color(0xFF8D6E63) // Brown for storage
        PermissionType.OTHER -> Color(0xFF9C27B0) // Purple
    }
}