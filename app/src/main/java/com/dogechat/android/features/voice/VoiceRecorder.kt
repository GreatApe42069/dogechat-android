package com.dogechat.android.features.voice

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException

/**
 * Voice recording functionality for dogechat audio messages
 * Handles audio recording with amplitude monitoring for waveform generation
 */
class VoiceRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceRecorder"
    }
    
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()
    
    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    enum class RecordingState {
        IDLE,
        RECORDING,
        PAUSED,
        STOPPED
    }
    
    /**
     * Start recording audio
     */
    fun startRecording(): File? {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return null
        }
        
        return try {
            // Create output file
            val recordingsDir = File(context.cacheDir, "recordings")
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }
            
            outputFile = File(recordingsDir, "voice_${System.currentTimeMillis()}.m4a")
            
            // Initialize MediaRecorder
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(64000)
                setOutputFile(outputFile!!.absolutePath)
                
                prepare()
                start()
            }
            
            isRecording = true
            _recordingState.value = RecordingState.RECORDING
            
            Log.i(TAG, "Started recording to: ${outputFile!!.absolutePath}")
            outputFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            stopRecording()
            null
        }
    }
    
    /**
     * Stop recording audio
     */
    fun stopRecording(): File? {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            return null
        }
        
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            isRecording = false
            _recordingState.value = RecordingState.STOPPED
            _amplitude.value = 0
            
            val result = outputFile
            Log.i(TAG, "Stopped recording. File: ${result?.absolutePath}")
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}")
            cleanup()
            null
        }
    }
    
    /**
     * Cancel recording and delete file
     */
    fun cancelRecording() {
        if (!isRecording) {
            Log.w(TAG, "Not currently recording")
            return
        }
        
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            // Delete the output file
            outputFile?.delete()
            outputFile = null
            
            isRecording = false
            _recordingState.value = RecordingState.IDLE
            _amplitude.value = 0
            
            Log.i(TAG, "Cancelled recording")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel recording: ${e.message}")
            cleanup()
        }
    }
    
    /**
     * Get current amplitude for waveform visualization
     */
    fun getCurrentAmplitude(): Int {
        return try {
            if (isRecording && mediaRecorder != null) {
                val amplitude = mediaRecorder!!.maxAmplitude
                _amplitude.value = amplitude
                amplitude
            } else {
                0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get amplitude: ${e.message}")
            0
        }
    }
    
    /**
     * Update recording duration
     */
    fun updateDuration(durationMs: Long) {
        _duration.value = durationMs
    }
    
    /**
     * Clean up resources
     */
    private fun cleanup() {
        try {
            mediaRecorder?.release()
            mediaRecorder = null
            
            outputFile?.delete()
            outputFile = null
            
            isRecording = false
            _recordingState.value = RecordingState.IDLE
            _amplitude.value = 0
            _duration.value = 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        if (isRecording) {
            stopRecording()
        }
        cleanup()
    }
}