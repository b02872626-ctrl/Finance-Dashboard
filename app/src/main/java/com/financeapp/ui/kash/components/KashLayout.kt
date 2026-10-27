package com.financeapp.ui.kash.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSize
import com.financeapp.ui.kash.KashSpace
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.kashClickable

// ─── Screen ──────────────────────────────────────────────────────────────

/**
 * Kash.Screen: the two-layer app screen. [hero] is the slate top (header
 * row, the one figure, its controls); the light sheet below rises over it
 * with a handle and holds [content]. The sheet starts where the hero ends.
 *
 * [expandable]: scrolling the sheet's content up first lifts the sheet over
 * the hero, up to just under the status bar; scrolling back down at the top
 * of the content lowers it again. The handle can be dragged too.
 *
 * [content] gets the bottom padding a scrolling list needs so its last row
 * clears the floating [tabBar] (or the navigation bar on sub-pages).
 */
@Composable
fun KashScreen(
    hero: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    tabBar: (@Composable () -> Unit)? = null,
    handle: Boolean = true,
    heroGap: Dp = KashSpace.GapXl,
    heroBottom: Dp = 26.dp,
    expandable: Boolean = true,
    content: @Composable ColumnScope.(bottomPadding: PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottom = if (tabBar != null) KashTabBarHeight + navBottom else navBottom + 16.dp
    val statusTop = WindowInsets.statusBars.getTop(density)

    // How far (px) the sheet has risen over the hero, and how far it may.
    var risen by rememberSaveable { mutableFloatStateOf(0f) }
    val limit = remember { floatArrayOf(0f) }
    val rise = remember {
        object : NestedScrollConnection {
            // Content moving up: lift the sheet before the list scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || risen >= limit[0]) return Offset.Zero
                val d = minOf(-available.y, limit[0] - risen)
                risen += d
                return Offset(0f, -d)
            }
            // Content already at its top and still pulled down: lower the sheet.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || risen <= 0f) return Offset.Zero
                val d = minOf(available.y, risen)
                risen -= d
                return Offset(0f, d)
            }
        }
    }
    val drag = rememberDraggableState { delta -> risen = (risen - delta).coerceIn(0f, limit[0]) }

    Layout(
        modifier = modifier.fillMaxSize().background(KashColors.Slate),
        content = {
            Column(
                Modifier
                    .fillMaxWidth()
                    // The hero fades as the sheet covers it.
                    .graphicsLayer { alpha = if (limit[0] > 0f) 1f - (risen / limit[0]) * 0.6f else 1f }
                    .statusBarsPadding()
                    .padding(start = KashSpace.InsetApp, end = KashSpace.InsetApp, top = KashSpace.HeaderTop, bottom = heroBottom),
                verticalArrangement = Arrangement.spacedBy(heroGap),
                content = hero,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(KashShapes.Sheet)
                    .background(KashColors.Surface)
                    .then(if (expandable) Modifier.nestedScroll(rise) else Modifier)
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = KashSpace.InsetApp)) {
                    if (handle) {
                        KashHandle(
                            Modifier
                                .then(if (expandable) Modifier.draggable(drag, Orientation.Vertical) else Modifier)
                                .padding(top = 10.dp, bottom = 10.dp)
                        )
                    }
                    content(PaddingValues(bottom = bottom))
                }
                if (tabBar != null) Box(Modifier.align(Alignment.BottomCenter)) { tabBar() }
            }
        },
    ) { measurables, constraints ->
        val heroPlaceable = measurables[0].measure(constraints.copy(minHeight = 0))
        // The sheet can rise to 8dp under the status bar.
        limit[0] = if (expandable) (heroPlaceable.height - statusTop - 8.dp.roundToPx()).coerceAtLeast(0).toFloat() else 0f
        val top = (heroPlaceable.height - risen.coerceIn(0f, limit[0])).roundToInt().coerceAtMost(constraints.maxHeight)
        val sheetPlaceable = measurables[1].measure(
            Constraints.fixed(constraints.maxWidth, (constraints.maxHeight - top).coerceAtLeast(0))
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            heroPlaceable.place(0, 0)
            sheetPlaceable.place(0, top)
        }
    }
}

