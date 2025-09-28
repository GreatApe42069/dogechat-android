package com.dogechat.android.wallet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * QR Code generator component using ZXing but computed off the UI thread to avoid freezes.
 *
 * Usage: place inside your UI and provide the text to encode. The generation runs on
 * Dispatchers.Default via produceState so composition is never blocked.
 */
@Composable
fun QRCodeCanvas(
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }.toInt().coerceAtLeast(64)

    // Generate BitMatrix off the UI thread. produceState runs its block in a coroutine.
    val matrix by produceState<BitMatrix?>(initialValue = null, key1 = text, key2 = sizePx) {
        value = try {
            withContext(Dispatchers.Default) {
                QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
            }
        } catch (e: Throwable) {
            // Failed to generate — keep null so UI can show fallback
            null
        }
    }

    Box(modifier = modifier.size(size).background(Color.White)) {
        if (matrix == null) {
            // show a light loading indicator while the QR is generated
            CircularProgressIndicator(modifier = Modifier.size(size * 0.25f))
        } else {
            Canvas(modifier = Modifier.size(size)) {
                val bitMatrix = matrix ?: return@Canvas
                val canvasSize = sizePx.toFloat()
                val cellSize = canvasSize / bitMatrix.width

                // Draw QR cells. Use local vars for performance.
                val w = bitMatrix.width
                val h = bitMatrix.height

                for (y in 0 until h) {
                    val top = y * cellSize
                    for (x in 0 until w) {
                        if (bitMatrix[x, y]) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(x * cellSize, top),
                                size = Size(cellSize, cellSize)
                            )
                        }
                    }
                }
            }
        }
    }
}
