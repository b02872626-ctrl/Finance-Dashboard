package com.financeapp.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.data.categorize.CategoryPredictor
import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.components.CategoryPickerSheet
import com.financeapp.ui.components.CorrectionSheet
import com.financeapp.viewmodel.ReviewQueueState
import com.financeapp.viewmodel.ReviewQueueViewModel
import com.financeapp.viewmodel.ReviewScope
import com.financeapp.viewmodel.ReviewSheet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// ─── Palette ────────────────────────────────────────────────────────────────
private val PageBg          = Color(0xFF0F0C09)
private val CardBg          = Color(0xFF231E1A)
private val CardSoft        = Color(0xFF302A25)
private val BorderCol       = Color(0xFF353535)
private val TextPrimary     = Color(0xFFF5F2EB)
private val TextMute        = Color(0xFFB7B3AC)
private val Brown           = Color(0xFFDCC8AF)
private val Mint            = Color(0xFF82C8AA)
private val MintSoft        = Color(0xFF508C73)
private val MintBannerBg    = Color(0x1A82C8AA)
private val MintWashFg      = Color(0xFF96DCBE)
private val AmberBannerBg   = Color(0x1AFFB45A)
private val AmberBannerBd   = Color(0xFFB48246)
private val AmberBannerFg   = Color(0xFFF5BE78)
private val Red             = Color(0xFFFF6E6E)
private val GreenIncome     = Color(0xFF78D296)
private val RawBg           = Color(0xFF221D18)
private val RawText         = Color(0xFFC8BEAF)
private val ProgressEmpty   = Color(0xFF302A22)
private val SwipeConfirmBg  = Color(0xF282C8AA)
private val SwipeSkipBg     = Color(0xF2FFB45A)
private val SnackBg         = Color(0xFF322C24)
private val SnackUndo       = Color(0xFFFE9B9F)
private val UnknownBg       = Color(0xFF3C2020)
private val UnknownFg       = Color(0xFFF8A2A2)
private val MonoFont        = FontFamily.Monospace

