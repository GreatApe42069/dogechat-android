package com.dogechat.android.features.file

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * File utility functions for dogechat media transfers
 * Handles file operations, MIME type detection, and file management
 */
object FileUtils {
    private const val TAG = "FileUtils"
    
    // Supported file types
    val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp")
    val SUPPORTED_AUDIO_TYPES = setOf("audio/mp3", "audio/mpeg", "audio/wav", "audio/m4a", "audio/aac", "audio/ogg")
    val SUPPORTED_VIDEO_TYPES = setOf("video/mp4", "video/mpeg", "video/avi", "video/mov", "video/webm")
    
    // File size limits (in bytes)
    const val MAX_FILE_SIZE = 100 * 1024 * 1024 // 100MB
    const val MAX_IMAGE_SIZE = 25 * 1024 * 1024 // 25MB
    const val MAX_AUDIO_SIZE = 50 * 1024 * 1024 // 50MB
    
    /**
     * Get the incoming files directory
     */
    fun getIncomingFilesDir(context: Context): File {
        val incomingDir = File(context.filesDir, "incoming")
        if (!incomingDir.exists()) {
            incomingDir.mkdirs()
        }
        return incomingDir
    }
    
    /**
     * Get the temp files directory
     */
    fun getTempFilesDir(context: Context): File {
        val tempDir = File(context.cacheDir, "temp")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return tempDir
    }
    
    /**
     * Get file extension from MIME type
     */
    fun getExtensionFromMimeType(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "audio/mp3", "audio/mpeg" -> ".mp3"
            "audio/wav" -> ".wav"
            "audio/m4a" -> ".m4a"
            "audio/aac" -> ".aac"
            "audio/ogg" -> ".ogg"
            "video/mp4" -> ".mp4"
            "video/mpeg" -> ".mpeg"
            "video/avi" -> ".avi"
            "video/mov" -> ".mov"
            "video/webm" -> ".webm"
            "application/pdf" -> ".pdf"
            "text/plain" -> ".txt"
            else -> ""
        }
    }
    
    /**
     * Get MIME type from file extension
     */
    fun getMimeTypeFromExtension(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp3" -> "audio/mp3"
            "wav" -> "audio/wav"
            "m4a" -> "audio/m4a"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "mpeg" -> "video/mpeg"
            "avi" -> "video/avi"
            "mov" -> "video/mov"
            "webm" -> "video/webm"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
    
    /**
     * Check if file type is supported
     */
    fun isSupportedFileType(mimeType: String): Boolean {
        return mimeType in SUPPORTED_IMAGE_TYPES || 
               mimeType in SUPPORTED_AUDIO_TYPES || 
               mimeType in SUPPORTED_VIDEO_TYPES ||
               mimeType in setOf("application/pdf", "text/plain", "application/octet-stream")
    }
    
    /**
     * Check if file size is within limits
     */
    fun isFileSizeValid(size: Long, mimeType: String): Boolean {
        return when {
            mimeType in SUPPORTED_IMAGE_TYPES -> size <= MAX_IMAGE_SIZE
            mimeType in SUPPORTED_AUDIO_TYPES -> size <= MAX_AUDIO_SIZE
            else -> size <= MAX_FILE_SIZE
        }
    }
    
    /**
     * Copy file from URI to internal storage
     */
    fun copyFileFromUri(context: Context, uri: Uri, destinationFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file from URI: ${e.message}")
            false
        }
    }
    
    /**
     * Get file name from URI
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "content" -> {
                    if (DocumentsContract.isDocumentUri(context, uri)) {
                        // Document URI
                        val docId = DocumentsContract.getDocumentId(uri)
                        docId.substringAfterLast('/')
                    } else {
                        // MediaStore URI
                        val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                        cursor?.use {
                            if (it.moveToFirst()) {
                                val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                                if (nameIndex >= 0) it.getString(nameIndex) else null
                            } else null
                        }
                    }
                }
                "file" -> {
                    File(uri.path!!).name
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get filename from URI: ${e.message}")
            null
        }
    }
    
    /**
     * Get file size from URI
     */
    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        return try {
            when (uri.scheme) {
                "content" -> {
                    val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                            if (sizeIndex >= 0) it.getLong(sizeIndex) else 0L
                        } else 0L
                    } ?: 0L
                }
                "file" -> {
                    File(uri.path!!).length()
                }
                else -> 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file size from URI: ${e.message}")
            0L
        }
    }
    
    /**
     * Calculate file hash for duplicate detection
     */
    fun calculateFileHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead = inputStream.read(buffer)
                while (bytesRead != -1) {
                    digest.update(buffer, 0, bytesRead)
                    bytesRead = inputStream.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to calculate file hash: ${e.message}")
            null
        }
    }
    
    /**
     * Clean up old temp files
     */
    fun cleanupTempFiles(context: Context, maxAgeMs: Long = 24 * 60 * 60 * 1000) { // 24 hours
        val tempDir = getTempFilesDir(context)
        val currentTime = System.currentTimeMillis()
        
        tempDir.listFiles()?.forEach { file ->
            if (currentTime - file.lastModified() > maxAgeMs) {
                try {
                    file.delete()
                    Log.d(TAG, "Deleted old temp file: ${file.name}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to delete temp file: ${file.name}")
                }
            }
        }
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        val unit = 1024
        if (bytes < unit) return "$bytes B"
        
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = "KMGTPE"[exp - 1]
        val value = bytes / Math.pow(unit.toDouble(), exp.toDouble())
        
        return "%.1f %sB".format(value, pre)
    }
    
    /**
     * Check if file is an image
     */
    fun isImageFile(mimeType: String): Boolean {
        return mimeType in SUPPORTED_IMAGE_TYPES
    }
    
    /**
     * Check if file is audio
     */
    fun isAudioFile(mimeType: String): Boolean {
        return mimeType in SUPPORTED_AUDIO_TYPES
    }
    
    /**
     * Check if file is video
     */
    fun isVideoFile(mimeType: String): Boolean {
        return mimeType in SUPPORTED_VIDEO_TYPES
    }
}