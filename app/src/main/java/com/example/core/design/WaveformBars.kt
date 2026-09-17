package com.example.core.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun WaveformBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "waveform_anim")

    val h1 by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isPlaying) 0.9f else 0.2f,
        animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = if (isPlaying) 0.3f else 0.2f,
        animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = if (isPlaying) 1.0f else 0.2f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = if (isPlaying) 0.25f else 0.2f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label = "h4"
    )

    val heights = listOf(h1, h2, h3, h4)

    Canvas(
        modifier = modifier
            .width(24.dp)
            .height(20.dp)
    ) {
        val barWidth = size.width / (heights.size * 2 - 1)
        val cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)

        heights.forEachIndexed { index, fraction ->
            val barHeight = size.height * fraction
            val x = index * barWidth * 2
            val y = size.height - barHeight

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = cornerRadius
            )
        }
    }
}
