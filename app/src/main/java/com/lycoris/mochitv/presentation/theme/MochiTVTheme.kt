package com.lycoris.mochitv.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---- Palette ---------------------------------------------------------------
// Deep violet / purple accent suits the anime-streaming aesthetic.
// Swap these for a proper design-system palette before release.

private val Purple80  = Color(0xFFD0BCFF)
private val Purple40  = Color(0xFF6650A4)
private val Violet20  = Color(0xFF1A1A2E)

private val DarkColorScheme = darkColorScheme(
    primary         = Purple80,
    secondary       = Color(0xFFCCC2DC),
    tertiary        = Color(0xFFEFB8C8),
    background      = Violet20,
    surface         = Color(0xFF1E1E30),
    onBackground    = Color(0xFFE6E1E5),
    onSurface       = Color(0xFFE6E1E5),
)

private val LightColorScheme = lightColorScheme(
    primary         = Purple40,
    secondary       = Color(0xFF625B71),
    tertiary        = Color(0xFF7D5260),
)

/**
 * Root Material 3 theme wrapper for MochiTV.
 *
 * @param darkTheme  Follows system dark-mode setting by default; override for previews.
 */
@Composable
fun MochiTVTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
