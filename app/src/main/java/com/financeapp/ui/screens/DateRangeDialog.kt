package com.financeapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashDialog
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num
import com.financeapp.util.EthiopianCalendar
import com.financeapp.util.KashDates
import java.util.Calendar

private const val DAY = 86_400_000L

/** "20 – 26 Oct", "28 Sep – 3 Oct", "Tikimt 10 – 16" — fits both calendars. */
fun rangeLabel(fromMs: Long, toInclusiveMs: Long, cal: String): String {
    val sameMonth = KashDates.monthBounds(fromMs, cal).first == KashDates.monthBounds(toInclusiveMs, cal).first
    return if (cal == "ETHIOPIAN") {
        val a = EthiopianCalendar.fromMillis(fromMs)
        val b = EthiopianCalendar.fromMillis(toInclusiveMs)
        if (sameMonth) "${a.monthName} ${a.day} – ${b.day}" else "${a.monthName} ${a.day} – ${b.monthName} ${b.day}"
    } else {
        val a = KashDates.dayMonth(fromMs, cal)
        val b = KashDates.dayMonth(toInclusiveMs, cal)
        if (sameMonth) "${a.substringBefore(' ')} – $b" else "$a – $b"
    }
}

/**
 * AppHistoryDate / AppHistoryDateEC: pick a start and an end day in a month
 * grid of the chosen calendar. [onApply] gets [start, endExclusive).
 */
@Composable
fun DateRangeDialog(
    calendarSystem: String,
    initialFrom: Long?,
    initialTo: Long?,
    onDismiss: () -> Unit,
    onApply: (fromMs: Long, toExclusiveMs: Long) -> Unit,
) {
    val today = KashDates.startOfDay(System.currentTimeMillis())
    var monthStart by remember { mutableLongStateOf(KashDates.monthBounds(initialFrom ?: today, calendarSystem).first) }
    var start by remember { mutableStateOf(initialFrom?.let(KashDates::startOfDay)) }
    var end by remember { mutableStateOf(initialTo?.let { KashDates.startOfDay(it - 1) }) }

    val title = when {
        start != null && end != null -> rangeLabel(start!!, end!!, calendarSystem)
        start != null -> rangeLabel(start!!, start!!, calendarSystem)
        else -> "Pick a start day"
    }

    KashDialog(title = "", onDismiss = onDismiss) {
        Column(Modifier.padding(top = 0.dp)) {
            Text("Pick dates", style = KashType.Label, color = KashColors.InkMuted)
            Text(title, style = KashType.TitleM, color = KashColors.Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            KashIconButton(KashIcon.Back, "Previous month", {
                monthStart = KashDates.monthBounds(monthStart - DAY, calendarSystem).first
            })
            Text(
                KashDates.monthYear(monthStart + DAY, calendarSystem),
                style = KashType.Body, color = KashColors.Ink,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            KashIconButton(KashIcon.ChevronRight, "Next month", {
                monthStart = KashDates.monthBounds(monthStart, calendarSystem).second
            })
        }
        MonthGrid(
            monthStart = monthStart,
            calendarSystem = calendarSystem,
            today = today,
            start = start,
            end = end,
            onPick = { day ->
                val s = start
                when {
                    s == null || end != null -> { start = day; end = null }
                    day < s -> { end = s; start = day }
                    else -> end = day
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KashButton("Cancel", onDismiss, Modifier.widthIn(min = 104.dp), KashButtonVariant.Secondary)
            KashButton(
                "Show these days",
                {
                    val s = start ?: return@KashButton
                    val e = end ?: s
                    onApply(s, KashDates.startOfDay(e + DAY + DAY / 2))
                },
                Modifier.weight(1f),
                enabled = start != null,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    monthStart: Long,
    calendarSystem: String,
    today: Long,
    start: Long?,
    end: Long?,
    onPick: (Long) -> Unit,
) {
    val monthEnd = KashDates.monthBounds(monthStart, calendarSystem).second
    val days = ((monthEnd - monthStart + DAY / 2) / DAY).toInt()
    // Monday-first offset of day 1.
    val dow = Calendar.getInstance().apply { timeInMillis = monthStart }.get(Calendar.DAY_OF_WEEK)
    val offset = (dow - Calendar.MONDAY + 7) % 7
    val cells = offset + days
    val rows = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it, style = KashType.Caption, color = KashColors.InkMuted,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        repeat(rows) { r ->
            Row {
                repeat(7) { c ->
                    val i = r * 7 + c - offset + 1
                    if (i < 1 || i > days) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val day = KashDates.startOfDay(monthStart + (i - 1) * DAY + DAY / 2)
                        val isEnd = day == start || day == end
                        val inRange = start != null && end != null && day > start && day < end
                        val isToday = day == today
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .then(if (inRange) Modifier.background(KashColors.Well) else Modifier)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .then(
                                    when {
                                        isEnd -> Modifier.background(KashColors.Slate)
                                        isToday -> Modifier.border(2.dp, KashColors.Ink, RoundedCornerShape(14.dp))
                                        else -> Modifier
                                    }
                                )
                                .kashClickable { onPick(day) }
                                .semantics {
                                    selected = isEnd || inRange
                                    contentDescription = KashDates.monthDay(day, calendarSystem)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$i",
                                style = KashType.Body.num().copy(fontWeight = if (isEnd) FontWeight.SemiBold else FontWeight.Medium),
                                color = when {
                                    isEnd -> KashColors.OnSlate
                                    day > today -> KashColors.InkFaint
                                    else -> KashColors.Ink
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
