package com.dogechat.android.ui

import com.dogechat.android.R
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding

@Composable
fun SidebarOverlay(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }

    val connectedPeers by viewModel.connectedPeers.observeAsState(initial = emptyList<String>())
    val joinedChannels by viewModel.joinedChannels.observeAsState(initial = emptySet<String>())
    val currentChannel by viewModel.currentChannel.observeAsState(initial = null)
    val selectedPrivatePeer by viewModel.selectedPrivateChatPeer.observeAsState(initial = null)
    val nickname by viewModel.nickname.observeAsState(initial = "")
    val unreadChannelMessages by viewModel.unreadChannelMessages.observeAsState(initial = emptyMap<String, Int>())
    val peerNicknames by viewModel.peerNicknames.observeAsState(initial = emptyMap<String, String>())
    val peerRSSI by viewModel.peerRSSI.observeAsState(initial = emptyMap<String, Int>())

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(indication = null, interactionSource = interactionSource) { onDismiss() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp)
                .align(Alignment.CenterEnd)
                .clickable { /* Prevent dismissing when clicking sidebar */ }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.Gray.copy(alpha = 0.3f))
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(colorScheme.background.copy(alpha = 0.95f))
                    .statusBarsPadding()
            ) {
                SidebarHeader()

                HorizontalDivider()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (joinedChannels.isNotEmpty()) {
                        item {
                            ChannelsSection(
                                channels = joinedChannels.toList(),
                                currentChannel = currentChannel,
                                colorScheme = colorScheme,
                                onChannelClick = { channel ->
                                    viewModel.switchToChannel(channel)
                                    onDismiss()
                                },
                                onLeaveChannel = { channel ->
                                    viewModel.leaveChannel(channel)
                                },
                                unreadChannelMessages = unreadChannelMessages
                            )
                        }

                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    
                    // People section - switch between mesh and geohash lists (iOS-compatible)
                    item {
                        val selectedLocationChannel by viewModel.selectedLocationChannel.observeAsState()
                        
                        when (selectedLocationChannel) {
                            is com.dogechat.android.geohash.ChannelID.Location -> {
                                // Show geohash people list when in location channel
                                GeohashPeopleList(
                                    viewModel = viewModel,
                                    onTapPerson = onDismiss
                                )
                            }
                            else -> {
                                // Show mesh peer list when in mesh channel (default)
                                PeopleSection(
                                    connectedPeers = connectedPeers,
                                    peerNicknames = peerNicknames,
                                    peerRSSI = peerRSSI,
                                    nickname = nickname,
                                    colorScheme = colorScheme,
                                    selectedPrivatePeer = selectedPrivatePeer,
                                    viewModel = viewModel,
                                    onPrivateChatStart = { peerID ->
                                        viewModel.startPrivateChat(peerID)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarHeader() {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .height(42.dp)
            .fillMaxWidth()
            .background(colorScheme.background.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.your_network).uppercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = colorScheme.primary
        )
    }
}

@Composable
private fun ChannelsSection(
    channels: List<String>,
    currentChannel: String?,
    colorScheme: ColorScheme,
    onChannelClick: (String) -> Unit,
    onLeaveChannel: (String) -> Unit,
    unreadChannelMessages: Map<String, Int>
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Channels",
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        channels.sorted().forEach { channel ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChannelClick(channel) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (channel == currentChannel) colorScheme.primary else colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                if (unreadChannelMessages[channel] ?: 0 > 0) {
                    UnreadBadge(count = unreadChannelMessages[channel] ?: 0, colorScheme = colorScheme)
                }

                IconButton(onClick = { onLeaveChannel(channel) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Leave channel",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

@Composable
private fun PeopleSection(
    connectedPeers: List<String>,
    peerNicknames: Map<String, String>,
    peerRSSI: Map<String, Int>,
    nickname: String,
    colorScheme: ColorScheme,
    selectedPrivatePeer: String?,
    viewModel: ChatViewModel,
    onPrivateChatStart: (String) -> Unit
) {
    val hasUnreadPrivateMessages by viewModel.hasUnreadPrivateMessages.observeAsState(emptySet())

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "People",
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sort peers: favorites first, then alphabetically
        val sortedPeers = connectedPeers.sortedWith(compareByDescending<String> { 
            privateChatManager.isFavorite(it) 
        }.thenBy { 
            peerNicknames[it] ?: it 
        })

        sortedPeers.forEach { peerID ->
            val isFavorite = privateChatManager.isFavorite(peerID)
            val hasUnreadDM = hasUnreadPrivateMessages.contains(peerID)

            PeerItem(
                peerID = peerID,
                displayName = if (peerID == nickname) "You" else (peerNicknames[peerID] ?: peerID),
                signalStrength = convertRSSIToSignalStrength(peerRSSI[peerID]),
                isSelected = peerID == selectedPrivatePeer,
                isFavorite = isFavorite,
                hasUnreadDM = hasUnreadDM,
                colorScheme = colorScheme,
                viewModel = viewModel,
                onItemClick = { onPrivateChatStart(peerID) },
                onToggleFavorite = {
                    viewModel.toggleFavorite(peerID)
                },
                unreadCount = if (hasUnreadDM) 1 else 0
            )
        }
    }
}

@Composable
private fun PeerItem(
    peerID: String,
    displayName: String,
    signalStrength: Int,
    isSelected: Boolean,
    isFavorite: Boolean,
    hasUnreadDM: Boolean,
    colorScheme: ColorScheme,
    viewModel: ChatViewModel,
    onItemClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    unreadCount: Int = 0
) {
    // Split display name for hashtag suffix support (iOS-compatible)
    val (baseName, suffix) = com.dogechat.android.ui.splitSuffix(displayName)
    val isMe = displayName == "You" || peerID == viewModel.nickname.value
    
    // Get consistent peer color (iOS-compatible)
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f
    val assignedColor = viewModel.colorForMeshPeer(peerID, isDark)
    val baseColor = if (isMe) Color(0xFFFF9500) else assignedColor
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .background(
                if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Show unread badge or signal strength  
        if (hasUnreadDM) {
            // Show mail icon for unread DMs (iOS orange)
            Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = "Unread message",
                modifier = Modifier.size(16.dp),
                tint = Color(0xFFFF9500) // iOS orange
            )
        } else {
            SignalStrengthIndicator(signalStrength = signalStrength, colorScheme = colorScheme)
        }

        Spacer(modifier = Modifier.width(8.dp))
        
        // Display name with iOS-style color and hashtag suffix support
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Base name with peer-specific color
            Text(
                text = baseName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal
                ),
                color = baseColor
            )
            
            // Hashtag suffix in lighter shade (iOS-style)
            if (suffix.isNotEmpty()) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    ),
                    color = baseColor.copy(alpha = 0.6f)
                )
            }
        }
        
        // Favorite star with proper filled/outlined states
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                modifier = Modifier.size(16.dp),
                tint = if (isFavorite) Color(0xFFFFD700) else Color(0xFF4CAF50)
            )
        }
    }
}



