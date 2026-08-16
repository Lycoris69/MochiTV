package com.lycoris.mochitv.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lycoris.mochitv.R
import com.lycoris.mochitv.domain.model.AppFont

// Nunito variable font — single TTF covers all weights via the wght axis (API 26+).
val NunitoFamily = FontFamily(
    Font(R.font.nunito, FontWeight.Normal),
    Font(R.font.nunito, FontWeight.Medium),
    Font(R.font.nunito, FontWeight.SemiBold),
    Font(R.font.nunito, FontWeight.Bold),
)

/** Builds the full [Typography] for the given [font] setting. */
fun buildTypography(font: AppFont): Typography {
    val family = when (font) {
        AppFont.NUNITO -> NunitoFamily
        AppFont.SYSTEM -> FontFamily.Default
    }
    return Typography(
        displayLarge   = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold,     fontSize = 57.sp, lineHeight = 64.sp),
        displayMedium  = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold,     fontSize = 45.sp, lineHeight = 52.sp),
        displaySmall   = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold,     fontSize = 36.sp, lineHeight = 44.sp),
        headlineLarge  = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
        headlineSmall  = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
        titleLarge     = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold,     fontSize = 20.sp, lineHeight = 26.sp),
        titleMedium    = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        titleSmall     = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge      = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium     = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall      = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal,   fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge     = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium    = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall     = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp),
    )
}

// Convenience default used where no runtime setting is available (e.g. previews).
val MochiTypography: Typography get() = buildTypography(AppFont.NUNITO)
