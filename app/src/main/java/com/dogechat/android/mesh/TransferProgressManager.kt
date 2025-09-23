package com.dogechat.android.mesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages file transfer progress tracking for dogechat media transfers
 */
class TransferProgressManager {
    
    private val _transferProgress = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())
    val transferProgress: StateFlow<Map<String, TransferProgress>> = _transferProgress.asStateFlow()
    
    private val progressMap = ConcurrentHashMap<String, TransferProgress>()
    
    /**
     * Update transfer progress for a file
     */
    fun updateProgress(fileId: String, bytesTransferred: Long, totalBytes: Long, isComplete: Boolean = false) {
        val progress = TransferProgress(
            fileId = fileId,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            percentage = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes * 100f) else 0f,
            isComplete = isComplete,
            timestamp = System.currentTimeMillis()
        )
        
        progressMap[fileId] = progress
        _transferProgress.value = progressMap.toMap()
    }
    
    /**
     * Mark transfer as complete
     */
    fun completeTransfer(fileId: String) {
        progressMap[fileId]?.let { currentProgress ->
            updateProgress(fileId, currentProgress.totalBytes, currentProgress.totalBytes, true)
        }
    }
    
    /**
     * Remove transfer progress
     */
    fun removeProgress(fileId: String) {
        progressMap.remove(fileId)
        _transferProgress.value = progressMap.toMap()
    }
    
    /**
     * Get progress for specific file
     */
    fun getProgress(fileId: String): TransferProgress? {
        return progressMap[fileId]
    }
    
    data class TransferProgress(
        val fileId: String,
        val bytesTransferred: Long,
        val totalBytes: Long,
        val percentage: Float,
        val isComplete: Boolean,
        val timestamp: Long
    )
}