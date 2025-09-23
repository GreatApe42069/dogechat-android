package com.dogechat.android.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * File packet structure for dogechat media transfers using TLV (Type-Length-Value) format
 */
data class DogechatFilePacket(
    val fileId: String,
    val fileName: String,
    val mimeType: String,
    val totalSize: Long,
    val chunkIndex: Int,
    val totalChunks: Int,
    val chunkData: ByteArray
) {
    
    companion object {
        // TLV Types (2 bytes each)
        private const val TLV_FILE_ID = 0x0001.toShort()
        private const val TLV_FILE_NAME = 0x0002.toShort()
        private const val TLV_MIME_TYPE = 0x0003.toShort()
        private const val TLV_FILE_SIZE = 0x0004.toShort()
        private const val TLV_CHUNK_INDEX = 0x0005.toShort()
        private const val TLV_TOTAL_CHUNKS = 0x0006.toShort()
        private const val TLV_CHUNK_DATA = 0x0007.toShort()
        
        /**
         * Serialize file packet to byte array using TLV format
         */
        fun serialize(packet: DogechatFilePacket): ByteArray {
            val buffer = ByteBuffer.allocate(8192) // Start with reasonable size
            buffer.order(ByteOrder.BIG_ENDIAN)
            
            // File ID
            writeTLV(buffer, TLV_FILE_ID, packet.fileId.toByteArray(Charsets.UTF_8))
            
            // File Name
            writeTLV(buffer, TLV_FILE_NAME, packet.fileName.toByteArray(Charsets.UTF_8))
            
            // MIME Type
            writeTLV(buffer, TLV_MIME_TYPE, packet.mimeType.toByteArray(Charsets.UTF_8))
            
            // File Size (8 bytes)
            val sizeBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(packet.totalSize).array()
            writeTLV(buffer, TLV_FILE_SIZE, sizeBytes)
            
            // Chunk Index (4 bytes)
            val chunkIndexBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(packet.chunkIndex).array()
            writeTLV(buffer, TLV_CHUNK_INDEX, chunkIndexBytes)
            
            // Total Chunks (4 bytes)
            val totalChunksBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(packet.totalChunks).array()
            writeTLV(buffer, TLV_TOTAL_CHUNKS, totalChunksBytes)
            
            // Chunk Data
            writeTLV(buffer, TLV_CHUNK_DATA, packet.chunkData)
            
            // Return only the used portion of the buffer
            val result = ByteArray(buffer.position())
            buffer.rewind()
            buffer.get(result)
            return result
        }
        
        /**
         * Deserialize byte array to file packet using TLV format
         */
        fun deserialize(data: ByteArray): DogechatFilePacket? {
            return try {
                val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                
                var fileId: String? = null
                var fileName: String? = null
                var mimeType: String? = null
                var totalSize: Long = 0
                var chunkIndex: Int = 0
                var totalChunks: Int = 0
                var chunkData: ByteArray? = null
                
                while (buffer.hasRemaining()) {
                    if (buffer.remaining() < 4) break // Need at least type and length
                    
                    val type = buffer.short
                    val length = buffer.short.toInt() and 0xFFFF
                    
                    if (buffer.remaining() < length) break // Not enough data for value
                    
                    val value = ByteArray(length)
                    buffer.get(value)
                    
                    when (type) {
                        TLV_FILE_ID -> fileId = String(value, Charsets.UTF_8)
                        TLV_FILE_NAME -> fileName = String(value, Charsets.UTF_8)
                        TLV_MIME_TYPE -> mimeType = String(value, Charsets.UTF_8)
                        TLV_FILE_SIZE -> {
                            if (value.size == 8) {
                                totalSize = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).long
                            }
                        }
                        TLV_CHUNK_INDEX -> {
                            if (value.size == 4) {
                                chunkIndex = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).int
                            }
                        }
                        TLV_TOTAL_CHUNKS -> {
                            if (value.size == 4) {
                                totalChunks = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).int
                            }
                        }
                        TLV_CHUNK_DATA -> chunkData = value
                    }
                }
                
                // Validate required fields
                if (fileId != null && fileName != null && mimeType != null && chunkData != null) {
                    DogechatFilePacket(fileId, fileName, mimeType, totalSize, chunkIndex, totalChunks, chunkData)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Write TLV (Type-Length-Value) entry to buffer
         */
        private fun writeTLV(buffer: ByteBuffer, type: Short, value: ByteArray) {
            buffer.putShort(type)
            buffer.putShort(value.size.toShort())
            buffer.put(value)
        }
        
        /**
         * Calculate optimal chunk size based on MTU and overhead
         */
        fun calculateChunkSize(mtu: Int = 500): Int {
            // Reserve space for TLV headers and metadata (approximately 100 bytes)
            return (mtu - 100).coerceAtLeast(100)
        }
        
        /**
         * Calculate total chunks needed for file size
         */
        fun calculateTotalChunks(fileSize: Long, chunkSize: Int): Int {
            return ((fileSize + chunkSize - 1) / chunkSize).toInt()
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as DogechatFilePacket
        
        if (fileId != other.fileId) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (totalSize != other.totalSize) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (!chunkData.contentEquals(other.chunkData)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = fileId.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + totalSize.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + chunkData.contentHashCode()
        return result
    }
}