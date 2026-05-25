package com.financeapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.R
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.CoralPrimary
import com.financeapp.viewmodel.AnalyticsViewModel
import com.financeapp.viewmodel.CategoryTotal
import com.financeapp.viewmodel.InsightsState
import java.util.Calendar
import java.util.Locale

// =============================================================================
// Insights tab — pixel-faithful reproduction of
//   design/insights/Insights_light.png
//
// Sections:
//   1. Header                 — greeting + name + profile button
//   2. Today's Spending card  — total + tx count
//   3. This Month's spending  — total + 5×7 calendar grid + top category
//   4. Spending AVG card      — avg/day + week bar chart (S M T W T F S)
//   5. Donut chart            — spend per category slices
//   6. Total spend by Category — list with icons + amounts (tap → detail)
//   7. Uncategorized pill     — count + arrow
// =============================================================================

private val PageBg     = AppBackground
private val CardBg     = Color(0xFF231E1A)
private val InnerDark  = Color(0xFF15110F)
private val Headline   = AppBlue
private val Coral      = CoralPrimary
private val BodyMuted  = Color(0xFFB7B3AC)
private val FooterGrey = Color(0xFF7C7872)
private val GridGrey   = Color(0xFF3A3633)
private val BarGrey    = Color(0xFF8E8A83)
private val DividerCol = Color(0x1FFFFFFF)

// Six-slice donut palette — order matches default categories.
private val DonutColors = listOf(
    Color(0xFF4FB99F),  // Food — teal
    Color(0xFFE56B5C),  // Coffee — coral
    Color(0xFF3C6BC9),  // Bills — blue
    Color(0xFF59C792),  // Loan — green
    Color(0xFF8A8F39),  // Drinks & fun — olive
    Color(0xFF7BD0E5)   // Transport — cyan
)

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel, onCounterpartyClick: (String) -> Unit) {
    val insights by vm.insights.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { InsightsHeader() }
        item { TodaySpendingCard(insights) }
        item { MonthSpendingCard(insights) }
        item { SpendingAvgCard(insights) }
        item { DonutCard(insights) }
        item { CategoryListCard(insights, onCategoryClick = onCounterpartyClick) }
        if (insights.uncategorizedCount > 0) {
            item { UncategorizedPill(count = insights.uncategorizedCount, onClick = { onCounterpartyClick("__uncategorized__") }) }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------
@Composable
private fun InsightsHeader() {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else      -> "Good evening"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = greeting, color = Headline, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Welcome",
                color = Headline,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 26.sp
            )
        }
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_profile_dark),
                contentDescription = "Profile",
                tint = Headline,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Today's Spending