/** The grab handle at the top of a sheet. */
@Composable
fun KashHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(KashShapes.Pill)
                .background(KashColors.Handle)
                .clearAndSetSemantics { }
        )
    }
}

/**
 * Header row of a sub-page: back on the left, title-xs beside it (or centred
 * with [centerTitle]), up to two utilities on the right.
 */
@Composable
fun KashTopBar(
    title: String?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onSlate: Boolean = true,
    centerTitle: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val color = if (onSlate) KashColors.OnSlate else KashColors.Ink
    Box(modifier.fillMaxWidth().height(KashSize.IconButton)) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (onBack != null) KashIconButton(KashIcon.Back, "Back", onBack, onSlate = onSlate)
            if (!centerTitle) {
                if (leading != null) leading()
                if (title != null) {
                    Text(
                        title, style = KashType.TitleXs, color = color, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() },
                    )
                } else Spacer(Modifier.weight(1f))
            } else Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(KashSpace.GapS), content = actions)
        }
        if (centerTitle && title != null) {
            Row(
                Modifier.align(Alignment.Center).padding(horizontal = 60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leading != null) leading()
                Text(
                    title, style = KashType.TitleXs, color = color, maxLines = 1, textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() },
                )
            }
        }
    }
}

// ─── TabBar ──────────────────────────────────────────────────────────────

enum class KashTab(val label: String, val icon: KashIcon) {
    Home("Home", KashIcon.Home),
    Accounts("Accounts", KashIcon.Card),
    History("History", KashIcon.History),
    Analytics("Analytics", KashIcon.Chart),
}

/** Height of the tab bar above the navigation bar (6 top + 52 tab + 4). */
val KashTabBarHeight: Dp = 62.dp

/**
 * Kash.TabBar: four icon-only tabs; the active one is ink with a 5dp dot.
 * No text under icons. Only on the four tab screens.
 */
@Composable
fun KashTabBar(active: KashTab, onSelect: (KashTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(KashColors.Surface)) {
        KashDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = KashSpace.InsetApp, end = KashSpace.InsetApp, top = 6.dp, bottom = 4.dp)
                .navigationBarsPadding(),
        ) {
            KashTab.entries.forEach { tab ->
                val on = tab == active
                val tint = if (on) KashColors.Ink else KashColors.InkFaint
                Column(
                    Modifier
                        .weight(1f)
                        .height(KashSize.TabH)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(tab) },
                        )
                        .semantics {
                            contentDescription = tab.label
                            selected = on
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                ) {
                    KashIconView(tab.icon, size = 22.dp, tint = tint)
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(KashShapes.Pill)
                            .background(if (on) KashColors.Ink else androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
    }
}

// ─── Sheet ───────────────────────────────────────────────────────────────

/**
 * Kash.Sheet: rises from the bottom over the slate-ink backdrop for choosing
 * something. Close is a chevron-down icon button at the top right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KashSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = KashShapes.Sheet,
        containerColor = KashColors.Surface,
        contentColor = KashColors.Ink,
        tonalElevation = 0.dp,
        scrimColor = KashColors.Backdrop,
        dragHandle = { KashHandle(Modifier.padding(top = 10.dp)) },
    ) {
        KashSheetBody(title = title, onClose = onDismiss, content = content)
    }
}

/** The inside of a sheet (also usable in previews). */
@Composable
fun KashSheetBody(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = KashSpace.InsetApp, end = KashSpace.InsetApp, top = 4.dp, bottom = 28.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = KashType.TitleM, color = KashColors.Ink, modifier = Modifier.weight(1f).semantics { heading() })
            KashIconButton(KashIcon.ChevronDown, "Close", onClose)
        }
        content()
    }
}

// ─── Dialog ──────────────────────────────────────────────────────────────

/**
 * Kash.Dialog: a centred card on the slate-ink backdrop (16dp margins) that
 * asks one question. Buttons stack: main action full width, then a quiet
 * way out. [alert] for destructive questions (use KashButtonVariant.Danger).
 */
