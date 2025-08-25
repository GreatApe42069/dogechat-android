package com.dogechat.android.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dogechat.android.core.ui.utils.singleOrTripleClickable
import com.dogechat.android.geohash.ChannelID
import com.dogechat.android.geohash.GeoPerson

/**
 * ChatHeader.kt - Final merged version
 * Combines upstream improvements with Dogechat branding and logic
 */

// ------------------- REACTIVE FAVORITE STATE -------------------

@Composable
fun isFavoriteReactive(
    peerID: String,
    peerFingerprints: Map<String, String>,
    favoritePeers: Set<String>
): Boolean {
    return remember(peerID, peerFingerprints, favoritePeers) {
        val fingerprint = peerFingerprints[peerID]
        fingerprint != null && favoritePeers.contains(fingerprint)
    }
}

// ------------------- NOISE SESSION ICON -------------------

@Composable
fun NoiseSessionIcon(
    sessionState: String?,
    modifier: Modifier = Modifier
) {
    val (icon, color, contentDescription) = when (sessionState) {
        "uninitialized" -> Triple(
            Icons.Outlined.NoEncryption,
            Color.Gray,
            "Ready for handshake"
        )
        "handshaking" -> Triple(
            Icons.Outlined.Sync,
            Color.Gray,
            "Handshake in progress"
        )
        "established" -> Triple(
            Icons.Filled.Lock,
            Color(0xFFFF9500), // Orange for secure session
            "End-to-end encrypted"
        )
        else -> Triple(
            Icons.Outlined.Warning,
            Color(0xFFFF4444), // Red for error
            "Handshake failed"
        )
    }

    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = color
    )
}

// ------------------- NICKNAME EDITOR -------------------

@Composable
fun NicknameEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    LaunchedEffect(value) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = "@",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.primary.copy(alpha = 0.8f)
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = colorScheme.primary,
                fontFamily = FontFamily.Monospace
            ),
            cursorBrush = SolidColor(colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .widthIn(max = 120.dp)
                .horizontalScroll(scrollState)
        )
    }
}

// ------------------- PEER COUNTER -------------------

@Composable
fun PeerCounter(
    connectedPeers: List<String>,
    joinedChannels: Set<String>,
    hasUnreadChannels: Map<String, Int>,
    hasUnreadPrivateMessages: Set<String>,
    isConnected: Boolean,
    selectedLocationChannel: ChannelID?,
    geohashPeople: List<GeoPerson>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (peopleCount, countColor) = when (selectedLocationChannel) {
        is ChannelID.Location -> {
            val count = geohashPeople.size
            val green = Color(0xFF00C851)
            Pair(count, if (count > 0) green else Color.Gray)
        }
        is ChannelID.Mesh, null -> {
            val count = connectedPeers.size
            val meshBlue = Color(0xFF007AFF)
            Pair(count, if (isConnected && count > 0) meshBlue else Color.Gray)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clickable { onClick() }.padding(end = 8.dp)
    ) {
        if (hasUnreadChannels.values.any { it > 0 }) {
            Text("#", color = Color(0xFF0080FF), fontSize = 16.sp)
            Spacer(modifier = Modifier.width(6.dp))
        }

        if (hasUnreadPrivateMessages.isNotEmpty()) {
            Icon(Icons.Filled.Email, contentDescription = "Unread private messages", tint = Color(0xFFFF9500), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }

        Icon(
            imageVector = Icons.Default.Group,
            contentDescription = if (selectedLocationChannel is ChannelID.Location) "Geohash participants" else "Connected peers",
            modifier = Modifier.size(16.dp),
            tint = countColor
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "$peopleCount",
            color = countColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        if (joinedChannels.isNotEmpty()) {
            Text(
                text = " · ⧉ ${joinedChannels.size}",
                color = if (isConnected) Color(0xFF00C851) else Color.Red,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ------------------- MAIN HEADER LOGIC -------------------

@Composable
fun ChatHeaderContent(
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onSidebarClick: () -> Unit,
    onTripleClick: () -> Unit,
    onShowAppInfo: () -> Unit,
    onLocationChannelsClick: () -> Unit
) {
    when {
        selectedPrivatePeer != null -> {
            val favoritePeers by viewModel.favoritePeers.observeAsState(emptySet())
            val peerFingerprints by viewModel.peerFingerprints.observeAsState(emptyMap())
            val peerSessionStates by viewModel.peerSessionStates.observeAsState(emptyMap())
            val peerNicknames by viewModel.peerNicknames.observeAsState(emptyMap())

            val isFavorite = isFavoriteReactive(selectedPrivatePeer, peerFingerprints, favoritePeers)
            val sessionState = peerSessionStates[selectedPrivatePeer]

            val selectedLocationChannel by viewModel.selectedLocationChannel.observeAsState()
            val geohashPeople by viewModel.geohashPeople.observeAsState(emptyList())

            PrivateChatHeader(
                peerID = selectedPrivatePeer,
                peerNicknames = peerNicknames,
                isFavorite = isFavorite,
                sessionState = sessionState,
                selectedLocationChannel = selectedLocationChannel,
                geohashPeople = geohashPeople,
                onBackClick = onBackClick,
                onToggleFavorite = { viewModel.toggleFavorite(selectedPrivatePeer) }
            )
        }
        currentChannel != null -> {
            ChannelHeader(channel = currentChannel, onBackClick = onBackClick, onSidebarClick = onSidebarClick, onLeaveChannel = { viewModel.leaveChannel(currentChannel) })
        }
        else -> {
            MainHeader(
                nickname = nickname,
                onNicknameChange = viewModel::setNickname,
                onTitleClick = onShowAppInfo,
                onTripleTitleClick = onTripleClick,
                onSidebarClick = onSidebarClick,
                onLocationChannelsClick = onLocationChannelsClick,
                viewModel = viewModel
            )
        }
    }
}

// ------------------- PRIVATE CHAT HEADER -------------------

@Composable
private fun PrivateChatHeader(
    peerID: String,
    peerNicknames: Map<String, String>,
    isFavorite: Boolean,
    sessionState: String?,
    selectedLocationChannel: ChannelID?,
    geohashPeople: List<GeoPerson>,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isNostrDM = peerID.startsWith("nostr_") || peerID.startsWith("nostr:")

    val titleText: String = if (isNostrDM) {
        val geohash = (selectedLocationChannel as? ChannelID.Location)?.channel?.geohash
        val shortId = peerID.removePrefix("nostr_").removePrefix("nostr:")
        val person = geohashPeople.firstOrNull { it.id.startsWith(shortId, ignoreCase = true) }
        val baseName = person?.displayName?.substringBefore('#') ?: peerNicknames[peerID] ?: "unknown"
        val geoPart = geohash?.let { "#$it" } ?: "#geohash"
        "$geoPart/@$baseName"
    } else {
        peerNicknames[peerID] ?: peerID
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBackClick, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = colorScheme.primary)
        }

        Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Text(text = titleText, style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF9500))
            Spacer(modifier = Modifier.width(4.dp))
            if (!isNostrDM) {
                NoiseSessionIcon(sessionState = sessionState, modifier = Modifier.size(14.dp))
            }
        }

        IconButton(onClick = onToggleFavorite, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) Color(0xFFFFD700) else Color.Gray
            )
        }
    }
}

// ------------------- CHANNEL HEADER -------------------

@Composable
private fun ChannelHeader(
    channel: String?,
    onBackClick: () -> Unit,
    onSidebarClick: () -> Unit,
    onLeaveChannel: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colorScheme.surfaceVariant.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colorScheme.primary)
        }

        Text(
            text = "channel: $channel",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFFF9500),
            modifier = Modifier.clickable { onSidebarClick() }
        )

        TextButton(onClick = onLeaveChannel) {
            Text("leave", color = Color.Red)
        }
    }
}

