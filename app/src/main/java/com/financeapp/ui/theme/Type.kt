package com.financeapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.financeapp.R

private val GoogleSans = FontFamily(
    Font(R.font.google_sans_regular, FontWeight.Normal),
    Font(R.font.google_sans_medium, FontWeight.Medium),
    Font(R.font.google_sans_semibold, FontWeight.SemiBold),
    Font(R.font.google_sans_bold, FontWeight.Bold)
)

val DisplayFontFamily: FontFamily = GoogleSans
val BodyFontFamily: FontFamily = GoogleSans
val MonoFontFamily: FontFamily = GoogleSans

private fun textStyle(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float = 0f
) = TextStyle(
    fontFamily = GoogleSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.em,
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

val FinanceTypography = Typography(
    displayLarge   = textStyle(FontWeight.Bold,     34, 38, -0.02f),
    displayMedium  = textStyle(FontWeight.Bold,     28, 32, -0.02f),
    displaySmall   = textStyle(FontWeight.SemiBold, 24, 28, -0.015f),
    headlineLarge  = textStyle(FontWeight.SemiBold, 22, 26, -0.015f),
    headlineMedium = textStyle(FontWeight.SemiBold, 20, 24, -0.01f),
    headlineSmall  = textStyle(FontWeight.SemiBold, 18, 22, -0.01f),
    titleLarge     = textStyle(FontWeight.SemiBold, 16, 20, -0.005f),
    titleMedium    = textStyle(FontWeight.Medium,   15, 19),
    titleSmall     = textStyle(FontWeight.Medium,   14, 18),
    bodyLarge      = textStyle(FontWeight.Normal,   15, 22),
    bodyMedium     = textStyle(FontWeight.Normal,   14, 20),
    bodySmall      = textStyle(FontWeight.Normal,   12, 17),
    labelLarge     = textStyle(FontWeight.SemiBold, 14, 18),
    labelMedium    = textStyle(FontWeight.Medium,   12, 16, 0.04f),
    labelSmall     = textStyle(FontWeight.Medium,   11, 14, 0.06f)
)
