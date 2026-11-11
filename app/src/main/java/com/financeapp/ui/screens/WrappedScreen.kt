package com.financeapp.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.financeapp.notifications.NotifyText
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.components.kashBank
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.AnalyticsViewModel
import com.financeapp.viewmodel.Tip
import com.financeapp.viewmodel.Wrapped
import kotlinx.coroutines.launch
import java.io.File

/** One page of the story: a small line, the big line, and what it means. */
private data class WrappedPage(val kicker: String, val big: String, val body: String)

/** The pages a month has; pages without data are left out. */
private fun pagesOf(w: Wrapped, cal: String): List<WrappedPage> = buildList {
    val month = KashDates.monthName(w.monthStartMs + 86_400_000L, cal)
    add(WrappedPage("Kash Wrapped", "Your $month", "${w.payments} payments across ${if (w.accounts == 1) "1 account" else "${w.accounts} accounts"}. Here's how it went."))
    add(WrappedPage(
        "Money out", "${KashMoney.whole(w.spent)} ETB",
        when {
            w.vsLastMonthPct == null -> "That's what left your accounts in $month."
            w.vsLastMonthPct > 0 -> "${w.vsLastMonthPct}% more than the month before."
            w.vsLastMonthPct < 0 -> "${-w.vsLastMonthPct}% less than the month before. Nice."
            else -> "About the same as the month before."
        },
    ))
    if (w.moneyIn > 0) add(
        if (w.kept >= 0) WrappedPage("You kept", "${KashMoney.whole(w.kept)} ETB", "${w.keptPct}% of the ${KashMoney.whole(w.moneyIn)} ETB that came in.")
        else WrappedPage("Out beat in", "${KashMoney.whole(-w.kept)} ETB", "More went out than the ${KashMoney.whole(w.moneyIn)} ETB that came in.")
    )
    w.biggest?.let { b ->
        add(WrappedPage("Biggest payment", "${KashMoney.whole(b.amount)} ETB", "To ${b.who}, on ${KashDates.dayMonth(b.atMs, cal)}."))
    }
    w.regular?.let { r ->
        add(WrappedPage("Your regular", r.name, "You paid them ${r.count} times, ${KashMoney.whole(r.total)} ETB in all."))
    }
    if (w.fees > 0) add(WrappedPage(
        "Bank fees", "${KashMoney.amount(w.fees)} ETB",
        w.feeBank?.let { (bank, f) -> "${kashBank(bank).shortName} took the most: ${KashMoney.amount(f)} ETB." } ?: "Small charges add up.",
    ))
    w.busiestWeekdayMs?.let { ms ->
        val days = KashDates.weekdays(ms, cal)
        add(WrappedPage(
            "Your habits", days,
            "${w.busiestWeekdayPct}% of your spending landed on $days" + (w.partOfDay?.let { ", mostly in the ${it.label}." } ?: "."),
        ))
    }
    add(WrappedPage("For next month", tipTitle(w.tip, cal), tipBody(w.tip, cal)))
}

private fun tipTitle(t: Tip, cal: String): String = when (t) {
    is Tip.Fees -> "Watch the fees"
    is Tip.SpentMore -> "Ease off a little"
    is Tip.Overspent -> "Set a weekly limit"
    is Tip.KeptWell -> "Save it first"
    is Tip.BusyDay -> "Plan your ${KashDates.weekdays(t.weekdayMs, cal)}"
    Tip.KeepGoing -> "Keep it up"
}

private fun tipBody(t: Tip, cal: String): String = when (t) {
    is Tip.Fees -> "Banks took ${KashMoney.amount(t.fees)} ETB. Fewer, bigger transfers usually cost less than many small ones."
    is Tip.SpentMore -> "You spent ${t.pct}% more than the month before. Check Analytics mid-month to stay on track."
    is Tip.Overspent -> "${KashMoney.whole(t.over)} ETB more went out than came in. A small weekly limit makes the next month easier."
    is Tip.KeptWell -> "You kept ${t.pct}% of your money. Move some to savings as soon as it comes in, before it gets spent."
    is Tip.BusyDay -> "Your ${KashDates.weekdays(t.weekdayMs, cal)} cost the most. A little plan for that day goes a long way."
    Tip.KeepGoing -> "A steady month. Kash keeps counting so you don't have to."
}

