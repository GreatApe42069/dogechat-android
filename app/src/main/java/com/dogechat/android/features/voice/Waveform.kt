package com.dogechat.android.features.voice

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

/**
 * Waveform component for audio playback visualization with seek functionality
 */
@Composable
fun Waveform(
    waveformData: List<Float>,
    progress: Float = 0f,
    isPlaying: Boolean = false,
    onSeek: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
    playedColor: Color = MaterialTheme.colorScheme.primary,
    unplayedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    barWidth: Float = 2f,
    barSpacing: Float = 1f,
    maxBarHeight: Float = 30f
) {
    var animatedProgress by remember { mutableStateOf(0f) }
    
    // Animate progress changes
    val progressAnimation = animateFloatAsState(
        targetValue = progress,
        animationSpec = if (isPlaying) {
            tween(durationMillis = 100, easing = LinearEasing)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        },
        label = "progress"
    )
    
    LaunchedEffect(progressAnimation.value) {
        animatedProgress = progressAnimation.value
    }
    
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clipToBounds()
            .then(
                if (onSeek != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val seekProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek(seekProgress)
                        }
                    }
                } else Modifier
            )
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerY = canvasHeight / 2f
        
        if (waveformData.isEmpty()) {
            // Draw placeholder bars when no data
            val barCount = (canvasWidth / (barWidth + barSpacing)).toInt()
            repeat(barCount) { index ->
                val x = index * (barWidth + barSpacing) + barWidth / 2f
                val height = maxBarHeight * 0.2f
                
                drawLine(
                    color = unplayedColor,
                    start = Offset(x, centerY - height / 2f),
                    end = Offset(x, centerY + height / 2f),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }
            return@Canvas
        }
        
        // Calculate bar dimensions
        val barCount = waveformData.size
        val totalBarWidth = barCount * barWidth + (barCount - 1) * barSpacing
        val scale = canvasWidth / totalBarWidth
        val scaledBarWidth = barWidth * scale
        val scaledBarSpacing = barSpacing * scale
        
        // Draw waveform bars
        waveformData.forEachIndexed { index, amplitude ->
            val x = index * (scaledBarWidth + scaledBarSpacing) + scaledBarWidth / 2f
            val normalizedAmplitude = amplitude.coerceIn(0f, 1f)
            val barHeight = normalizedAmplitude * maxBarHeight
            
            // Determine color based on progress
            val barProgress = index.toFloat() / (barCount - 1)
            val color = if (barProgress <= animatedProgress) playedColor else unplayedColor
            
            // Add slight animation to played bars
            val animatedHeight = if (barProgress <= animatedProgress && isPlaying) {
                barHeight + sin((System.currentTimeMillis() / 100f + index * 0.2f)) * 1f
            } else {
                barHeight
            }
            
            drawLine(
                color = color,
                start = Offset(x, centerY - animatedHeight / 2f),
                end = Offset(x, centerY + animatedHeight / 2f),
                strokeWidth = scaledBarWidth,
                cap = StrokeCap.Round
            )
        }
        
        // Draw progress indicator line
        if (onSeek != null && animatedProgress > 0f) {
            val progressX = canvasWidth * animatedProgress
            drawLine(
                color = playedColor,
                start = Offset(progressX, 0f),
                end = Offset(progressX, canvasHeight),
                strokeWidth = 2f,
                alpha = 0.7f
            )
        }
    }
}

/**
 * Generate sample waveform data from audio amplitude values
 */
fun generateWaveformData(amplitudes: List<Int>, sampleCount: Int = 50): List<Float> {
    if (amplitudes.isEmpty()) return emptyList()
    
    val chunkSize = amplitudes.size / sampleCount
    if (chunkSize <= 0) return amplitudes.map { it / 32767f }
    
    return (0 until sampleCount).map { index ->
        val start = index * chunkSize
        val end = minOf(start + chunkSize, amplitudes.size)
        val chunk = amplitudes.subList(start, end)
        
        // Calculate RMS (Root Mean Square) for better representation
        val rms = sqrt(chunk.map { it * it }.average()).toFloat()
        (rms / 32767f).coerceIn(0f, 1f)
    }
}

/**
 * Generate random waveform data for testing/placeholder
 */
fun generateRandomWaveform(sampleCount: Int = 50): List<Float> {
    return (0 until sampleCount).map { 
        (sin(it * 0.3) + cos(it * 0.7) + random()).toFloat().absoluteValue.coerceIn(0f, 1f) 
    }
}

private fun random(): Double = Math.random() * 0.5 - 0.25