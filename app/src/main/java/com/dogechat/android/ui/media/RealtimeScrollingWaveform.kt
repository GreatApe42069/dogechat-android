package com.dogechat.android.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

private val dogeGold = Color(0xFFFFD700)      // Dogecoin Gold (playback / highlight)
private val BrandAccent = Color(0xFFFFFF00)   // Bright Yellow Accent (recording live bars)
private val JasmineGold = Color(0xFFEBCA66)   // Jasmine Gold base
private val JasmineGoldFaint = Color(0x22EBCA66) // Faint Jasmine (alpha similar to other faint bases)

/**
 * Real-time scrolling waveform for recording: maintains a dense sliding window of bars.
 * Pass in normalized amplitude [0f..1f]; the component handles sampling and drawing.
 */
@Composable
fun RealtimeScrollingWaveform(
    modifier: Modifier = Modifier,
    amplitudeNorm: Float,
    bars: Int = 240,
    barColor: Color = BrandAccent,             // replaced former green (0xFF00FF7F) with Doge Brand Accent
    baseColor: Color = JasmineGoldFaint        // themed base (was 0xFF444444; original param retained for ref but not used below)
) {
    val latestAmp by rememberUpdatedState(amplitudeNorm)
    val samples: SnapshotStateList<Float> = remember {
        mutableStateListOf<Float>().also { list -> repeat(bars) { list.add(0f) } }
    }

    // Append samples on a steady cadence to create a smooth scroll
    LaunchedEffect(bars) {
        while (true) {
            withFrameNanos { _: Long -> }
            val v = latestAmp.coerceIn(0f, 1f)
            samples.add(v)
            val overflow = samples.size - bars
            if (overflow > 0) repeat(overflow) { if (samples.isNotEmpty()) samples.removeAt(0) }
            kotlinx.coroutines.delay(20)
        }
    }

    Canvas(modifier = modifier.fillMaxWidth()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val n = samples.size
        if (n <= 0) return@Canvas
        val stepX = w / n
        val midY = h / 2f
        val stroke = .5f.dp.toPx()

        // Optional faint base to match chat density
        // Draw bars with heavy dynamic range compression: quiet sounds almost at zero, loud sounds still prominent
        for (i in 0 until n) {
            val amp = samples[i].coerceIn(0f, 1f)
            // Use squared amplitude to heavily compress small values while preserving high amplitudes
            // This makes quiet sounds almost invisible but loud sounds still show prominently
            val compressedAmp = amp * amp // amp^2
            val lineH = (compressedAmp * (h * 0.9f)).coerceAtLeast(1f)
            val x = i * stepX + stepX / 2f
            val yTop = midY - lineH / 2f
            val yBot = midY + lineH / 2f
            drawLine(
                color = barColor,
                start = Offset(x, yTop),
                end = Offset(x, yBot),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}