// ─── Screen entry point ─────────────────────────────────────────────────────
@Composable
fun ReviewQueueScreen(
    vm: ReviewQueueViewModel,
    userName: String,
    customCategories: List<String>,
    onAddCustomCategory: (String) -> Unit,
    onClose: () -> Unit
) {
    val state by vm.state.collectAsState()
    val categories = remember(customCategories) {
        TransactionCategoryCatalog.allCategories(customCategories)
    }
    // Tactile feedback on commit actions (confirm / skip / category pick).
    // Centralized so every action site fires the same physical pattern.
    val haptic = com.financeapp.ui.theme.rememberHaptic()

    // Auto-close when the queue clears so the user lands back on Home —
    // but wait for the snackbar to disappear first so the Undo button is
    // tappable for the full window. The snackbar auto-dismisses in the
    // ViewModel (2.6 s single / 6.5 s bulk); we re-poll after it goes away.
    LaunchedEffect(state.complete, state.snackbar) {
        if (state.complete && state.snackbar == null) {
            kotlinx.coroutines.delay(450)
            // Re-check that nothing came back (e.g. via Undo).
            if (state.complete && state.snackbar == null) onClose()
        }
    }

    Box(Modifier.fillMaxSize().background(PageBg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            QueueHeader(
                done = state.doneToday,
                total = state.totalToday,
                scope = state.scope,
                onClose = onClose
            )

            Spacer(Modifier.height(16.dp))

            // The card swims inside its own region so swipes don't fight
            // a scroll container.
            //
            // Stacked-depth: render up to 2 ghost cards behind the active
            // one (deeper = smaller + more transparent + offset down) so the
            // user can SEE there's more queued, not just trust a counter.
            //
            // Entry nudge: on first composition of this screen, briefly
            // shift the active card right→left→neutral so the swipe
            // affordance is discoverable without copy.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                val current = state.current
                if (state.isLoading) {
                    LoadingQueue()
                } else if (current == null) {
                    EmptyQueue()
                } else {
                    // Ghost cards: render BEFORE the active card so the
                    // active one is z-ordered on top.
                    state.items.getOrNull(state.currentIdx + 2)?.let { peek ->
                        GhostTxnCard(tx = peek, depth = 2)
                    }
                    state.items.getOrNull(state.currentIdx + 1)?.let { peek ->
                        GhostTxnCard(tx = peek, depth = 1)
                    }

                    // Entry-nudge animation removed per user direction — UI
                    // now stays still on screen entry. SwipeableTxnCard still
                    // accepts nudgeOffset but we always pass 0f.
                    SwipeableTxnCard(
                        tx = current,
                        userName = userName,
                        showRaw = state.showRaw,
                        nudgeOffset = 0f,
                        onConfirm = { haptic(com.financeapp.ui.theme.Haptic.CONFIRM); vm.confirm() },
                        onSkip    = { haptic(com.financeapp.ui.theme.Haptic.TAP);     vm.skip() },
                        onPickCat = vm::openPickerSheet,
                        onToggleRaw = vm::toggleRaw,
                        onLongPress = { haptic(com.financeapp.ui.theme.Haptic.LONG);  vm.openDetailSheet() },
                        onMarkAsTransfer = {
                            haptic(com.financeapp.ui.theme.Haptic.CONFIRM)
                            vm.pickCategory("Transfer", advance = true)
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (state.current != null) {
                CatChipRow(
                    // Show the user-confirmed category if there is one;
                    // otherwise highlight the prediction. Was previously
                    // hardcoded to predictedCategory which never reflected
                    // the user's later changes.
                    active = state.current?.let { it.category ?: it.predictedCategory },
                    categories = categories,
                    onPick = {
                        haptic(com.financeapp.ui.theme.Haptic.CONFIRM)
                        vm.pickCategory(it, advance = true)
                    },
                    onMore = vm::openPickerSheet
                )
                Spacer(Modifier.height(12.dp))
                val current = state.current!!
                ActionBar(
                    onSkip = vm::skip,
                    onConfirm = vm::confirm,
                    primaryLabel = if (current.predictedCategory == null) "Save" else "Confirm"
                )
            }
        }

        // Snackbar
        state.snackbar?.let { snack ->
            Snackbar(
                text = snack.text,
                onUndo = vm::undo,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 80.dp)
            )
        }

        // Confirmation popup — "Apply X to all Y transactions?"
        state.confirmApplyAll?.let { confirm ->
            AlertDialog(
                onDismissRequest = vm::dismissConfirmApplyAll,
                containerColor = CardBg,
                titleContentColor = TextPrimary,
                textContentColor = TextMute,
                title = {
                    Text(
                        "Categorize all transactions with ${confirm.counterparty}?",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    )
                },
                text = {
                    Text(
                        buildString {
                            append("Found ")
                            append(if (confirm.totalCount == 1) "1 transaction" else "${confirm.totalCount} transactions")
                            append(" with this name.\n\n")
                            append("'Apply to all' will set every one of them — including any you've already categorized differently — to ${confirm.category}, and remember the rule so future ${confirm.counterparty} transactions auto-categorize.\n\n")
                            append("'Just this one' only categorizes this row.")
                        },
                        color = TextMute,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = vm::confirmApplyAll,
                        colors = ButtonDefaults.textButtonColors(contentColor = Mint)
                    ) {
                        Text(
                            "Apply to all",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = vm::confirmApplyJustOne,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextMute)
                    ) {
                        Text("Just this one", fontSize = 14.sp)
                    }
                }
            )
        }

        // Sheets
        if (state.sheet == ReviewSheet.Picker) {
            val current = state.current
            if (current != null) {
                CategoryPickerSheet(
                    merchant = current.counterparty ?: "this merchant",
                    currentCategory = current.category ?: current.predictedCategory,
                    categories = categories,
                    applySimilar = state.applyToSimilar,
                    onApplySimilarChange = { vm.toggleApplyToSimilar() },
                    onPick = { vm.pickCategory(it, advance = true) },
                    onAddCustom = onAddCustomCategory,
                    onClose = vm::closeSheet
                )
            }
        } else if (state.sheet == ReviewSheet.Detail) {
            val current = state.current
            if (current != null) {
                CorrectionSheet(
                    tx = current,
                    onPickCat = vm::openPickerSheet,
                    onNotATransaction = vm::markNotATransaction,
                    onClose = vm::closeSheet
                )
            }
        }
    }
}

// ─── QueueHeader: back + title + dotted progress + estimate ─────────────────
@Composable
private fun QueueHeader(done: Int, total: Int, scope: ReviewScope, onClose: () -> Unit) {
    val left = (total - done).coerceAtLeast(0)
    val calendarSystem = com.financeapp.util.LocalCalendarSystem.current
    val dateLabel = remember(calendarSystem) {
        // Weekday + month/day. Weekday stays locale-driven; month/day flips
        // to Ethiopian (Ginbot, Sene, etc) when calendarSystem == "ETHIOPIAN".
        val weekday = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date())
        val monthDay = com.financeapp.util.CalendarFormatter.formatShort(
            timestampMs = System.currentTimeMillis(),
            calendarSystem = calendarSystem
        )
        "$weekday · $monthDay"
    }
    val (eyebrow, title) = when (scope) {
        ReviewScope.TODAY -> "Daily Review" to dateLabel
        ReviewScope.ALL   -> "Review queue" to "$left older pending"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton38(onClick = onClose) {
            Text("‹", color = Brown, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(eyebrow, color = TextMute, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        // Placeholder to balance the back button so the title stays centered.
        // (Was a no-op calendar icon — removed until a date jump is wired up.)
        Spacer(Modifier.size(38.dp))
    }
    Spacer(Modifier.height(16.dp))
    SegmentedProgress(done = done, total = total)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("$done of $total reviewed", color = TextMute, fontSize = 12.5.sp)
        Text("$left left · ${humanEta(left)}", color = TextMute, fontSize = 12.5.sp)
    }
}

/** Friendly time estimate at ~6 s per review: "30s" / "5 min" / "1 hr 20 min". */
private fun humanEta(left: Int): String {
    val seconds = (left * 6).coerceAtLeast(1)
    if (seconds < 60)   return "~${seconds}s"
    val minutes = (seconds + 30) / 60        // round to nearest
    if (minutes < 60)   return "~${minutes} min"
    val hours    = minutes / 60
    val mins     = minutes % 60
    return if (mins == 0) "~${hours} hr" else "~${hours} hr ${mins} min"
}

@Composable
private fun SegmentedProgress(done: Int, total: Int) {
    val segments = total.coerceIn(1, 24)
    // For small queues (total <= 24), one segment = one transaction.
    // For larger backlogs, scale progress proportionally so 50% done means
    // 12 of 24 segments are filled — not just whichever 12 indices are < done.
    val filledSegments = when {
        total <= 0   -> 0
        total <= 24  -> done.coerceAtMost(segments)
        else         -> (done.toLong() * segments / total).toInt().coerceIn(0, segments)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(segments) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (i < filledSegments) Mint else ProgressEmpty)
            )
        }
    }
}

