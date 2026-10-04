/* © 2026 Zentora CLC. All rights reserved. Platform Core engineered by Zentora. */
package com.zentora.nexora.stream.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NexoraRed,
    onPrimary = Color.White,
    primaryContainer = NexoraRedDark,
    onPrimaryContainer = Color.White,
    secondary = NexoraSparkGold,
    onSecondary = Color.Black,
    tertiary = NexoraZentoraBlue,
    background = NexoraDarkBackground,
    onBackground = NexoraTextPrimaryDark,
    surface = NexoraDarkSurface,
    onSurface = NexoraTextPrimaryDark,
    surfaceVariant = NexoraDarkSurfaceVariant,
    onSurfaceVariant = NexoraTextSecondaryDark,
    outline = NexoraDarkBorder,
    error = NexoraError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = NexoraRed,
    onPrimary = Color.White,
    primaryContainer = NexoraRedLight,
    onPrimaryContainer = Color.White,
    secondary = NexoraSparkGold,
    onSecondary = Color.Black,
    tertiary = NexoraZentoraBlue,
    background = NexoraLightBackground,
    onBackground = NexoraTextPrimaryLight,
    surface = NexoraLightSurface,
    onSurface = NexoraTextPrimaryLight,
    surfaceVariant = NexoraLightSurfaceVariant,
    onSurfaceVariant = NexoraTextSecondaryLight,
    outline = NexoraLightBorder,
    error = NexoraError,
    onError = Color.White
)

@Composable
fun NexoraStreamTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
