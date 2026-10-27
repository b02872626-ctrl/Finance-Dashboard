package com.financeapp.ui.kash

import android.provider.Settings
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.financeapp.R

/**
 * Kash design system ("Slate + sand", light only). Values come straight from
 * design/kash-ui-handoff/design-system/tokens.json — change them there first.
 */
object KashColors {
    val Slate = Color(0xFF3D4859)
    val SlateInk = Color(0xFF2B3340)
    val Sand = Color(0xFFE9DFC6)
    val OnSlate = Color(0xFFFFFFFF)
    val OnSlateMuted = Color(0xFFC6CDD8)
    val GhostFill = Color(0x12FFFFFF)      // white @ 7%
    val GhostLine = Color(0x38FFFFFF)      // white @ 22%
    val ChartBar = Color(0x2EFFFFFF)       // white @ 18%
    val SkeletonOnSlate = Color(0x1FFFFFFF) // white @ 12%
    val Surface = Color(0xFFF4F5F7)
    val SurfaceRaised = Color(0xFFFFFFFF)
    val Well = Color(0xFFE7EAEF)
    val Line = Color(0xFFE1E4E9)
    val LineStrong = Color(0xFFD5D9E0)
    val Handle = Color(0xFFCBD0D8)
    val Ink = Color(0xFF222A36)
    val InkMuted = Color(0xFF5F6877)
    val InkFaint = Color(0xFF7D8594)
    val MoneyIn = Color(0xFF3E7A4A)
    val MoneyOut = Color(0xFFA3412F)
    val MoneyNeutral = InkFaint
    val Focus = Slate
    val FocusOnSlate = Sand
    val BankCbe = Color(0xFF6B2E8E)
    val BankTelebirr = Color(0xFF0F7A5E)
    val BankBoa = Color(0xFF8F6418)
    /** Backdrop behind sheets and dialogs. */
    val Backdrop = SlateInk.copy(alpha = 0.94f)
}

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

private fun style(
    size: Int,
    lineHeight: TextUnit,
    weight: FontWeight,
    tracking: Double = 0.0,
    numeric: Boolean = false,
) = TextStyle(
    fontFamily = Figtree,
    fontSize = size.sp,
    lineHeight = lineHeight,
    fontWeight = weight,
    letterSpacing = tracking.em,
    fontFeatureSettings = if (numeric) "tnum" else null,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Type scale from tokens.json. Figures use tabular numbers. */
object KashType {
    val FigureXl = style(46, (46 * 1.05).sp, FontWeight.SemiBold, -0.035, numeric = true)
    val FigureL = style(40, 44.sp, FontWeight.SemiBold, -0.035, numeric = true)
    val FigureM = style(34, (34 * 1.1).sp, FontWeight.SemiBold, -0.03, numeric = true)
    val FigureUnit = style(22, (22 * 1.1).sp, FontWeight.Medium, -0.01, numeric = true)
    val FigureS = style(16, 20.sp, FontWeight.SemiBold, numeric = true)

    val TitleL = style(32, (32 * 1.12).sp, FontWeight.Bold, -0.035)
    val TitleM = style(24, 30.sp, FontWeight.Bold, -0.03)
    val Wordmark = style(22, 26.sp, FontWeight.Bold, -0.03)
    val TitleS = style(18, 24.sp, FontWeight.SemiBold, -0.02)
    val TitleXs = style(17, 22.sp, FontWeight.SemiBold)

    val BodyL = style(16, 24.sp, FontWeight.Normal)
    val LabelL = style(16, 20.sp, FontWeight.SemiBold)
    val Body = style(15, 20.sp, FontWeight.Medium)
    val BodyStrong = style(15, 20.sp, FontWeight.SemiBold)
    val Label = style(14, 20.sp, FontWeight.Medium)
    val Caption = style(13, 18.sp, FontWeight.Normal)
    val Micro = style(12, 16.sp, FontWeight.Normal)
}

/** Tabular numbers on any style (every amount on screen). */
fun TextStyle.num(): TextStyle = copy(fontFeatureSettings = "tnum")

object KashShapes {
    val Sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val Card = RoundedCornerShape(20.dp)
    val Control = RoundedCornerShape(16.dp)
    val Field = RoundedCornerShape(14.dp)
    val Chip = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

object KashSpace {
    val GapXs = 6.dp
    val GapS = 8.dp
    val GapM = 10.dp
    val GapL = 14.dp
    val GapXl = 22.dp
    val InsetApp = 22.dp
    val InsetOnboarding = 24.dp
    val RowY = 13.dp
    /** Space between the status bar and the first header row. */
    val HeaderTop = 14.dp
}

object KashSize {
    val ButtonH = 56.dp
    val ButtonHCompact = 52.dp
    val IconButton = 44.dp
    val Well = 40.dp
    val ChipH = 38.dp
    val TabH = 52.dp
    val FieldH = 52.dp
    val SearchH = 46.dp
}

private val KashColorScheme = lightColorScheme(
    primary = KashColors.Slate,
    onPrimary = KashColors.OnSlate,
    secondary = KashColors.Sand,
    onSecondary = KashColors.SlateInk,
    background = KashColors.Surface,
    onBackground = KashColors.Ink,
    surface = KashColors.Surface,
    onSurface = KashColors.Ink,
    surfaceVariant = KashColors.Well,
    onSurfaceVariant = KashColors.InkMuted,
    outline = KashColors.Line,
    outlineVariant = KashColors.Line,
    error = KashColors.MoneyOut,
    onError = KashColors.OnSlate,
    scrim = KashColors.SlateInk,
)

private val KashTypography = Typography(
    displayLarge = KashType.FigureXl,
    headlineLarge = KashType.TitleL,
    headlineMedium = KashType.TitleM,
    titleLarge = KashType.TitleS,
    titleMedium = KashType.TitleXs,
    bodyLarge = KashType.BodyL,
    bodyMedium = KashType.Body,
    bodySmall = KashType.Caption,
    labelLarge = KashType.LabelL,
    labelMedium = KashType.Label,
    labelSmall = KashType.Micro,
)

@Composable
fun KashTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KashColorScheme, typography = KashTypography, content = content)
}

/**
 * Kash press feedback: 88% opacity while pressed, no ripple; 40% when disabled.
 */
fun Modifier.kashClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    this
        .alpha(if (!enabled) 0.4f else if (pressed) 0.88f else 1f)
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/** True when the user turned animations off ("remove animations"). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
