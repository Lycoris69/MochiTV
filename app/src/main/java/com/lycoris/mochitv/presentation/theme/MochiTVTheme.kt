package com.lycoris.mochitv.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lycoris.mochitv.domain.model.AccentColor
import com.lycoris.mochitv.domain.model.AppSettings
import com.lycoris.mochitv.domain.model.ThemeMode

// ---------------------------------------------------------------------------
// Colour scheme builder
// ---------------------------------------------------------------------------

private val Violet20 = Color(0xFF1A1A2E)

private fun buildColorScheme(darkTheme: Boolean, accent: AccentColor) =
    if (darkTheme) {
        darkColorScheme(
            primary         = accent.darkPrimary,
            secondary       = Color(0xFFCCC2DC),
            tertiary        = Color(0xFFEFB8C8),
            background      = Violet20,
            surface         = Color(0xFF1E1E30),
            onBackground    = Color(0xFFE6E1E5),
            onSurface       = Color(0xFFE6E1E5),
        )
    } else {
        lightColorScheme(
            primary   = accent.lightPrimary,
            secondary = Color(0xFF625B71),
            tertiary  = Color(0xFF7D5260),
        )
    }

// ---------------------------------------------------------------------------
// Theme composable
// ---------------------------------------------------------------------------

/**
 * Root Material 3 theme wrapper for MochiTV.
 *
 * @param settings  Live [AppSettings] from [AppSettingsRepository]; controls
 *                  theme mode, accent colour, font family, and font scale.
 *                  Defaults to [AppSettings] for previews / tests.
 */
@Composable
fun MochiTVTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = buildColorScheme(darkTheme, settings.accentColor)
    val typography  = buildTypography(settings.appFont)

    // Apply font scale via Density — affects all sp-sized text without touching
    // individual composables.
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density   = baseDensity.density,
        fontScale = settings.fontScale.scale,
    )

    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = typography,
            content     = content,
        )
    }
}