// ─── SwipeableTxnCard — the big interactive card ────────────────────────────
@Composable
private fun SwipeableTxnCard(
    tx: TransactionEntity,
    userName: String,
    showRaw: Boolean,
    nudgeOffset: Float = 0f,        // entry-hint horizontal nudge (px)
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onPickCat: () -> Unit,
    onToggleRaw: () -> Unit,
    onLongPress: () -> Unit,
    onMarkAsTransfer: () -> Unit
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    // Fling threshold: if the user flings horizontally above this velocity
    // we commit even when the displacement hasn't passed swipeThresholdPx.
    // 800 dp/sec is a comfortable wrist-flick.
    val flingThresholdPx = with(density) { 800.dp.toPx() }
    val dragX = remember { Animatable(0f) }
    val velocityTracker = remember { androidx.compose.ui.input.pointer.util.VelocityTracker() }
    val scope = rememberCoroutineScope()

    // Column (not Box) so banners flow below the card instead of stacking
    // on top of it. The swipe modifiers stay on the parent so the whole
    // group moves together when the user drags.
    //
    // nudgeOffset is added on top of dragX in graphicsLayer ONLY for the
    // visual translation — the swipe-threshold check uses dragX alone, so
    // the entry nudge can't accidentally trigger confirm/skip.
    Column(
        Modifier
            .graphicsLayer {
                translationX = dragX.value + nudgeOffset
                rotationZ    = (dragX.value * 0.015f)
            }
            .pointerInput(tx.id) {
                detectHorizontalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onDragEnd = {
                        val vx = velocityTracker.calculateVelocity().x
                        val pastDistance = abs(dragX.value) > swipeThresholdPx
                        val pastVelocity = abs(vx) > flingThresholdPx
                        if (pastDistance || pastVelocity) {
                            // Direction comes from displacement when we have
                            // it; otherwise (a fling from rest) trust the
                            // velocity sign.
                            val rightward =
                                if (abs(dragX.value) > 1f) dragX.value > 0 else vx > 0
                            if (rightward) onConfirm() else onSkip()
                        }
                        scope.launch {
                            dragX.animateTo(0f, tween(durationMillis = 220))
                        }
                    }
                ) { change, drag ->
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    scope.launch { dragX.snapTo(dragX.value + drag) }
                }
            }
            .pointerInput(tx.id) {
                detectTapGestures(onLongPress = { onLongPress() })
            }
    ) {
        TxnCardBody(
            tx = tx,
            userName = userName,
            showRaw = showRaw,
            dragX = dragX.value,
            onPickCat = onPickCat,
            onToggleRaw = onToggleRaw
        )

        FlagBanners(
            tx = tx,
            userName = userName,
            onMarkAsTransfer = onMarkAsTransfer,
            onOpenDetail = onLongPress
        )
    }
}