@Composable
fun KashDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = false,
        ),
    ) {
        // Our backdrop replaces the window dim.
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .background(KashColors.Backdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (dismissible) onDismiss() },
                )
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            KashDialogCard(title, description, modifier.clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {},
            ), content)
        }
    }
}

/** The card itself (also usable in previews). */
@Composable
fun KashDialogCard(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .clip(KashShapes.Dialog)
            .background(KashColors.Surface)
            .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (title.isNotEmpty() || description != null) Column {
            if (title.isNotEmpty()) Text(title, style = KashType.TitleM, color = KashColors.Ink, modifier = Modifier.semantics { heading() })
            if (description != null) {
                Spacer(Modifier.height(8.dp))
                Text(description, style = KashType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = KashColors.InkMuted)
            }
        }
        content()
    }
}

// ─── Previews ────────────────────────────────────────────────────────────

@Preview(name = "Screen with tab bar", widthDp = 390, heightDp = 844)
@Composable
private fun ScreenPreview() = KashTheme {
    KashScreen(
        hero = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("kash", style = KashType.Wordmark, color = KashColors.OnSlate, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KashIconButton(KashIcon.Plus, "Add transaction", {}, onSlate = true)
                    KashIconButton(KashIcon.Settings, "Settings", {}, onSlate = true)
                }
            }
            Column {
                Text("Total balance", style = KashType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = KashColors.OnSlateMuted)
                KashFigure(18245.0)
            }
            KashButton("Review", {}, Modifier.fillMaxWidth(), KashButtonVariant.Accent, badge = "4", compact = true)
            KashWeekChart(listOf(0.3f, 0.6f, 0.15f, 0.4f, 0.9f, 0f, 0f), today = 4, total = "−4,120")
        },
        tabBar = { KashTabBar(KashTab.Home, {}) },
    ) { pad ->
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Transactions", style = KashType.TitleS, color = KashColors.Ink, modifier = Modifier.weight(1f))
            Text("See all", style = KashType.Label, color = KashColors.InkMuted)
        }
        Column(Modifier.padding(pad)) {
            KashDayHeader("Today")
            KashTransactionRow("bekelu beyene", 160.0, KashDirection.In, account = "Telebirr", time = "12:36")
            KashTransactionRow("Daily Internet Pack 1GB", 100.0, KashDirection.Out, account = "Telebirr", time = "12:36", category = "Airtime")
        }
    }
}

@Preview(name = "Sub-page header", widthDp = 390)
@Composable
private fun TopBarPreview() = KashTheme {
    Column(Modifier.background(KashColors.Slate).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KashTopBar("Review", onBack = {}, actions = { Text("3 of 6", style = KashType.Label, color = KashColors.OnSlateMuted) })
        KashTopBar("Food", onBack = {}, centerTitle = true, actions = { KashIconButton(KashIcon.Calendar, "Pick dates", {}, onSlate = true) })
        KashTopBar("Commercial Bank", onBack = {}, centerTitle = true, leading = { KashBankBadge("Commercial Bank of Ethiopia", size = 32.dp) })
    }
}

@Preview(name = "Sheet and dialog", widthDp = 390)
@Composable
private fun OverlaysPreview() = KashTheme {
    Column(Modifier.background(KashColors.Backdrop).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KashDialogCard("Forgot your PIN?", "Sign in to your Kash account again, then choose a new PIN.") {
            KashButton("Sign in again", {}, Modifier.fillMaxWidth())
            KashButton("Cancel", {}, Modifier.fillMaxWidth(), KashButtonVariant.Quiet)
        }
        Box(Modifier.clip(KashShapes.Sheet).background(KashColors.Surface)) {
            KashSheetBody("Show transactions from", onClose = {}) {
                Column {
                    KashRadioRow("This week", false, {})
                    KashRadioRow("This month", true, {})
                    KashListRow("Pick dates…", icon = KashIcon.Calendar, showDivider = false, inset = 0.dp, onClick = {})
                }
            }
        }
    }
}
