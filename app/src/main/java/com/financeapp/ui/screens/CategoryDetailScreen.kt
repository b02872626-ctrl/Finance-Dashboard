package com.financeapp.ui.screens

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.R
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.CoralPrimary
import com.financeapp.viewmodel.AnalyticsViewModel
import com.financeapp.viewmodel.CategoryDetailState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// =============================================================================
// Category Detail — pixel-faithful reproduction of
//   design/insights/spending_etails_light.png
// =============================================================================

private val PageBg     = AppBackground
private val CardBg     = Color(0xFF231E1A)
private val InnerDark  = Color(0xFF15110F)
private val Headline   = AppBlue
private val Coral      = CoralPrimary
private val BodyMuted  = Color(0xFFB7B3AC)
private val FooterGrey = Color(0xFF7C7872)
private val DividerCol = Color(0x1FFFFFFF)
private val IncomeGreen = Color(0xFF4CD495)

@Composable
fun CategoryDetailScreen(
    vm: AnalyticsViewModel,
    category: String,
    onBack: () -> Unit,
    onAddTransaction: () -> Unit
) {
    val state by vm.categoryDetail(category).collectAsState()
    val grouped = remember(state.transactions) { buildDayGroups(state.transactions) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Header(title = state.category, onBack = onBack)
        Spacer(Modifier.height(18.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { HeroCard(state) }
            item { StatTiles(state) }
            item {
                Text(
                    text = "Recent History",
                    color = Headline,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (grouped.isEmpty()) {
                item { EmptyState() }
            } else {
                grouped.forEach { group ->
                    item(key = "g-${group.label}") { DaySectionLite(group) }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                AddTransactionsPill(onClick = onAddTransaction)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------
@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_back_dark),
                contentDescription = "Back",
                tint = Headline,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$title Spending",
                color = Headline,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(text = "30-Day View", color = BodyMuted, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_calendar_dark),
                contentDescription = "Calendar",
                tint = Headline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hero monthly card
// ---------------------------------------------------------------------------
@Composable
private fun HeroCard(state: CategoryDetailState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InnerDark),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emojiFor(state.category), fontSize = 26.sp)
        }
        Spacer(Modifier.height(12.dp))
        Text(text = "Total monthly expenditure", color = BodyMuted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${moneyShort(state.monthSpending)} ETB",
            color = Headline,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.4).sp
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(InnerDark)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "↑ ${state.percentOfTotal.toInt()}% of total spend",
                color = BodyMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Today / Average daily tiles
// ---------------------------------------------------------------------------
@Composable
private fun StatTiles(state: CategoryDetailState) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(label = "Todays spend", value = "${moneyShort(state.todaySpending)} ETB", modifier = Modifier.weight(1f))
        StatTile(label = "Average daily spend", value = "${moneyShort(state.avgDailySpend)} ETB", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(InnerDark),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "↗", color = Coral, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(text = label, color = BodyMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(text = value, color = Headline, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Day-grouped activity rows
// ---------------------------------------------------------------------------
private data class CatDayGroup(val label: String, val transactions: List<TransactionEntity>)

@Composable
private fun DaySectionLite(group: CatDayGroup) {
    val net = group.transactions.sumOf { if (it.type == TransactionType.CREDIT.name) it.amount else -it.amount }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = group.label, color = Headline, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Net", color = FooterGrey, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = signedShort(net),
                    color = if (net < 0) Coral else IncomeGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
        group.transactions.forEachIndexed { i, tx ->
            CatRow(tx)
            if (i != group.transactions.lastIndex) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
            }
        }
    }
}

@Composable
private fun CatRow(tx: TransactionEntity) {
    val outgoing = tx.type != TransactionType.CREDIT.name
    val color = if (outgoing) Coral else IncomeGreen
    val iconRes = if (outgoing) R.drawable.ic_expense else R.drawable.ic_income

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InnerDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.counterparty?.takeIf { it.isNotBlank() } ?: tx.sender,
                color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(text = tx.bankName, color = FooterGrey, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (outgoing) "-" else "+"} ${moneyShort(tx.amount)}",
                color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
            )
            tx.balance?.let { bal ->
                Spacer(Modifier.height(2.dp))
                Text(text = "bal ${moneyShort(bal)}", color = FooterGrey, fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Add Transactions pill
// ---------------------------------------------------------------------------
@Composable
private fun AddTransactionsPill(onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(CardBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 28.dp, vertical = 14.dp)
        ) {
            Text(text = "+ Add Transactions", color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(20.dp)
    ) {
        Text(text = "No activity recorded for this category yet.", color = BodyMuted, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
private fun emojiFor(category: String): String = when (category.lowercase(Locale.US)) {
    "food" -> "🍩"
    "coffee and refreshments" -> "☕"
    "bills" -> "💡"
    "loan" -> "🔄"
    "drinks and fun" -> "🔥"
    "transport" -> "🛵"
    else -> "💳"
}

private fun moneyShort(amount: Double): String = String.format(Locale.US, "%,.2f", amount)

private fun signedShort(amount: Double): String {
    val a = moneyShort(abs(amount))
    return if (amount < 0) "-$a" else "+$a"
}

private fun buildDayGroups(transactions: List<TransactionEntity>): List<CatDayGroup> {
    if (transactions.isEmpty()) return emptyList()
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val today = fmt.format(Date())
    val yesterday = fmt.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time)
    return transactions
        .groupBy { fmt.format(Date(it.dateTime)) }
        .entries.sortedByDescending { it.key }
        .map { (key, txs) ->
            val label = when (key) {
                today -> "Today"
                yesterday -> "Yesterday"
                else -> SimpleDateFormat("EEEE", Locale.getDefault()).format(fmt.parse(key) ?: Date())
            }
            CatDayGroup(label, txs.sortedByDescending { it.dateTime })
        }
}
