package com.financeapp.ui.kash

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
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

// Colours shared by both themes (never change with the setting).
private val SlateInkColor = Color(0xFF2B3340)
private val SandColor = Color(0xFFE9DFC6)
private val WhiteColor = Color(0xFFFFFFFF)

/**
 * One set of the design-system colours. Light comes from
 * design/kash-ui-handoff/design-system/tokens.json; dark from the Kash
 * canvas's dark.css (the FD_ rows of the approved flow). Change them there
 * first. [accent] is the one fill for selected states, primary buttons and
 * the switch when on: slate on light sheets, sand on dark ones (sand never
 * sits on a light sheet).
 */
data class KashPalette(
    val dark: Boolean,
    val slate: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val well: Color,
    val line: Color,
    val lineStrong: Color,
    val handle: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val moneyIn: Color,
    val moneyOut: Color,
    val moneyNeutral: Color,
    val focus: Color,
    val accent: Color,
    val onAccent: Color,
    val switchKnobOn: Color,
    val switchKnobOff: Color,
)

object KashPalettes {
    val Light = KashPalette(
        dark = false,
        slate = Color(0xFF3D4859),
        surface = Color(0xFFF4F5F7),
        surfaceRaised = WhiteColor,
        well = Color(0xFFE7EAEF),
        line = Color(0xFFE1E4E9),
        lineStrong = Color(0xFFD5D9E0),
        handle = Color(0xFFCBD0D8),
        ink = Color(0xFF222A36),
        inkMuted = Color(0xFF5F6877),
        inkFaint = Color(0xFF7D8594),
        moneyIn = Color(0xFF3E7A4A),
        moneyOut = Color(0xFFA3412F),
        moneyNeutral = Color(0xFF7D8594),
        focus = Color(0xFF3D4859),
        accent = Color(0xFF3D4859),
        onAccent = WhiteColor,
        switchKnobOn = WhiteColor,
        switchKnobOff = WhiteColor,
    )

    /**
     * dark.css, with one lift: ink-muted is 9AA3AF rather than 8A939F so a
     * 15sp muted label on a raised card reads at 5:1 (8A939F gave 4.1:1).
     */
    val Dark = KashPalette(
        dark = true,
        slate = Color(0xFF161B22),
        surface = Color(0xFF1F252E),
        surfaceRaised = Color(0xFF29313C),
        well = Color(0xFF262D37),
        line = Color(0xFF262D37),
        lineStrong = Color(0xFF343D49),
        handle = Color(0xFF4A5361),
        ink = Color(0xFFF1F3F6),
        inkMuted = Color(0xFF9AA3AF),
        inkFaint = Color(0xFF6F7885),
        moneyIn = Color(0xFF7CC48B),
        moneyOut = Color(0xFFF2907A),
        moneyNeutral = Color(0xFF9AA3AF),
        focus = SandColor,
        accent = SandColor,
        onAccent = SlateInkColor,
        switchKnobOn = SlateInkColor,
        switchKnobOff = Color(0xFF9AA3AF),
    )
}

/**
 * Kash design system ("Slate + sand"). The theme-dependent colours read the
 * palette [KashTheme] chose, through snapshot state, so every screen,
 * draw and layout repaints when the setting changes. The constants below
 * the getters are the same in both themes.
 */
object KashColors {
    private var paletteState by mutableStateOf(KashPalettes.Light)

    /** The palette in use; [KashTheme] sets it. */
    val palette: KashPalette get() = paletteState
    val isDark: Boolean get() = paletteState.dark
    internal fun use(p: KashPalette) { if (paletteState !== p) paletteState = p }

    val Slate: Color get() = paletteState.slate
    val Surface: Color get() = paletteState.surface
    val SurfaceRaised: Color get() = paletteState.surfaceRaised
    val Well: Color get() = paletteState.well
    val Line: Color get() = paletteState.line
    val LineStrong: Color get() = paletteState.lineStrong
    val Handle: Color get() = paletteState.handle
    val Ink: Color get() = paletteState.ink
    val InkMuted: Color get() = paletteState.inkMuted
    val InkFaint: Color get() = paletteState.inkFaint
    val MoneyIn: Color get() = paletteState.moneyIn
    val MoneyOut: Color get() = paletteState.moneyOut
    val MoneyNeutral: Color get() = paletteState.moneyNeutral
    val Focus: Color get() = paletteState.focus
    /** Selected states, primary buttons, the switch when on. */
    val Accent: Color get() = paletteState.accent
    val OnAccent: Color get() = paletteState.onAccent
    val SwitchKnobOn: Color get() = paletteState.switchKnobOn
    val SwitchKnobOff: Color get() = paletteState.switchKnobOff

