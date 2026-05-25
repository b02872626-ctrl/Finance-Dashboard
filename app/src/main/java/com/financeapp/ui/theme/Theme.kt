package com.financeapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val FinanceColorScheme = darkColorScheme(
    primary             = CoralPrimary,       // CTA buttons, key data emphasis
    onPrimary           = PureWhite,
    primaryContainer    = CoralSurface,
    onPrimaryContainer  = CoralPrimary,
    secondary           = AppBlue,            // brand accent (teal headlines, links)
    onSecondary         = AppBackground,
    secondaryContainer  = AppBlueSurface,
    onSecondaryContainer = AppBlue,
    tertiary            = AppBlue,
    onTertiary          = AppBackground,
    background          = AppBackground,
    onBackground        = CharcoalText,
    surface             = AppSurface,
    onSurface           = CharcoalText,
    surfaceVariant      = AppSurfaceSecondary,
    onSurfaceVariant    = SlateGray,
    error               = CoralPrimary,
    onError             = PureWhite,
    outline             = BorderStrong,
    outlineVariant      = BorderLight
)

@Composable
fun FinanceAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FinanceColorScheme,
        typography = FinanceTypography,
        content = content
    )
}
