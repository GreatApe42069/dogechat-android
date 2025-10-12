package com.dogechat.android.wallet.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dogechat.android.wallet.viewmodel.WalletViewModel
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Simple success animation for Dogechat wallet operations.
 * Uses WalletViewModel.SuccessAnimationData (message + txHash) from your ViewModel.
 */
@Composable
fun SuccessAnimation(
    animationData: WalletViewModel.SuccessAnimationData,
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    var startExit by remember { mutableStateOf(false) }

    LaunchedEffect(animationData) {
        isVisible = true
        // show for 2s
        delay(2000)
        startExit = true
        delay(400)
        onAnimationComplete()
    }

    val infiniteTransition = rememberInfiniteTransition()
    val iconRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isVisible && !startExit) 6f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isVisible && !startExit) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val contentScale by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 1f else 0f,
        animationSpec = tween(durationMillis = if (startExit) 300 else 500)
    )

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 0.9f else 0f,
        animationSpec = tween(durationMillis = if (startExit) 300 else 500)
    )

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.scale(contentScale)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color(0xFF00C851).copy(alpha = 0.18f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color(0xFF00C851), CircleShape)
                            .scale(iconScale)
                            .graphicsLayer { rotationZ = iconRotation },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = Color.Black,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                    Text(
                        text = "SUCCESS",
                        color = Color(0xFF00C851),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = animationData.message,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 36.dp)
                    )

                    animationData.txHash?.let { hash ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "TX: ${shortHash(hash)}",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FailureAnimation(
    errorMessage: String,
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    var startExit by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        isVisible = true
        delay(3000)
        startExit = true
        delay(400)
        onAnimationComplete()
    }

    val infiniteTransition = rememberInfiniteTransition()
    val iconShake by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isVisible && !startExit) 4f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isVisible && !startExit) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val contentScale by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
    )

    val alpha by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 1f else 0f,
        animationSpec = tween(durationMillis = if (startExit) 300 else 500)
    )

    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isVisible && !startExit) 0.9f else 0f,
        animationSpec = tween(durationMillis = if (startExit) 300 else 500)
    )

    if (alpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.scale(contentScale)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    Box(modifier = Modifier.size(100.dp).background(Color(0xFFFF4444).copy(alpha = 0.18f), CircleShape))
                    Box(modifier = Modifier.size(80.dp).background(Color(0xFFFF4444), CircleShape).scale(iconScale).graphicsLayer { translationX = iconShake }, contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Error", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
                    Text(text = "FAILED", color = Color(0xFFFF4444), fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = errorMessage, color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 36.dp))
                }
            }
        }
    }
}

private fun shortHash(hash: String): String {
    val safe = hash.ifBlank { "-" }
    return if (safe.length <= 12) safe else safe.take(8) + "..." + safe.takeLast(4)
}
