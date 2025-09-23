package com.dogechat.android.features.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Voice visualizer component for real-time amplitude display during recording
 */
@Composable
fun VoiceVisualizer(
    amplitude: Int,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 30,
    maxBarHeight: Float = 40f
) {
    val amplitudeHistory = remember { mutableListOf<Float>() }
    
    // Update amplitude history
    LaunchedEffect(amplitude, isRecording) {
        if (isRecording) {
            val normalizedAmplitude = amplitude.toFloat() / 32767f // Normalize to 0-1
            amplitudeHistory.add(normalizedAmplitude)
            
            // Keep only the last barCount values
            if (amplitudeHistory.size > barCount) {
                amplitudeHistory.removeAt(0)
            }
        } else {
            // Gradually fade out when not recording
            if (amplitudeHistory.isNotEmpty()) {
                for (i in amplitudeHistory.indices) {
                    amplitudeHistory[i] = amplitudeHistory[i] * 0.95f
                }
                // Remove very small values
                amplitudeHistory.removeAll { it < 0.01f }
            }
        }
    }
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = if (barCount > 0) canvasWidth / barCount else 0f
        val centerY = canvasHeight / 2f
        
        // Draw amplitude bars
        amplitudeHistory.forEachIndexed { index, normalizedAmplitude ->
            val x = index * barWidth + barWidth / 2f
            val barHeight = normalizedAmplitude * maxBarHeight
            
            // Add some animation with sin wave
            val animatedHeight = if (isRecording) {
                barHeight + sin((System.currentTimeMillis() / 100f + index * 0.5f)) * 2f
            } else {
                barHeight
            }
            
            drawLine(
                color = color,
                start = Offset(x, centerY - animatedHeight / 2f),
                end = Offset(x, centerY + animatedHeight / 2f),
                strokeWidth = barWidth * 0.8f,
                cap = StrokeCap.Round
            )
        }
        
        // Fill remaining bars with baseline when history is short
        if (amplitudeHistory.size < barCount) {
            val remainingBars = barCount - amplitudeHistory.size
            repeat(remainingBars) { index ->
                val x = (amplitudeHistory.size + index) * barWidth + barWidth / 2f
                val baselineHeight = if (isRecording) 3f else 1f
                
                drawLine(
                    color = color.copy(alpha = 0.3f),
                    start = Offset(x, centerY - baselineHeight / 2f),
                    end = Offset(x, centerY + baselineHeight / 2f),
                    strokeWidth = barWidth * 0.8f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}