// ------------------- MAIN HEADER -------------------

@Composable
private fun MainHeader(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    onTitleClick: () -> Unit,
    onTripleTitleClick: () -> Unit,
    onSidebarClick: () -> Unit,
    onLocationChannelsClick: () -> Unit,
    viewModel: ChatViewModel
) {
    val connectedPeers by viewModel.connectedPeers.observeAsState(emptyList())
    val joinedChannels by viewModel.joinedChannels.observeAsState(emptySet())
    val hasUnreadChannels by viewModel.unreadChannelMessages.observeAsState(emptyMap())
    val hasUnreadPrivateMessages by viewModel.unreadPrivateMessages.observeAsState(emptySet())
    val isConnected by viewModel.isConnected.observeAsState(false)
    val selectedLocationChannel by viewModel.selectedLocationChannel.observeAsState()
    val geohashPeople by viewModel.geohashPeople.observeAsState(emptyList())

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "dogechat/",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.singleOrTripleClickable(
                    onSingleClick = onTitleClick,
                    onTripleClick = onTripleTitleClick
                )
            )
            Spacer(modifier = Modifier.width(2.dp))
            NicknameEditor(value = nickname, onValueChange = onNicknameChange)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LocationChannelsButton(viewModel = viewModel, onClick = onLocationChannelsClick)
            PeerCounter(
                connectedPeers = connectedPeers.filter { it != viewModel.meshService.myPeerID },
                joinedChannels = joinedChannels,
                hasUnreadChannels = hasUnreadChannels,
                hasUnreadPrivateMessages = hasUnreadPrivateMessages,
                isConnected = isConnected,
                selectedLocationChannel = selectedLocationChannel,
                geohashPeople = geohashPeople,
                onClick = onSidebarClick
            )
        }
    }
}

// ------------------- LOCATION CHANNELS BUTTON -------------------

@Composable
private fun LocationChannelsButton(
    viewModel: ChatViewModel,
    onClick: () -> Unit
) {
    val selectedChannel by viewModel.selectedLocationChannel.observeAsState()
    val teleported by viewModel.isTeleported.observeAsState(false)

    val (badgeText, badgeColor) = when (selectedChannel) {
        is ChannelID.Mesh -> "#mesh" to Color(0xFF007AFF)
        is ChannelID.Location -> {
            val geohash = (selectedChannel as ChannelID.Location).channel.geohash
            "#$geohash" to Color(0xFF00C851)
        }
        null -> "#mesh" to Color(0xFF007AFF)
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = badgeColor),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = badgeText, fontFamily = FontFamily.Monospace, color = badgeColor)
            if (teleported) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(Icons.Default.PinDrop, contentDescription = "Teleported", modifier = Modifier.size(12.dp), tint = badgeColor)
            }
        }
    }
}
