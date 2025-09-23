package com.dogechat.android.features.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Image utility functions for dogechat media transfers
 * Handles image compression, rotation, and thumbnail generation
 */
object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val MAX_IMAGE_DIMENSION = 2048 // Maximum width/height for compressed images
    private const val JPEG_QUALITY = 85 // JPEG compression quality
    
    /**
     * Compress and resize image from URI
     */
    fun compressImage(context: Context, uri: Uri, maxSizeBytes: Long = 2 * 1024 * 1024): ByteArray? {
        return try {
            // Load image as bitmap
            val bitmap = loadBitmapFromUri(context, uri) ?: return null
            
            // Correct orientation
            val correctedBitmap = correctImageOrientation(context, uri, bitmap)
            
            // Resize if needed
            val resizedBitmap = resizeBitmap(correctedBitmap, MAX_IMAGE_DIMENSION)
            
            // Compress to JPEG
            val outputStream = ByteArrayOutputStream()
            var quality = JPEG_QUALITY
            
            do {
                outputStream.reset()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                quality -= 10
            } while (outputStream.size() > maxSizeBytes && quality > 10)
            
            val result = outputStream.toByteArray()
            
            // Clean up bitmaps
            if (bitmap != correctedBitmap) bitmap.recycle()
            if (correctedBitmap != resizedBitmap) correctedBitmap.recycle()
            resizedBitmap.recycle()
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress image: ${e.message}")
            null
        }
    }
    
    /**
     * Load bitmap from URI with proper scaling
     */
    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // First, get image dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                
                // Calculate sample size to reduce memory usage
                val sampleSize = calculateInSampleSize(options, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                
                // Load the actual bitmap
                context.contentResolver.openInputStream(uri)?.use { secondInputStream ->
                    val finalOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    BitmapFactory.decodeStream(secondInputStream, null, finalOptions)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bitmap from URI: ${e.message}")
            null
        }
    }
    
    /**
     * Calculate appropriate sample size for image loading
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Correct image orientation based on EXIF data
     */
    private fun correctImageOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = inputStream?.let { ExifInterface(it) }
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to correct image orientation: ${e.message}")
            bitmap
        }
    }
    
    /**
     * Rotate bitmap by specified degrees
     */
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply {
            postRotate(degrees)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Resize bitmap while maintaining aspect ratio
     */
    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        
        val ratio = minOf(maxDimension.toFloat() / width, maxDimension.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Generate thumbnail from image file
     */
    fun generateThumbnail(imageFile: File, thumbnailSize: Int = 200): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imageFile.absolutePath, options)
            
            val sampleSize = calculateInSampleSize(options, thumbnailSize, thumbnailSize)
            val finalOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, finalOptions)
            bitmap?.let { resizeBitmap(it, thumbnailSize) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate thumbnail: ${e.message}")
            null
        }
    }
}