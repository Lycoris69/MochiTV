package com.lycoris.mochitv.domain.model

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Enums — one per tuneable dimension
// ---------------------------------------------------------------------------

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/**
 * Preset accent colours.  Each entry carries its M3 primary colour token in
 * both dark and light variants so [MochiTVTheme] can build the right scheme
 * without needing a full colour-scheme definition per accent.
 */
enum class AccentColor(
    val label: String,
    val darkPrimary: Color,
    val lightPrimary: Color,
) {
    VIOLET("Violet", Color(0xFFD0BCFF), Color(0xFF6650A4)),
    BLUE  ("Blue",   Color(0xFF90CAF9), Color(0xFF1565C0)),
    RED   ("Red",    Color(0xFFEF9A9A), Color(0xFFC62828)),
    GREEN ("Green",  Color(0xFFA5D6A7), Color(0xFF2E7D32)),
    ORANGE("Orange", Color(0xFFFFCC80), Color(0xFFE65100)),
    PINK  ("Pink",   Color(0xFFF48FB1), Color(0xFFAD1457)),
}

enum class AppFont(val label: String) {
    NUNITO("Nunito"),
    SYSTEM("System"),
}

enum class FontScale(val label: String, val scale: Float) {
    SMALL      ("Small",  0.85f),
    NORMAL     ("Normal", 1.00f),
    LARGE      ("Large",  1.15f),
    EXTRA_LARGE("XL",     1.30f),
}

// ---------------------------------------------------------------------------
// Aggregate settings model
// ---------------------------------------------------------------------------

data class AppSettings(
    val themeMode:   ThemeMode   = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.VIOLET,
    val appFont:     AppFont     = AppFont.NUNITO,
    val fontScale:   FontScale   = FontScale.NORMAL,
)