/**
 * Static non-interactive card that sits behind the active SwipeableTxnCard
 * to communicate "more to review". Scaled + faded + offset down by depth so
 * a 2-deep stack reads as a real stack of paper. No gestures, no flag
 * banners — the next-card preview only needs the merchant + amount line so
 * the user can already start thinking about the next one.
 *
 * Why these numbers (per depth):
 *   1 → scale 0.95, y-offset 14dp, alpha 0.65
 *   2 → scale 0.90, y-offset 28dp, alpha 0.40
 * Deeper than 2 disappears entirely — beyond that the gain is just noise.
 */
@Composable
private fun GhostTxnCard(tx: TransactionEntity, depth: Int) {
    val scale  = if (depth == 1) 0.95f else 0.90f
    val alpha  = if (depth == 1) 0.65f else 0.40f
    val yShift = if (depth == 1) 14f   else 28f
    val isIncome = tx.type == TransactionType.CREDIT.name
    val time = remember(tx.dateTime) {
        SimpleDateFormat("HH:mm", Locale.US).format(Date(tx.dateTime))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX       = scale
                scaleY       = scale
                translationY = yShift * density
                this.alpha   = alpha
            }
            .clip(RoundedCornerShape(28.dp))
            .background(CardBg)
            .border(1.dp, BorderCol, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 24.dp)
    ) {
        // Just the header strip — source pill + time + EXPENSE/INCOME chip.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourcePill(tx.bankName.ifBlank { tx.sender })
                Text("SMS · $time", color = TextMute, fontSize = 12.sp)
            }
            Text(
                if (isIncome) "INCOME" else "EXPENSE",
                color = if (isIncome) GreenIncome else Red,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isIncome) Color(0x1F78D296) else Color(0x1FFF6E6E))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
        // Amount + merchant — enough preview that the user knows what's next.
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (if (isIncome) "+" else "−") + formatAmount(tx.amount),
                color = if (isIncome) GreenIncome else Red,
                fontSize = 36.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("ETB", color = TextMute, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tx.counterparty ?: "Unknown merchant",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun TxnCardBody(
    tx: TransactionEntity,
    userName: String,
    showRaw: Boolean,
    dragX: Float,
    onPickCat: () -> Unit,
    onToggleRaw: () -> Unit
) {
    val isIncome = tx.type == TransactionType.CREDIT.name
    val calendarSystem = com.financeapp.util.LocalCalendarSystem.current
    val time = remember(tx.dateTime, calendarSystem) {
        // Show the full date for older transactions so adjacent cards in
        // the backlog don't look identical. For today's items, just HH:mm.
        val now    = System.currentTimeMillis()
        val isToday = run {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = now
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            tx.dateTime >= cal.timeInMillis
        }
        if (isToday) {
            SimpleDateFormat("HH:mm", Locale.US).format(Date(tx.dateTime))
        } else {
            // Ethiopian when toggled on: "Miyazya 4 · 14:32"
            val datePart = com.financeapp.util.CalendarFormatter.formatShort(tx.dateTime, calendarSystem)
            val timePart = SimpleDateFormat("HH:mm", Locale.US).format(Date(tx.dateTime))
            "$datePart · $timePart"
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CardBg)
            .border(1.dp, BorderCol, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 24.dp)
    ) {
        // Swipe hint chip
        if (abs(dragX) > 30) {
            val right = dragX > 0
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (right) Arrangement.Start else Arrangement.End
            ) {
                Text(
                    if (right) "CONFIRM" else "SKIP",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (right) SwipeConfirmBg else SwipeSkipBg)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // Source pill + time + EXPENSE/INCOME chip
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourcePill(tx.bankName.ifBlank { tx.sender })
                Text("SMS · $time", color = TextMute, fontSize = 12.sp)
            }
            Text(
                if (isIncome) "INCOME" else "EXPENSE",
                color = if (isIncome) GreenIncome else Red,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isIncome) Color(0x1F78D296) else Color(0x1FFF6E6E))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }

        // Amount
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                (if (isIncome) "+" else "−") + formatAmount(tx.amount),
                color = if (isIncome) GreenIncome else Red,
                fontSize = 44.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("ETB", color = TextMute, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        }

        // Merchant
        Spacer(Modifier.height(14.dp))
        Text(if (isIncome) "From" else "To", color = TextMute, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            tx.counterparty ?: "Unknown merchant",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 26.sp
        )

        // Suggested category strip
        Spacer(Modifier.height(18.dp))
        SuggestedCategoryStrip(
            category = tx.predictedCategory,
            confidence = tx.categoryConfidence,
            onChange = onPickCat
        )

        // Confidence dots / "learned from N similar"
        val confidence = tx.categoryConfidence ?: 0.0
        if (tx.predictedCategory != null && confidence >= CategoryPredictor.LOW_CONFIDENCE_THRESHOLD) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ConfidenceDots(confidence)
                Text(
                    "${(confidence * 100).toInt()}% confidence",
                    color = TextMute, fontSize = 11.5.sp
                )
            }
        }

        // Raw SMS toggle
        Spacer(Modifier.height(14.dp))
        Text(
            if (showRaw) "▲ Hide raw SMS" else "▼ Show raw SMS",
            color = TextMute,
            fontSize = 12.5.sp,
            modifier = Modifier.clickable(
                onClick = onToggleRaw,
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            )
        )
        if (showRaw) {
            Spacer(Modifier.height(8.dp))
            Text(
                tx.rawBody,
                color = RawText,
                fontSize = 11.5.sp,
                fontFamily = MonoFont,
                lineHeight = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RawBg)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun SourcePill(label: String) {
    val (fg, bg) = when {
        label.contains("Commercial Bank", ignoreCase = true) -> Color(0xFF8CD2AA) to Color(0xFF1C3426)
        label.contains("Telebirr", ignoreCase = true)        -> Color(0xFFFFB478) to Color(0xFF402816)
        label.contains("Abyssinia", ignoreCase = true)       -> Color(0xFF98C2FA) to Color(0xFF1E2E48)
        label.contains("Awash", ignoreCase = true)           -> Color(0xFFFAC679) to Color(0xFF3A2C18)
        label.contains("Dashen", ignoreCase = true)          -> Color(0xFFC6ACEE) to Color(0xFF322648)
        else                                                  -> Brown to Color(0xFF2A2520)
    }
    Text(
        shortBankLabel(label),
        color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

private fun shortBankLabel(name: String): String = when {
    name.contains("Commercial Bank", ignoreCase = true) -> "CBE"
    name.contains("Telebirr", ignoreCase = true)        -> "Telebirr"
    name.contains("Abyssinia", ignoreCase = true)       -> "BOA"
    name.contains("Awash", ignoreCase = true)           -> "Awash"
    name.contains("Dashen", ignoreCase = true)          -> "Dashen"
    else -> name
}

@Composable
private fun SuggestedCategoryStrip(
    category: String?,
    confidence: Double?,
    onChange: () -> Unit
) {
    val low = category == null || (confidence ?: 0.0) < CategoryPredictor.LOW_CONFIDENCE_THRESHOLD
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardSoft)
            .border(1.dp, BorderCol, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Glyph block (first letter of category or "?")
        val glyph = category?.firstOrNull()?.uppercase() ?: "?"
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (low) UnknownBg else Color(0xFF44281A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                glyph,
                color = if (low) UnknownFg else Color(0xFFFFB482),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (low) "Needs review" else "Likely",
                color = TextMute, fontSize = 12.5.sp
            )
            Text(
                category ?: "Uncategorized",
                color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium
            )
        }
        Text(
            "Change",
            color = TextPrimary, fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, BorderCol, RoundedCornerShape(50))
                .clickable(onClick = onChange)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ConfidenceDots(level: Double) {
    val n = when {
        level > 0.85 -> 3
        level > 0.60 -> 2
        else         -> 1
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i < n) Mint else ProgressEmpty)
            )
        }
    }
}

