package com.dogechat.android.model

import android.content.Context
import android.util.Log
import com.dogechat.android.features.file.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages file sharing operations for dogechat media transfers
 */
class FileSharingManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FileSharingManager"
        private const val DEFAULT_CHUNK_SIZE = 4096 // 4KB chunks
    }
    
    // Active file transfers
    private val activeTransfers = ConcurrentHashMap<String, FileTransfer>()
    private val receivingFiles = ConcurrentHashMap<String, FileReceiver>()
    
    private val _transferStatus = MutableStateFlow<Map<String, TransferStatusInfo>>(emptyMap())
    val transferStatus: StateFlow<Map<String, TransferStatusInfo>> = _transferStatus.asStateFlow()
    
    /**
     * Start sending a file
     */
    fun startFileSend(file: File, chunkSize: Int = DEFAULT_CHUNK_SIZE): String? {
        return try {
            val fileId = UUID.randomUUID().toString()
            val mimeType = FileUtils.getMimeTypeFromExtension(file.name)
            
            // Validate file
            if (!file.exists() || !file.canRead()) {
                Log.e(TAG, "File does not exist or cannot be read: ${file.absolutePath}")
                return null
            }
            
            if (!FileUtils.isSupportedFileType(mimeType)) {
                Log.e(TAG, "Unsupported file type: $mimeType")
                return null
            }
            
            if (!FileUtils.isFileSizeValid(file.length(), mimeType)) {
                Log.e(TAG, "File size exceeds limits: ${file.length()} bytes")
                return null
            }
            
            val totalChunks = DogechatFilePacket.calculateTotalChunks(file.length(), chunkSize)
            val transfer = FileTransfer(
                fileId = fileId,
                file = file,
                mimeType = mimeType,
                chunkSize = chunkSize,
                totalChunks = totalChunks
            )
            
            activeTransfers[fileId] = transfer
            updateTransferStatus(fileId, TransferStatus.STARTING)
            
            Log.i(TAG, "Started file send: ${file.name} (ID: $fileId, ${totalChunks} chunks)")
            fileId
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start file send: ${e.message}")
            null
        }
    }
    
    /**
     * Get next chunk for file transfer
     */
    fun getNextChunk(fileId: String): DogechatFilePacket? {
        val transfer = activeTransfers[fileId] ?: return null
        
        return try {
            if (transfer.currentChunk >= transfer.totalChunks) {
                // Transfer complete
                updateTransferStatus(fileId, TransferStatus.COMPLETED)
                activeTransfers.remove(fileId)
                return null
            }
            
            val file = transfer.file
            val chunkSize = transfer.chunkSize
            val offset = transfer.currentChunk * chunkSize.toLong()
            val remainingBytes = file.length() - offset
            val actualChunkSize = minOf(chunkSize.toLong(), remainingBytes).toInt()
            
            val chunkData = ByteArray(actualChunkSize)
            FileInputStream(file).use { fis ->
                fis.skip(offset)
                fis.read(chunkData)
            }
            
            val packet = DogechatFilePacket(
                fileId = fileId,
                fileName = file.name,
                mimeType = transfer.mimeType,
                totalSize = file.length(),
                chunkIndex = transfer.currentChunk,
                totalChunks = transfer.totalChunks,
                chunkData = chunkData
            )
            
            transfer.currentChunk++
            val progress = (transfer.currentChunk.toFloat() / transfer.totalChunks * 100f).toInt()
            updateTransferStatus(fileId, TransferStatus.SENDING, progress)
            
            packet
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get chunk for file $fileId: ${e.message}")
            updateTransferStatus(fileId, TransferStatus.ERROR, error = e.message)
            activeTransfers.remove(fileId)
            null
        }
    }
    
    /**
     * Handle received file chunk
     */
    fun handleReceivedChunk(packet: DogechatFilePacket): Boolean {
        return try {
            val fileId = packet.fileId
            val receiver = receivingFiles.getOrPut(fileId) {
                FileReceiver(
                    fileId = fileId,
                    fileName = packet.fileName,
                    mimeType = packet.mimeType,
                    totalSize = packet.totalSize,
                    totalChunks = packet.totalChunks,
                    outputFile = File(FileUtils.getIncomingFilesDir(context), packet.fileName)
                )
            }
            
            // Validate chunk
            if (packet.chunkIndex < 0 || packet.chunkIndex >= packet.totalChunks) {
                Log.w(TAG, "Invalid chunk index: ${packet.chunkIndex}")
                return false
            }
            
            // Check if chunk already received
            if (receiver.receivedChunks.contains(packet.chunkIndex)) {
                Log.d(TAG, "Chunk ${packet.chunkIndex} already received for file $fileId")
                return true // Not an error, just duplicate
            }
            
            // Write chunk to file
            val offset = packet.chunkIndex * DogechatFilePacket.calculateChunkSize()
            receiver.writeChunk(packet.chunkIndex, packet.chunkData, offset.toLong())
            
            val progress = (receiver.receivedChunks.size.toFloat() / receiver.totalChunks * 100f).toInt()
            updateTransferStatus(fileId, TransferStatus.RECEIVING, progress)
            
            // Check if file is complete
            if (receiver.receivedChunks.size == receiver.totalChunks) {
                receiver.finalize()
                updateTransferStatus(fileId, TransferStatus.COMPLETED)
                receivingFiles.remove(fileId)
                Log.i(TAG, "File receive completed: ${receiver.fileName}")
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to handle received chunk: ${e.message}")
            updateTransferStatus(packet.fileId, TransferStatus.ERROR, error = e.message)
            receivingFiles.remove(packet.fileId)
            false
        }
    }
    
    /**
     * Cancel file transfer
     */
    fun cancelTransfer(fileId: String) {
        activeTransfers.remove(fileId)
        receivingFiles[fileId]?.cleanup()
        receivingFiles.remove(fileId)
        updateTransferStatus(fileId, TransferStatus.CANCELLED)
    }
    
    /**
     * Get transfer progress
     */
    fun getTransferProgress(fileId: String): Int {
        activeTransfers[fileId]?.let { transfer ->
            return (transfer.currentChunk.toFloat() / transfer.totalChunks * 100f).toInt()
        }
        receivingFiles[fileId]?.let { receiver ->
            return (receiver.receivedChunks.size.toFloat() / receiver.totalChunks * 100f).toInt()
        }
        return 0
    }
    
    private fun updateTransferStatus(fileId: String, status: TransferStatus, progress: Int = 0, error: String? = null) {
        val currentStatuses = _transferStatus.value.toMutableMap()
        currentStatuses[fileId] = TransferStatusInfo(status, progress, error, System.currentTimeMillis())
        _transferStatus.value = currentStatuses
    }
    
    enum class TransferStatus {
        STARTING,
        SENDING,
        RECEIVING,
        COMPLETED,
        CANCELLED,
        ERROR
    }
    
    data class TransferStatusInfo(
        val status: TransferStatus,
        val progress: Int,
        val error: String? = null,
        val timestamp: Long
    )
    
    private data class FileTransfer(
        val fileId: String,
        val file: File,
        val mimeType: String,
        val chunkSize: Int,
        val totalChunks: Int,
        var currentChunk: Int = 0
    )
    
    private class FileReceiver(
        val fileId: String,
        val fileName: String,
        val mimeType: String,
        val totalSize: Long,
        val totalChunks: Int,
        val outputFile: File
    ) {
        val receivedChunks = mutableSetOf<Int>()
        private val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        
        init {
            // Create temp file
            tempFile.createNewFile()
        }
        
        fun writeChunk(chunkIndex: Int, data: ByteArray, offset: Long) {
            FileOutputStream(tempFile, false).use { fos ->
                fos.channel.position(offset)
                fos.write(data)
            }
            receivedChunks.add(chunkIndex)
        }
        
        fun finalize() {
            if (tempFile.exists()) {
                tempFile.renameTo(outputFile)
            }
        }
        
        fun cleanup() {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}