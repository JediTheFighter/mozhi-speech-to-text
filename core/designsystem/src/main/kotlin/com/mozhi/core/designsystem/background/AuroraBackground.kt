package com.mozhi.core.designsystem.background

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mozhi.core.designsystem.theme.LotusGold
import com.mozhi.core.designsystem.theme.MonsoonTeal
import com.mozhi.core.designsystem.theme.NightInk
import com.mozhi.core.designsystem.theme.WaveCyan
import com.mozhi.core.designsystem.theme.WaveViolet

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    listening: Boolean = false,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (listening) 8_000 else 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (listening) 0.72f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (listening) 1_400 else 4_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(NightInk)
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(MonsoonTeal.copy(alpha = pulse * 0.45f), Color.Transparent),
                    center = Offset(w * (0.18f + drift * 0.12f), h * 0.22f),
                    radius = w * 0.72f,
                ),
                radius = w * 0.72f,
                center = Offset(w * (0.18f + drift * 0.12f), h * 0.22f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(WaveViolet.copy(alpha = pulse * 0.4f), Color.Transparent),
                    center = Offset(w * (0.86f - drift * 0.1f), h * 0.18f),
                    radius = w * 0.62f,
                ),
                radius = w * 0.62f,
                center = Offset(w * (0.86f - drift * 0.1f), h * 0.18f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(LotusGold.copy(alpha = 0.16f + pulse * 0.08f), Color.Transparent),
                    center = Offset(w * 0.5f, h * (0.78f - drift * 0.04f)),
                    radius = w * 0.85f,
                ),
                radius = w * 0.85f,
                center = Offset(w * 0.5f, h * (0.78f - drift * 0.04f)),
            )
            if (listening) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(WaveCyan.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.72f),
                        radius = w * 0.55f,
                    ),
                    radius = w * 0.55f,
                    center = Offset(w * 0.5f, h * 0.72f),
                )
            }
        }
        content()
    }
}
