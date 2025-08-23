package com.dogechat.android.model

import android.os.Parcelable
import com.google.gson.GsonBuilder
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

/**
 * Delivery status for messages - exact same as iOS version
 */
sealed class DeliveryStatus : Parcelable {
    @Parcelize
    object Sending : DeliveryStatus()

    @Parcelize
    object Sent : DeliveryStatus()

    @Parcelize
    data class Delivered(val to: String, val at: Date) : DeliveryStatus()

    @Parcelize
    data class Read(val by: String, val at: Date) : DeliveryStatus()

    @Parcelize
    data class Failed(val reason: String) : DeliveryStatus()

    @Parcelize
    data class PartiallyDelivered(val reached: Int, val total: Int) : DeliveryStatus()

    fun getDisplayText(): String {
        return when (this) {
            is Sending -> "Sending..."
            is Sent -> "Sent"
            is Delivered -> "Delivered to ${this.to}"
            is Read -> "Read by ${this.by}"
            is Failed -> "Failed: ${this.reason}"
            is PartiallyDelivered -> "Delivered to ${this.reached}/${this.total}"
        }
    }
}

/**
 * dogechatMessage - 100% compatible with iOS version
 */
@Parcelize
data class dogechatMessage(
    val id: String = UUID.randomUUID().toString().uppercase(),
    val sender: String,
    val content: String,
    val timestamp: Date,
    val isRelay: Boolean = false,
    val originalSender: String? = null,
    val isPrivate: Boolean = false,
    val recipientNickname: String? = null,
    val senderPeerID: String? = null,
    val mentions: List<String>? = null,
    val channel: String? = null,
    val encryptedContent: ByteArray? = null,
    val isEncrypted: Boolean = false,
    val deliveryStatus: DeliveryStatus? = null
) : Parcelable {

    /**
     * Convert message to binary payload format - exactly same as iOS version
     */
    fun toBinaryPayload(): ByteArray? {
        try {
            val buffer = ByteBuffer.allocate(4096).apply { order(ByteOrder.BIG_ENDIAN) }

            // Message format:
            // - Flags: 1 byte (flags for optional fields)
            // - Timestamp: 8 bytes (milliseconds since epoch, big-endian)
            // - ID length: 1 byte + ID data
            // - Sender length: 1 byte + sender data
            // - Content length: 2 bytes + content data (or encrypted content)
            // Optional fields based on flags...

            var flags: UByte = 0u
            if (isRelay) flags = flags or 0x01u
            if (isPrivate) flags = flags or 0x02u
            if (originalSender != null) flags = flags or 0x04u
            if (recipientNickname != null) flags = flags or 0x08u
            if (senderPeerID != null) flags = flags or 0x10u
            if (mentions != null && mentions.isNotEmpty()) flags = flags or 0x20u
            if (channel != null) flags = flags or 0x40u
            if (isEncrypted) flags = flags or 0x80u

            buffer.put(flags.toByte())

            // Timestamp (in milliseconds, 8 bytes big-endian)
            val timestampMillis = timestamp.time
            buffer.putLong(timestampMillis)

            // ID
            val idBytes = id.toByteArray(Charsets.UTF_8)
            buffer.put(minOf(idBytes.size, 255).toByte())
            buffer.put(idBytes.take(255).toByteArray())

            // Sender
            val senderBytes = sender.toByteArray(Charsets.UTF_8)
            buffer.put(minOf(senderBytes.size, 255).toByte())
            buffer.put(senderBytes.take(255).toByteArray())

            // Content or encrypted content
            if (isEncrypted && encryptedContent != null) {
                val length = minOf(encryptedContent.size, 65535)
                buffer.putShort(length.toShort())
                buffer.put(encryptedContent.take(length).toByteArray())
            } else {
                val contentBytes = content.toByteArray(Charsets.UTF_8)
                val length = minOf(contentBytes.size, 65535)
                buffer.putShort(length.toShort())
                buffer.put(contentBytes.take(length).toByteArray())
            }

            // Optional fields
            originalSender?.let { origSender ->
                val origBytes = origSender.toByteArray(Charsets.UTF_8)
                buffer.put(minOf(origBytes.size, 255).toByte())
                buffer.put(origBytes.take(255).toByteArray())
            }

            recipientNickname?.let { recipient ->
                val recipBytes = recipient.toByteArray(Charsets.UTF_8)
                buffer.put(minOf(recipBytes.size, 255).toByte())
                buffer.put(recipBytes.take(255).toByteArray())
            }

            senderPeerID?.let { peerID ->
                val peerBytes = peerID.toByteArray(Charsets.UTF_8)
                buffer.put(minOf(peerBytes.size, 255).toByte())
                buffer.put(peerBytes.take(255).toByteArray())
            }

            mentions?.let { ments ->
                buffer.put(minOf(ments.size, 255).toByte())
                ments.take(255).forEach { mention ->
                    val mentBytes = mention.toByteArray(Charsets.UTF_8)
                    buffer.put(minOf(mentBytes.size, 255).toByte())
                    buffer.put(mentBytes.take(255).toByteArray())
                }
            }

            channel?.let { ch ->
                val chBytes = ch.toByteArray(Charsets.UTF_8)
                buffer.put(minOf(chBytes.size, 255).toByte())
                buffer.put(chBytes.take(255).toByteArray())
            }

            buffer.flip()
            return buffer.array().copyOf(buffer.limit())
        } catch (e: Exception) {
            return null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as dogechatMessage
        
        if (id != other.id) return false
        if (sender != other.sender) return false
        if (content != other.content) return false
        if (timestamp != other.timestamp) return false
        if (isRelay != other.isRelay) return false
        if (originalSender != other.originalSender) return false
        if (isPrivate != other.isPrivate) return false
        if (recipientNickname != other.recipientNickname) return false
        if (senderPeerID != other.senderPeerID) return false
        if (mentions != other.mentions) return false
        if (channel != other.channel) return false
        if (encryptedContent != null) {
            if (other.encryptedContent == null) return false
            if (!encryptedContent.contentEquals(other.encryptedContent)) return false
        } else if (other.encryptedContent != null) return false
        if (isEncrypted != other.isEncrypted) return false
        if (deliveryStatus != other.deliveryStatus) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isRelay.hashCode()
        result = 31 * result + (originalSender?.hashCode() ?: 0)
        result = 31 * result + isPrivate.hashCode()
        result = 31 * result + (recipientNickname?.hashCode() ?: 0)
        result = 31 * result + (senderPeerID?.hashCode() ?: 0)
        result = 31 * result + (mentions?.hashCode() ?: 0)
        result = 31 * result + (channel?.hashCode() ?: 0)
        result = 31 * result + (encryptedContent?.contentHashCode() ?: 0)
        result = 31 * result + isEncrypted.hashCode()
        result = 31 * result + (deliveryStatus?.hashCode() ?: 0)
        return result
    }
}
