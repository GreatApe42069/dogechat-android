package com.bitchat.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Sidebar components for ChatScreen
 * Extracted from ChatScreen.kt for better organization
 */

@Composable
fun SidebarOverlay(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val connectedPeers by viewModel.connectedPeers.observeAsState(emptyList())
    val joinedChannels by viewModel.joinedChannels.observeAsState(emptyList())
    val currentChannel by viewModel.currentChannel.observeAsState()
    val selectedPrivatePeer by viewModel.selectedPrivateChatPeer.observeAsState()
    val nickname by viewModel.nickname.observeAsState("")
    
    // Get peer data from mesh service
    val peerNicknames = viewModel.meshService.getPeerNicknames()
    val peerRSSI = viewModel.meshService.getPeerRSSI()
    
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp)
                .align(Alignment.CenterEnd)
                .clickable { /* Prevent dismissing when clicking sidebar */ }
        ) {
            // Grey vertical bar for visual continuity (matches iOS)
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
                    .background(colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.statusBars) // Add status bar padding
            ) {
                SidebarHeader()
                
                Divider()
                
                // Scrollable content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Channels section
                    if (joinedChannels.isNotEmpty()) {
                        item {
                            ChannelsSection(
                                channels = joinedChannels.toList(), // Convert Set to List
                                currentChannel = currentChannel,
                                colorScheme = colorScheme,
                                onChannelClick = { channel ->
                                    viewModel.switchToChannel(channel)
                                    onDismiss()
                                },
                                onLeaveChannel = { channel ->
                                    viewModel.leaveChannel(channel)
                                }
                            )
                        }
                        
                        item {
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    
                    // People section
                    item {
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

@Composable
private fun SidebarHeader() {
    val colorScheme = MaterialTheme.colorScheme
    
    Row(
        modifier = Modifier
            .height(36.dp) // Match reduced main header height
            .fillMaxWidth()
            .background(colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "YOUR NETWORK",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun ChannelsSection(
    channels: List<String>,
    currentChannel: String?,
    colorScheme: ColorScheme,
    onChannelClick: (String) -> Unit,
    onLeaveChannel: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person, // Using Person icon as placeholder
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "CHANNELS",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
        }
        
        channels.forEach { channel ->
            val isSelected = channel == currentChannel
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChannelClick(channel) }
                    .background(
                        if (isSelected) colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#$channel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                
                // Leave channel button
                IconButton(
                    onClick = { onLeaveChannel(channel) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Leave channel",
                        modifier = Modifier.size(14.dp),
                        tint = colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
fun PeopleSection(
    connectedPeers: List<String>,
    peerNicknames: Map<String, String>,
    peerRSSI: Map<String, Int>,
    nickname: String,
    colorScheme: ColorScheme,
    selectedPrivatePeer: String?,
    viewModel: ChatViewModel,
    onPrivateChatStart: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person, // Using Person icon for people
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "PEOPLE",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
        }
        
        if (connectedPeers.isEmpty()) {
            Text(
                text = "No one connected",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        } else {
            // Sort peers: favorites first, then by nickname
            val sortedPeers = connectedPeers.sortedWith { peer1, peer2 ->
                val isFav1 = viewModel.isFavorite(peer1)
                val isFav2 = viewModel.isFavorite(peer2)
                
                when {
                    isFav1 && !isFav2 -> -1
                    !isFav1 && isFav2 -> 1
                    else -> {
                        val name1 = if (peer1 == nickname) "You" else (peerNicknames[peer1] ?: peer1)
                        val name2 = if (peer2 == nickname) "You" else (peerNicknames[peer2] ?: peer2)
                        name1.compareTo(name2, ignoreCase = true)
                    }
                }
            }
            
            sortedPeers.forEach { peerID ->
                PeerItem(
                    peerID = peerID,
                    displayName = if (peerID == nickname) "You" else (peerNicknames[peerID] ?: peerID),
                    signalStrength = peerRSSI[peerID] ?: 0,
                    isSelected = peerID == selectedPrivatePeer,
                    isFavorite = viewModel.isFavorite(peerID),
                    colorScheme = colorScheme,
                    onItemClick = { onPrivateChatStart(peerID) },
                    onToggleFavorite = { viewModel.toggleFavorite(peerID) }
                )
            }
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
    colorScheme: ColorScheme,
    onItemClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
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
        // Signal strength indicators
        SignalStrengthIndicator(
            signalStrength = signalStrength,
            colorScheme = colorScheme
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        
        // Favorite star
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                modifier = Modifier.size(16.dp),
                tint = if (isFavorite) colorScheme.primary else colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun SignalStrengthIndicator(
    signalStrength: Int,
    colorScheme: ColorScheme
) {
    Row(modifier = Modifier.width(24.dp)) {
        repeat(3) { index ->
            val opacity = when {
                signalStrength >= (index + 1) * 33 -> 1f
                else -> 0.2f
            }
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = (4 + index * 2).dp)
                    .background(
                        colorScheme.onSurface.copy(alpha = opacity),
                        RoundedCornerShape(1.dp)
                    )
            )
            if (index < 2) Spacer(modifier = Modifier.width(2.dp))
        }
    }
}
