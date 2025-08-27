package com.dogechat.android.model

import com.dogechat.android.protocol.DogechatPacket

/**
 * Represents a routed packet with additional metadata
 * Used for processing and routing packets in the mesh network
 */
data class RoutedPacket(
    val packet: DogechatPacket,
    val peerID: String? = null,           // Who sent it (parsed from packet.senderID)
    val relayAddress: String? = null      // Address it came from (for avoiding loopback)
)
