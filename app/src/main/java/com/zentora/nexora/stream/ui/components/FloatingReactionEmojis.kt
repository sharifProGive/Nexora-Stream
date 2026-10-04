/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.UUID

data class FloatingEmoji(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val xOffsetRatio: Float
)

/**
 * Feature 25: Floating live reaction emojis during playback.
 */
@Composable
fun FloatingReactionContainer(
    modifier: Modifier = Modifier,
    onReactionSent: (String) -> Unit = {}
) {
    var floatingList by remember { mutableStateOf(listOf<FloatingEmoji>()) }

    Box(modifier = modifier.fillMaxSize()) {
        // Floating emoji renderers
        floatingList.forEach { item ->
            key(item.id) {
                FloatingItem(
                    emoji = item.emoji,
                    xOffsetRatio = item.xOffsetRatio,
                    onAnimationEnd = {
                        floatingList = floatingList.filter { it.id != item.id }
                    }
                )
            }
        }

        // Reaction Bar Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val emojiOptions = listOf("🔥", "🚀", "❤️", "⚡", "👏")
            emojiOptions.forEach { em ->
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .size(34.dp)
                        .clickable {
                            val newX = (0.6f + (Math.random() * 0.35f)).toFloat()
                            floatingList = floatingList + FloatingEmoji(emoji = em, xOffsetRatio = newX)
                            onReactionSent(em)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = em, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.FloatingItem(
    emoji: String,
    xOffsetRatio: Float,
    onAnimationEnd: () -> Unit
) {
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
        )
        onAnimationEnd()
    }

    val progress = animProgress.value
    val yOffset = -progress * 260f
    val alpha = (1f - progress).coerceIn(0f, 1f)
    val scale = 0.8f + (progress * 0.6f)

    Text(
        text = emoji,
        fontSize = 24.sp,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = (xOffsetRatio * 300).dp)
            .graphicsLayer {
                translationY = yOffset
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
    )
}