    val SlateInk = SlateInkColor
    val Sand = SandColor
    val OnSlate = WhiteColor
    val OnSlateMuted = Color(0xFFC6CDD8)
    val GhostFill = Color(0x12FFFFFF)      // white @ 7%
    val GhostLine = Color(0x38FFFFFF)      // white @ 22%
    val ChartBar = Color(0x2EFFFFFF)       // white @ 18%
    val SkeletonOnSlate = Color(0x1FFFFFFF) // white @ 12%
    val FocusOnSlate = SandColor
    val BankCbe = Color(0xFF6B2E8E)
    val BankTelebirr = Color(0xFF0F7A5E)
    val BankBoa = Color(0xFF8F6418)
    val SuccessOnSlate = Color(0xFF9BD3A5) // PIN accepted
    /** Goals section: a sand top instead of slate, slate-ink text on it. */
    val GoalsHero = SandColor
    val GoalsInk = SlateInkColor
    val GoalsInkMuted = Color(0xFF5E5A4E)
    val GoalsTrack = Color(0x242B3340)    // slate-ink @ 14%
    val GoalsReached: Color get() = paletteState.moneyIn
    /** Backdrop behind sheets and dialogs. */
    val Backdrop = SlateInkColor.copy(alpha = 0.94f)
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

/** Material's scheme, for the few Material widgets Kash uses (sheets, pull to refresh). */
private fun materialScheme(p: KashPalette) = if (p.dark) darkColorScheme(
    primary = p.accent, onPrimary = p.onAccent,
    secondary = SandColor, onSecondary = SlateInkColor,
    background = p.surface, onBackground = p.ink,
    surface = p.surface, onSurface = p.ink,
    surfaceVariant = p.well, onSurfaceVariant = p.inkMuted,
    outline = p.line, outlineVariant = p.line,
    error = p.moneyOut, onError = SlateInkColor,
    scrim = SlateInkColor,
) else lightColorScheme(
    primary = p.accent, onPrimary = p.onAccent,
    secondary = SandColor, onSecondary = SlateInkColor,
    background = p.surface, onBackground = p.ink,
    surface = p.surface, onSurface = p.ink,
    surfaceVariant = p.well, onSurfaceVariant = p.inkMuted,
    outline = p.line, outlineVariant = p.line,
    error = p.moneyOut, onError = WhiteColor,
    scrim = SlateInkColor,
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

/**
 * [mode] comes from Settings → Appearance (Light, Dark, Match phone). The
 * palette is set before the children compose, so the first frame is right.
 */
@Composable
fun KashTheme(mode: KashThemeMode = KashThemeMode.LIGHT, content: @Composable () -> Unit) {
    val palette = if (mode.isDark(isSystemInDarkTheme())) KashPalettes.Dark else KashPalettes.Light
    KashColors.use(palette)
    MaterialTheme(colorScheme = remember(palette) { materialScheme(palette) }, typography = KashTypography, content = content)
}

/** How a [kashClickable] answers a press. */
enum class KashPress {
    /** Buttons, cards, chips and links: shrink to 97% and spring back. */
    Scale,
    /** Full-width list rows: a faint ink wash behind the row, no shrink. */
    Highlight,
    /** PIN keys: a deeper dip to 92%. */
    Dip,
}

/**
 * Kash press feedback: 88% opacity plus [press] while pressed, no ripple;
 * 40% when disabled. With reduced motion only the opacity and wash remain.
 */
fun Modifier.kashClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    press: KashPress = KashPress.Scale,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val down = pressed && enabled
    val target = when {
        !down || reduced -> 1f
        press == KashPress.Scale -> 0.97f
        press == KashPress.Dip -> 0.92f
        else -> 1f
    }
    // Fast in, a little bounce on the way back out.
    val scale by animateFloatAsState(
        target,
        if (down) spring(stiffness = Spring.StiffnessHigh) else spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val wash by animateFloatAsState(if (down && press == KashPress.Highlight) 0.05f else 0f, tween(120), label = "wash")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .alpha(if (!enabled) 0.4f else if (pressed && press != KashPress.Highlight) 0.88f else 1f)
        .drawBehind { if (wash > 0f) drawRect(KashColors.Ink.copy(alpha = wash)) }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

/**
 * Status/navigation bar icon colours for the screen being shown. Slate tops
 * want light icons ([darkIcons] false); light onboarding pages want dark.
 * [navDarkIcons] follows the sheet: dark icons on the light theme's sheet,
 * light icons on the dark theme's.
 */
@Composable
fun KashSystemBars(darkIcons: Boolean, navDarkIcons: Boolean = !KashColors.isDark) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    androidx.compose.runtime.SideEffect {
        var ctx = view.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val window = (ctx as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = navDarkIcons
        }
    }
}

/** True when the user turned animations off ("remove animations"). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
