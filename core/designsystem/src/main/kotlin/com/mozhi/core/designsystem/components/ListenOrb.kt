package com.mozhi.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mozhi.core.designsystem.theme.LotusCoral
import com.mozhi.core.designsystem.theme.MonsoonTeal
import com.mozhi.core.designsystem.theme.NightInk
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ListenOrb(
    listening: Boolean,
    audioLevel: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
) {
    val level by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = tween(90),
        label = "level",
    )
    val infinite = rememberInfiniteTransition(label = "orb")
    val ring by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (listening) 1600 else 4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring",
    )
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (listening) 2400 else 9000)),
        label = "spin",
    )

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            if (listening) {
                val outer = radius * (0.62f + 0.28f * ring + 0.12f * level)
                drawCircle(
                    color = MonsoonTeal.copy(alpha = (1f - ring) * 0.45f),
                    radius = outer,
                    center = center,
                    style = Stroke(width = 6f + 18f * level),
                )
                drawCircle(
                    color = LotusCoral.copy(alpha = 0.18f + 0.25f * level),
                    radius = radius * (0.48f + 0.18f * level),
                    center = center,
                )
                val bars = 28
                for (i in 0 until bars) {
                    val angle = Math.toRadians((spin + i * (360f / bars)).toDouble())
                    val mag = 0.35f + 0.45f * level * ((i % 5) / 5f + 0.3f)
                    val inner = radius * 0.34f
                    val outerBar = radius * mag
                    val start = Offset(
                        center.x + inner * cos(angle).toFloat(),
                        center.y + inner * sin(angle).toFloat(),
                    )
                    val end = Offset(
                        center.x + outerBar * cos(angle).toFloat(),
                        center.y + outerBar * sin(angle).toFloat(),
                    )
                    drawLine(
                        color = MonsoonTeal.copy(alpha = 0.55f + 0.4f * level),
                        start = start,
                        end = end,
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }
            } else {
                drawCircle(
                    color = MonsoonTeal.copy(alpha = 0.12f),
                    radius = radius * 0.62f,
                    center = center,
                    style = Stroke(width = 8f),
                )
            }
        }
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier
                .size(92.dp)
                .zIndex(1f),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (listening) LotusCoral else MonsoonTeal,
                contentColor = NightInk,
            ),
        ) {
            Icon(
                imageVector = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (listening) "Stop listening" else "Start listening",
                modifier = Modifier.size(40.dp),
            )
        }
    }
}
