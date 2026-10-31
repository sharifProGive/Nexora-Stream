/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Premium visual layer shared by every screen.
 *
 * - Dark theme: soft cyan and magenta light glows over the obsidian background.
 * - Light theme: soft blue wash over the light background.
 *
 * The glow is drawn behind the content, so it never changes layout or touch handling.
 */
fun Modifier.premiumGlow(): Modifier = composed {
    val base = MaterialTheme.colorScheme.background
    val isDark = base.luminance() < 0.5f
    val primaryGlow = if (isDark) Color(0x3300E5FF) else Color(0x332979FF)
    val secondaryGlow = if (isDark) Color(0x22FF007F) else Color(0x2200B0FF)
    val footerGlow = if (isDark) Color(0x1A2979FF) else Color(0x1A82B1FF)

    this.drawBehind {
        drawRect(color = base)

        val w = size.width
        val h = size.height
        val topRadius = maxOf(w, h * 0.55f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primaryGlow, Color.Transparent),
                center = Offset(w * 0.12f, 0f),
                radius = topRadius
            ),
            radius = topRadius,
            center = Offset(w * 0.12f, 0f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondaryGlow, Color.Transparent),
                center = Offset(w * 0.95f, h * 0.18f),
                radius = w * 0.75f
            ),
            radius = w * 0.75f,
            center = Offset(w * 0.95f, h * 0.18f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(footerGlow, Color.Transparent),
                center = Offset(w * 0.5f, h),
                radius = w * 0.9f
            ),
            radius = w * 0.9f,
            center = Offset(w * 0.5f, h)
        )
    }
}

/** Full-screen container that paints the premium glow behind its content. */
@Composable
fun PremiumScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .premiumGlow(),
        content = content
    )
}

/** Thin gradient border used on premium cards and badges. */
@Composable
fun premiumBorderBrush(): Brush {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) {
        Brush.linearGradient(listOf(Color(0x5500E5FF), Color(0x1AFFFFFF), Color(0x44FF007F)))
    } else {
        Brush.linearGradient(listOf(Color(0x552979FF), Color(0x22000000), Color(0x4400B0FF)))
    }
}

/** Card with a rounded shape, soft surface colour and a gradient hairline border. */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val clickableModifier = if (onClick != null) {
        modifier.clip(shape).clickable(onClick = onClick)
    } else {
        modifier
    }
    Surface(
        modifier = clickableModifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, premiumBorderBrush()),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(content = content)
    }
}

/** Round icon badge with a tinted glow ring, used in front of list rows and section headers. */
@Composable
fun PremiumIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = NexoraCyanGlow,
    badgeSize: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.28f), tint.copy(alpha = 0.08f))
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
