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
    Settings("M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z", "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"),
    Card("M5 5h14a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z", "M2 10h20"),
    History("M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M12 7v5l3 2"),
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
    Eye("M2 12c2-4 6-7 10-7s8 3 10 7c-2 4-6 7-10 7S4 16 2 12z", "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"),
    Check("M5 12.5l4.5 4.5L19 7.5"),
    Backspace("M9 5h11a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H9l-6-7z", "M12 9.5l5 5M17 9.5l-5 5"),
    /** Added for the PIN and form error lines (not in the original 23). */
    Info("M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0z", "M12 7.5v5.5M12 16.5v.01"),
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
