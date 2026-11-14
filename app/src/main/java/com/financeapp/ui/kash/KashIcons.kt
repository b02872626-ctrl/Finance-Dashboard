package com.financeapp.ui.kash

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background

/**
 * The Kash stroke icons (the 23 from the handoff plus [Info]) (24px grid, round caps and joins). Path data is
 * copied verbatim from the SVG files in design/kash-ui-handoff/icons by a script.
 * Direction is drawn: [In], [Out] and [Transfer] are the only money arrows.
 */
enum class KashIcon(vararg val paths: String) {
    Back("M15 6l-6 6 6 6"),
    ChevronRight("M9 6l6 6-6 6"),
    ChevronDown("M6 9l6 6 6-6"),
    Plus("M12 5v14M5 12h14"),
    /** Settings → Appearance: a circle split down the middle. */
    Appearance("M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M12 3v18"),
    /** A gear: toothed outline around a ring (was a ring with rays, which read as a sun). */
    Settings(
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z",
    ),
    Card("M5 5h14a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M2 10h20"),
    History("M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M12 7v5l3 2"),
    /** The Review tab: an in-tray. */
    Review("M3 13h5l2 3h4l2-3h5", "M5 5h14l2 8v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-6z"),
    Chart("M7 19V5", "M12 19v-7", "M17 19V9"),
    Home("M3 11l9-8 9 8v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"),
    Export("M12 3v12M7 10l5 5 5-5M4 20h16"),
    Calendar("M6 5h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M3 10h18M8 3v4M16 3v4"),
    Search("M18 11a7 7 0 1 1-14 0 7 7 0 0 1 14 0z", "M20 20l-3.5-3.5"),
    Share("M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7M12 15V3M7 8l5-5 5 5"),
    Sms("M4 5h16v11H8l-4 4z"),
    Lock("M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V7a4 4 0 0 1 8 0v4"),
    In("M17 7L7 17M15 17H7V9"),
    Out("M7 17L17 7M9 7h8v8"),
    Transfer("M7 10h10l-3-3M17 14H7l3 3"),
    External("M14 4h6v6M20 4l-9 9M19 13v6a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h6"),
    Pause("M10 4v16M14 4v16"),
    /** Added for hiding an account (Eye with a slash). */
    EyeOff("M3 3l18 18", "M10.6 5.1A10 10 0 0 1 12 5c4 0 8 3 10 7a13 13 0 0 1-2.6 3.6", "M6.6 6.6C4.6 7.9 3 9.8 2 12c2 4 6 7 10 7 1.9 0 3.7-.6 5.3-1.6", "M9.9 9.9a3 3 0 0 0 4.2 4.2"),
    Eye("M2 12c2-4 6-7 10-7s8 3 10 7c-2 4-6 7-10 7S4 16 2 12z", "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"),
    Check("M5 12.5l4.5 4.5L19 7.5"),
    Backspace("M9 5h11a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H9l-6-7z", "M12 9.5l5 5M17 9.5l-5 5"),
    /** Added for the PIN and form error lines (not in the original 23). */
    Info("M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M12 7.5v5.5M12 16.5v.01"),
    // Goals, from design/goals-handoff (goals-bundle.js). Same 24 grid, 2px round stroke.
    Target("M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0z", "M13 12a1 1 0 1 1-2 0 1 1 0 0 1 2 0z"),
    More("M6.6 12a1.1 1.1 0 1 1-2.2 0 1.1 1.1 0 0 1 2.2 0z", "M13.1 12a1.1 1.1 0 1 1-2.2 0 1.1 1.1 0 0 1 2.2 0z", "M19.6 12a1.1 1.1 0 1 1-2.2 0 1.1 1.1 0 0 1 2.2 0z"),
    Edit("M4 20h4L19 9l-4-4L4 16z", "M13 7l4 4"),
    Archive("M3 5h18v4H3z", "M5 9v10a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9", "M10 13h4"),
    Trash("M4 7h16", "M10 11v6M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3"),
    Shield("M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z"),
    Laptop("M5 5h14a1 1 0 0 1 1 1v10H4V6a1 1 0 0 1 1-1z", "M2 19h20"),
    Phone("M8 3h8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M11 18h2"),
    School("M2 9l10-5 10 5-10 5z", "M6 11v5c2 2 10 2 12 0v-5", "M22 9v5"),
    Rings("M14 13.5a5 5 0 1 1-10 0 5 5 0 0 1 10 0z", "M20 13.5a5 5 0 1 1-10 0 5 5 0 0 1 10 0z", "M10 5l2-2 2 2-2 2z"),
    Plane("M21 3L3 10.5l7 3 3 7.5z", "M10 13.5L21 3"),
    Car("M4 16v-4l2.5-5h11L20 12v4a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z", "M4 12h16", "M7 17v2.5M17 17v2.5"),
    People("M12 8a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6", "M16 5a3 3 0 0 1 0 6", "M18 14.5c1.8.8 3 2.7 3 5.5"),
    Gift("M4 11h16v9a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z", "M3 7h18v4H3z", "M12 7v14", "M12 7c-1.5-3.5-5-4-5-1.5C7 7 12 7 12 7zM12 7c1.5-3.5 5-4 5-1.5C17 7 12 7 12 7z"),
    Repeat("M17 2l3 3-3 3", "M4 11V9a4 4 0 0 1 4-4h12", "M7 22l-3-3 3-3", "M20 13v2a4 4 0 0 1-4 4H4"),
}

private val cache = HashMap<Pair<KashIcon, Float>, ImageVector>()

/** The icon as a vector with the given stroke width (2 normally, 2.2 for row arrows). */
fun KashIcon.vector(strokeWidth: Float = 2f): ImageVector = cache.getOrPut(this to strokeWidth) {
    ImageVector.Builder(
        name = "Kash.$name",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

/**
 * Kash.Icon. Decorative by default: the button or row around it carries the
 * accessible name. Sizes: 20 in buttons, 22 in the tab bar, 17 in row wells.
 */
@Composable
fun KashIconView(
    icon: KashIcon,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.Unspecified,
    strokeWidth: Float = 2f,
    contentDescription: String? = null,
) {
    val vector = remember(icon, strokeWidth) { icon.vector(strokeWidth) }
    Icon(
        imageVector = vector,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = if (tint == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(name = "Kash icons", widthDp = 360)
@Composable
private fun KashIconsPreview() = KashTheme {
    FlowRow(
        modifier = Modifier.background(KashColors.Surface).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        KashIcon.entries.forEach { KashIconView(it, size = 24.dp, tint = KashColors.Ink) }
    }
}
