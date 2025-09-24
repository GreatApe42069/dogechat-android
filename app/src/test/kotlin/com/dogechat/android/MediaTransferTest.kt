package com.dogechat.android

import com.dogechat.android.model.DogechatFilePacket
import com.dogechat.android.model.DogechatMessage
import com.dogechat.android.model.MessageType
import org.junit.Test
import org.junit.Assert.*
import java.util.*

/**
 * Unit tests for media transfer functionality
 */
class MediaTransferTest {

    @Test
    fun testDogechatFilePacketSerialization() {
        val packet = DogechatFilePacket(
            fileId = "test-file-123",
            fileName = "test.jpg",
            mimeType = "image/jpeg",
            totalSize = 1024L,
            chunkIndex = 0,
            totalChunks = 3,
            chunkData = "test data".toByteArray()
        )

        val serialized = DogechatFilePacket.serialize(packet)
        assertNotNull("Serialization should not return null", serialized)
        assertTrue("Serialized data should not be empty", serialized.isNotEmpty())

        val deserialized = DogechatFilePacket.deserialize(serialized)
        assertNotNull("Deserialization should not return null", deserialized)
        assertEquals("File ID should match", packet.fileId, deserialized!!.fileId)
        assertEquals("File name should match", packet.fileName, deserialized.fileName)
        assertEquals("MIME type should match", packet.mimeType, deserialized.mimeType)
        assertEquals("Total size should match", packet.totalSize, deserialized.totalSize)
        assertEquals("Chunk index should match", packet.chunkIndex, deserialized.chunkIndex)
        assertEquals("Total chunks should match", packet.totalChunks, deserialized.totalChunks)
        assertArrayEquals("Chunk data should match", packet.chunkData, deserialized.chunkData)
    }

    @Test
    fun testDogechatMessageWithMedia() {
        val message = DogechatMessage(
            id = "test-msg-123",
            sender = "test-sender",
            content = "Sent an image",
            timestamp = Date(),
            messageType = MessageType.IMAGE,
            mediaFileName = "photo.jpg",
            mediaMimeType = "image/jpeg",
            mediaFileSize = 2048L,
            mediaFileId = "file-123",
            mediaThumbnail = "thumbnail data".toByteArray()
        )

        val binary = message.toBinaryPayload()
        assertNotNull("Binary payload should not be null", binary)

        val parsed = DogechatMessage.fromBinaryPayload(binary!!)
        assertNotNull("Parsed message should not be null", parsed)
        assertEquals("Message type should match", MessageType.IMAGE, parsed!!.messageType)
        assertEquals("Media filename should match", "photo.jpg", parsed.mediaFileName)
        assertEquals("Media MIME type should match", "image/jpeg", parsed.mediaMimeType)
        assertEquals("Media file size should match", 2048L, parsed.mediaFileSize)
        assertEquals("Media file ID should match", "file-123", parsed.mediaFileId)
        assertArrayEquals("Media thumbnail should match", message.mediaThumbnail, parsed.mediaThumbnail)
    }

    @Test
    fun testChunkCalculation() {
        val chunkSize = DogechatFilePacket.calculateChunkSize(500)
        assertTrue("Chunk size should be reasonable", chunkSize > 0 && chunkSize <= 400)

        val totalChunks = DogechatFilePacket.calculateTotalChunks(1000L, chunkSize)
        assertTrue("Total chunks should be calculated correctly", totalChunks > 0)
        assertTrue("Should need multiple chunks for large file", totalChunks >= 3)
    }
}