// ─── Edge flag banners (transfer-suspected + low-confidence) ───────────────
@Composable
private fun FlagBanners(
    tx: TransactionEntity,
    userName: String,
    onMarkAsTransfer: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val suspectedTransfer = isLikelyTransfer(tx, userName)
    val lowConf = (tx.categoryConfidence ?: 0.0) < CategoryPredictor.LOW_CONFIDENCE_THRESHOLD &&
                  tx.predictedCategory == null

    if (suspectedTransfer) {
        Spacer(Modifier.height(12.dp))
        FlagBanner(
            icon = "⇄",
            iconBg = Color(0xFF1C3426),
            iconFg = MintWashFg,
            bg = MintBannerBg,
            borderCol = MintSoft,
            title = "Looks like a transfer",
            body = "${tx.counterparty ?: "This person"} appears to match your own account. Marking as Transfer prevents fake spending.",
            cta = "Mark as transfer",
            ctaFg = MintWashFg,
            onCta = onMarkAsTransfer
        )
    }

    if (lowConf) {
        Spacer(Modifier.height(12.dp))
        FlagBanner(
            icon = "!",
            iconBg = Color(0xFF3A2C18),
            iconFg = AmberBannerFg,
            bg = AmberBannerBg,
            borderCol = AmberBannerBd,
            title = "Low-confidence parse",
            body = "We couldn't pick a category cleanly. Tap to verify the raw SMS, then pick one.",
            cta = "Open detail",
            ctaFg = AmberBannerFg,
            onCta = onOpenDetail
        )
    }
}