/**
 * Kash Wrapped (NAH-34): a month's money story, page by page. Tap the right
 * side for the next page, the left for the previous, or swipe. The last
 * page shares a picture with no account numbers, balances or names.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WrappedScreen(vm: AnalyticsViewModel, anyMsInMonth: Long, onClose: () -> Unit) {
    KashSystemBars(darkIcons = false)
    val cal = LocalCalendarSystem.current
    val w by remember(anyMsInMonth) { vm.wrapped(anyMsInMonth) }.collectAsState(initial = null)
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(KashColors.Slate)) {
        val data = w
        if (data == null) {
            Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
                KashIconButton(KashIcon.Back, "Close", onClose, onSlate = true)
                Spacer(Modifier.weight(1f))
                Text("Not enough to wrap yet", style = KashType.TitleM, color = KashColors.OnSlate)
                Text(
                    "Wrapped needs a few payments in a month. Check back next month.",
                    style = KashType.Body, color = KashColors.OnSlateMuted, modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.weight(1f))
            }
            return@Box
        }
        val pages = remember(data, cal) { pagesOf(data, cal) }
        val pager = rememberPagerState { pages.size }
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Story progress: one segment per page.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i <= pager.currentPage) KashColors.OnSlate else KashColors.OnSlate.copy(alpha = 0.25f))
                    )
                }
            }
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                KashIconButton(KashIcon.Back, "Close", onClose, onSlate = true)
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage + 1} of ${pages.size}", style = KashType.Label, color = KashColors.OnSlateMuted, modifier = Modifier.padding(end = 12.dp))
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(pages.size) {
                    detectTapGestures { pos ->
                        val next = if (pos.x > size.width * 0.35f) pager.currentPage + 1 else pager.currentPage - 1
                        if (next in pages.indices) scope.launch { pager.animateScrollToPage(next) }
                    }
                },
            ) { i ->
                val p = pages[i]
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(p.kicker.uppercase(), style = KashType.Label, color = KashColors.Sand)
                    Text(
                        p.big, style = KashType.FigureL, color = KashColors.OnSlate,
                        modifier = Modifier.padding(top = 10.dp).semantics { heading() },
                    )
                    Text(p.body, style = KashType.BodyL, color = KashColors.OnSlateMuted, modifier = Modifier.padding(top = 14.dp))
                }
            }
            val last = pager.currentPage == pages.lastIndex
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (last) {
                    KashButton("Share my month", { shareWrapped(context, data, cal) }, Modifier.fillMaxWidth(), KashButtonVariant.Accent)
                    KashButton("Done", onClose, Modifier.fillMaxWidth(), KashButtonVariant.QuietOnSlate)
                } else {
                    KashButton("Next", { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth(), KashButtonVariant.Accent)
                }
            }
        }
    }
}

/**
 * A 1080×1350 picture of the month for WhatsApp or Telegram: totals,
 * habits and the Kash link. Never account numbers, balances or the names of
 * people paid.
 */
