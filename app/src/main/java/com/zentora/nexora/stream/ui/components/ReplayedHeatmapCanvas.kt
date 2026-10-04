/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.zentora.nexora.stream.ui.theme.NexoraRed
import com.zentora.nexora.stream.ui.theme.NexoraSparkGold
import kotlin.math.sin

/**
 * Feature 5: Most Replayed Heatmap Canvas.
 * Draws dynamic engagement peaks along the seekbar based on playback spikes.
 */
@Composable
fun ReplayedHeatmapCanvas(
    modifier: Modifier = Modifier,
    videoDurationMs: Long = 1000L,
    seed: Int = 42
) {
    // Generate normalized points representing engagement density
    val points = remember(videoDurationMs, seed) {
        val count = 25
        List(count) { i ->
            val x = i.toFloat() / (count - 1)
            // Procedural engagement curve with prominent peaks
            val peak1 = kotlin.math.exp(-((x - 0.25f) * (x - 0.25f)) / 0.015f) * 0.9f
            val peak2 = kotlin.math.exp(-((x - 0.70f) * (x - 0.70f)) / 0.025f) * 0.75f
            val base = (sin(x * 12f) * 0.15f) + 0.2f
            (peak1 + peak2 + base).coerceIn(0.1f, 1.0f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
    ) {
        val width = size.width
        val height = size.height
        val step = width / (points.size - 1)

        val fillPath = Path().apply {
            moveTo(0f, height)
            for (i in points.indices) {
                val x = i * step
                val y = height - (points[i] * height * 0.85f)
                if (i == 0) {
                    lineTo(x, y)
                } else {
                    val prevX = (i - 1) * step
                    val prevY = height - (points[i - 1] * height * 0.85f)
                    val controlX = (prevX + x) / 2f
                    cubicTo(controlX, prevY, controlX, y, x, y)
                }
            }
            lineTo(width, height)
            close()
        }

        // Fill with subtle glowing gradient
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    NexoraSparkGold.copy(alpha = 0.5f),
                    NexoraRed.copy(alpha = 0.25f),
                    Color.Transparent
                )
            )
        )

        // Top contour line
        val strokePath = Path().apply {
            for (i in points.indices) {
                val x = i * step
                val y = height - (points[i] * height * 0.85f)
                if (i == 0) moveTo(x, y)
                else {
                    val prevX = (i - 1) * step
                    val prevY = height - (points[i - 1] * height * 0.85f)
                    val controlX = (prevX + x) / 2f
                    cubicTo(controlX, prevY, controlX, y, x, y)
                }
            }
        }

        drawPath(
            path = strokePath,
            color = NexoraSparkGold.copy(alpha = 0.8f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