@Composable
private fun SignalStrengthIndicator(signalStrength: Int, colorScheme: ColorScheme) {
    Row(modifier = Modifier.width(24.dp)) {
        repeat(3) { index ->
            val opacity = if (signalStrength >= (index + 1) * 33) 1f else 0.2f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((4 + index * 2).dp)
                    .background(
                        colorScheme.onSurface.copy(alpha = opacity),
                        RoundedCornerShape(1.dp)
                    )
            )
            if (index < 2) Spacer(modifier = Modifier.width(2.dp))
        }
    }
}

@Composable
private fun UnreadBadge(count: Int, colorScheme: ColorScheme, modifier: Modifier = Modifier) {
    if (count > 0) {
        Box(
            modifier = modifier
                .background(Color(0xFFFFD700), shape = RoundedCornerShape(10.dp))
                .padding(horizontal = 2.dp, vertical = 0.dp)
                .defaultMinSize(minWidth = 14.dp, minHeight = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.Black
            )
        }
    }
}

private fun convertRSSIToSignalStrength(rssi: Int?): Int {
    if (rssi == null) return 0
    return when {
        rssi >= -40 -> 100
        rssi >= -55 -> 85
        rssi >= -70 -> 70
        rssi >= -85 -> 50
        rssi >= -100 -> 25
        else -> 0
    }
}

@Composable
fun GeohashPeopleList(
    viewModel: ChatViewModel,
    onTapPerson: () -> Unit
) {
    val geohashPeople by viewModel.geohashPeople.observeAsState(emptyList())
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.red + colorScheme.background.green + colorScheme.background.blue < 1.5f

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Geohash People",
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        geohashPeople.sortedBy { it.displayName }.forEach { person ->
            val baseColor = viewModel.colorForNostrPubkey(person.pubkeyHex, isDark)
            val (baseName, suffix) = splitSuffix(person.displayName)
            val isTeleported = viewModel.isPersonTeleported(person.pubkeyHex)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        viewModel.startGeohashDM(person.pubkeyHex)
                        onTapPerson()
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isTeleported) {
                    Icon(
                        imageVector = Icons.Default.PinDrop,
                        contentDescription = "Teleported",
                        tint = baseColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                } else {
                    SignalStrengthIndicator(signalStrength = 100, colorScheme = colorScheme) // Full for geohash
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = baseName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        ),
                        color = baseColor
                    )
                    
                    if (suffix.isNotEmpty()) {
                        Text(
                            text = suffix,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            color = baseColor.copy(alpha = 0.6f)
                        )
                    }
                }

                // No favorite for geohash people
            }
        }
    }
}