private fun shareWrapped(context: Context, w: Wrapped, cal: String) {
    runCatching {
        val width = 1080; val height = 1350
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawColor(0xFF3D4859.toInt())
        fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        // Shrinks the text size until [text] fits [maxWidth].
        fun fit(p: Paint, text: String, maxWidth: Float): Paint {
            while (p.textSize > 20f && p.measureText(text) > maxWidth) p.textSize -= 2f
            return p
        }
        val sand = 0xFFE9DFC6.toInt(); val white = 0xFFFFFFFF.toInt(); val muted = 0xFFC6CDD8.toInt()
        val month = KashDates.monthYear(w.monthStartMs + 86_400_000L, cal)
        c.drawText("KASH WRAPPED", WrappedShare.MARGIN, 170f, paint(40f, sand, bold = true))
        c.drawText("My $month", WrappedShare.MARGIN, 260f, fit(paint(76f, white, bold = true), "My $month", width - 2 * WrappedShare.MARGIN))

        // Up to six stats in two columns; never into the footer.
        val stats = buildList {
            add("Spent" + (w.vsLastMonthPct?.let { if (it > 0) " · ↑$it% vs last month" else if (it < 0) " · ↓${-it}% vs last month" else "" } ?: "")
                to "${NotifyText.k(w.spent)} ETB")
            w.keptPct?.let { pct ->
                add(if (pct >= 0) "Kept of money in" to "$pct%" else "Overspent" to "${NotifyText.k(-w.kept)} ETB")
            }
            add("Payments" to "${w.payments}")
            w.busiestWeekdayMs?.let { add("Most expensive day" to KashDates.weekdays(it, cal)) }
            if (w.fees > 0) add("Bank fees" to "${NotifyText.k(w.fees)} ETB")
        }.take(WrappedShare.MAX_STATS)
        val cells = WrappedShare.cells(stats.size, stats.firstOrNull()?.first?.contains("·") == true)
        stats.forEachIndexed { i, (label, value) ->
            val cell = cells[i]
            c.drawText(label, cell.x, cell.y, fit(paint(34f, muted), label, cell.width))
            c.drawText(value, cell.x, cell.y + 76f, fit(paint(64f, white, bold = true), value, cell.width))
        }
        c.drawText("Track yours free with Kash", WrappedShare.MARGIN, height - 150f, paint(40f, white, bold = true))
        c.drawText("kash-140.pages.dev/download", WrappedShare.MARGIN, height - 90f, paint(38f, sand))

        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "kash-wrapped.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "My $month with Kash. Track yours free: https://kash-140.pages.dev/download?ref=wrapped")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Share your month").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "Couldn't make the picture. Try again.", Toast.LENGTH_SHORT).show() }
}

/** Home card in the first week of a month: "Your October, wrapped". */
@Composable
fun WrappedCard(monthName: String, onOpen: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(com.financeapp.ui.kash.KashShapes.Card).background(KashColors.Slate).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("KASH WRAPPED", style = KashType.Label, color = KashColors.Sand)
            Text("Your $monthName, wrapped", style = KashType.BodyStrong, color = KashColors.OnSlate)
            Text("Where it went, who you paid most, and one tip for this month.", style = KashType.Caption, color = KashColors.OnSlateMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KashButton("See it", onOpen, Modifier.weight(1f), KashButtonVariant.Accent, compact = true)
            KashButton("Later", onLater, Modifier.weight(1f), KashButtonVariant.QuietOnSlate, compact = true)
        }
    }
}


/**
 * Where each stat sits on the 1080×1350 share picture: two columns under
 * the title, rows of 190px, all above the footer (which starts at
 * [FOOTER_TOP]). A first stat with a long label ("Spent · ↓47% vs last
 * month") gets the full width.
 */
internal object WrappedShare {
    const val MARGIN = 90f
    const val MAX_STATS = 6
    const val TOP = 400f
    const val ROW = 190f
    const val FOOTER_TOP = 1150f
    private const val GAP = 60f
    private const val WIDTH = 1080f

    data class Cell(val x: Float, val y: Float, val width: Float)

    fun cells(count: Int, firstWide: Boolean = false): List<Cell> {
        val colW = (WIDTH - 2 * MARGIN - GAP) / 2
        val out = mutableListOf<Cell>()
        var row = 0
        var i = 0
        if (firstWide && count > 0) { out += Cell(MARGIN, TOP, WIDTH - 2 * MARGIN); row = 1; i = 1 }
        while (i < count) {
            val col = (i - (if (firstWide) 1 else 0)) % 2
            out += Cell(MARGIN + col * (colW + GAP), TOP + row * ROW, colW)
            if (col == 1) row++
            i++
        }
        return out
    }
}