@Composable
private fun FlagBanner(
    icon: String,
    iconBg: Color, iconFg: Color,
    bg: Color, borderCol: Color,
    title: String, body: String,
    cta: String, ctaFg: Color,
    onCta: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, color = iconFg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(body, color = TextMute, fontSize = 12.5.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                cta,
                color = ctaFg, fontSize = 12.5.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(CardBg)
                    .border(1.dp, borderCol, RoundedCornerShape(50))
                    .clickable(onClick = onCta)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Heuristic: an outgoing transaction whose counterparty contains a >=4-char
 * token from the user's display name looks like a self-transfer between
 * the user's own accounts. Not perfect — but the user can dismiss by
 * picking any other category.
 */
private fun isLikelyTransfer(tx: TransactionEntity, userName: String): Boolean {
    val cp = tx.counterparty ?: return false
    if (tx.type == TransactionType.CREDIT.name) return false
    val cpLow = cp.lowercase(Locale.US)
    val tokens = userName.split(' ', '.', ',', '\t')
        .map { it.lowercase(Locale.US).trim() }
        .filter { it.length >= 4 }
    return tokens.any { it in cpLow }
}

// ─── Bottom bar pieces ──────────────────────────────────────────────────────
@Composable
private fun CatChipRow(
    active: String?,
    categories: List<String>,
    onPick: (String) -> Unit,
    onMore: () -> Unit
) {
    val common = listOf("Food", "Transport", "Bills", "Shopping", "Transfer", "Other")
        .filter { it in categories }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        common.forEach { c ->
            val on = active == c
            Text(
                c,
                color = if (on) Color(0xFF231E1A) else TextPrimary,
                fontSize = 13.5.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) TextPrimary else CardBg)
                    .border(1.dp, BorderCol, RoundedCornerShape(50))
                    .clickable { onPick(c) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        Text(
            "All categories",
            color = TextMute, fontSize = 13.5.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, BorderCol, RoundedCornerShape(50))
                .clickable(onClick = onMore)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ActionBar(onSkip: () -> Unit, onConfirm: () -> Unit, primaryLabel: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(CardBg)
                .border(1.dp, BorderCol, RoundedCornerShape(18.dp))
                .clickable(onClick = onSkip)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⏭  Skip", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Row(
            Modifier
                .weight(2f)
                .clip(RoundedCornerShape(18.dp))
                .background(TextPrimary)
                .clickable(onClick = onConfirm)
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("✓  $primaryLabel", color = Color(0xFF231E1A), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Snackbar(text: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SnackBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text, color = TextPrimary, fontSize = 14.sp)
        Text(
            "Undo",
            color = SnackUndo, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onUndo).padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun EmptyQueue() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("All caught up", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Every transaction reviewed for today.", color = TextMute, fontSize = 14.sp)
    }
}

@Composable
private fun LoadingQueue() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            color = Mint, strokeWidth = 3.dp,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Loading review queue…", color = TextMute, fontSize = 13.sp)
    }
}

@Composable
private fun IconButton38(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, BorderCol, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun formatAmount(value: Double): String {
    val absolute = kotlin.math.abs(value)
    return String.format(Locale.US, "%,.2f", absolute)
}