// ---------------------------------------------------------------------------
@Composable
private fun TodaySpendingCard(state: InsightsState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(text = "Todays Spending", color = BodyMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "ETB ${moneyShort(state.todaySpending)}",
            color = Headline,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(2.dp))
        Text(text = "${state.todayCount} transactions today", color = FooterGrey, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------------------
// This Month's spending — number + 5×7 calendar grid + top category
// ---------------------------------------------------------------------------
@Composable
private fun MonthSpendingCard(state: InsightsState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(text = "This Months spending", color = BodyMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "ETB ${moneyShort(state.monthSpending)}",
            color = Headline,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp
        )
        Spacer(Modifier.height(12.dp))
        WeekHeaderRow()
        Spacer(Modifier.height(6.dp))
        MonthGrid(state.monthDayValues, state.monthMaxDay)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Top category: ", color = FooterGrey, fontSize = 12.sp)
            Text(
                text = state.topCategory.ifBlank { "—" },
                color = Headline,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WeekHeaderRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf("S", "M", "T", "W", "T", "F", "S").forEach { label ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(text = label, color = BodyMuted, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MonthGrid(values: List<Double>, max: Double) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(7) { col ->
                    val idx = row * 7 + col
                    val v = values.getOrNull(idx) ?: 0.0
                    val intensity = if (max > 0) (v / max).coerceIn(0.0, 1.0) else 0.0
                    val fill = if (intensity > 0) {
                        Headline.copy(alpha = (0.15 + intensity * 0.5).toFloat())
                    } else {
                        Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(fill)
                            .border(1.dp, GridGrey, RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Spending AVG card
// ---------------------------------------------------------------------------
@Composable
private fun SpendingAvgCard(state: InsightsState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text(text = "Spending", color = BodyMuted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "AVG ${moneyShort(state.monthAvgDaily).removeSuffix(".00")} per day",
            color = Headline,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(14.dp))
        WeekBarChart(state.weekBars)
    }
}

@Composable
private fun WeekBarChart(values: List<Double>) {
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val labels = listOf("S", "M", "T", "W", "T", "F", "S")
    Column {
        // Faint guide lines
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 18.dp)) {
                listOf(0.33f, 0.66f, 1f).forEach { f ->
                    drawLine(
                        color = DividerCol,
                        start = Offset(0f, size.height * f),
                        end = Offset(size.width, size.height * f),
                        strokeWidth = 1f
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 12.dp, bottom = 18.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                values.forEach { v ->
                    val ratio = if (v > 0) ((v / max).coerceIn(0.15, 1.0)).toFloat() else 0f
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(((80 * ratio).toInt()).dp.coerceAtLeast(0.dp))
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(BarGrey)
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEach { l ->
                Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    Text(l, color = BodyMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Donut chart
// ---------------------------------------------------------------------------
@Composable
private fun DonutCard(state: InsightsState) {
    val slices = state.categoryTotals.filter { it.total > 0 }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        if (slices.isEmpty()) {
            Text(text = "Categorise transactions to see the breakdown", color = FooterGrey, fontSize = 13.sp)
        } else {
            val total = slices.sumOf { it.total }
            Canvas(modifier = Modifier.size(180.dp)) {
                val stroke = size.minDimension * 0.22f
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                var startAngle = -90f
                slices.forEachIndexed { i, slice ->
                    val sweep = ((slice.total / total) * 360.0).toFloat()
                    drawArc(
                        color = DonutColors[i % DonutColors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke)
                    )
                    startAngle += sweep
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Category list
// ---------------------------------------------------------------------------
@Composable
private fun CategoryListCard(state: InsightsState, onCategoryClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total spend by Category",
                color = Headline,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(text = "This month", color = FooterGrey, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))

        state.categoryTotals.forEachIndexed { i, ct ->
            CategoryRow(
                category = ct,
                accent = DonutColors[i % DonutColors.size],
                onClick = { onCategoryClick(ct.category) }
            )
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryTotal, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryIcon(category.category, accent = accent)
        Spacer(Modifier.width(12.dp))
        Text(text = category.category, color = Headline, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val color = if (category.total > 0) Headline else FooterGrey
        Text(
            text = if (category.total > 0) "- ${moneyShort(category.total)}" else "—",
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun CategoryIcon(category: String, accent: Color) {
    val emoji = emojiFor(category)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 18.sp)
    }
}

private fun emojiFor(category: String): String = when (category.lowercase(Locale.US)) {
    "food" -> "🍩"                       // donut
    "coffee and refreshments" -> "☕"            // coffee cup
    "bills" -> "💡"                       // bulb
    "loan" -> "🔄"                        // refresh arrows
    "drinks and fun" -> "🔥"               // fire
    "transport" -> "🛵"                    // scooter
    else -> "💳"                          // card
}

// ---------------------------------------------------------------------------
// Uncategorized pill
// ---------------------------------------------------------------------------
@Composable
private fun UncategorizedPill(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(InnerDark),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "i", color = Headline, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$count uncategorized transactions",
            color = Headline,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(text = "›", color = Headline, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
private fun moneyShort(amount: Double): String = String.format(Locale.US, "%,.2f", amount)
