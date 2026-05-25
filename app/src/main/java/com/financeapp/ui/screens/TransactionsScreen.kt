package com.financeapp.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.R
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.repository.ErrorLogRepository
import com.financeapp.export.TransactionCsv
import com.financeapp.parsing.TransactionType
import com.financeapp.sms.SmsIngestService
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.CoralPrimary
import com.financeapp.ui.theme.PureWhite
import com.financeapp.viewmodel.DayGroup
import com.financeapp.viewmodel.TransactionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

// =============================================================================
// History (Transactions) — pixel-faithful reproduction of
//   design/history/History_Dark.png
//
//   1. Header           — greeting + name + profile button (same as Home)
//   2. History card     — "Transactions" / "History" + calendar/download
//                          buttons, search field, filter pills
//   3. Day sections     — Today / April 6 / … with net + transaction rows
//   4. Load more…       — outline pill
//   5. Bottom tab bar lives in AppNavigation.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: TransactionsViewModel, onTxClick: (Long) -> Unit) {
    val groups by vm.transactions.collectAsState()
    val filter by vm.filter.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isExporting by remember { mutableStateOf(false) }
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            isExporting = true
            scope.launch(Dispatchers.IO) {
                val result = runCatching {
                    val txs = vm.getExportTransactions()
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                        writer.write("﻿")
                        TransactionCsv.writeTo(writer, txs)
                    } ?: error("Unable to write to selected destination")
                    txs.size
                }
                withContext(Dispatchers.Main) {
                    isExporting = false
                    if (result.isSuccess) {
                        Toast.makeText(context, "Exported ${result.getOrThrow()} transactions", Toast.LENGTH_LONG).show()
                    } else {
                        result.exceptionOrNull()?.let { ex ->
                            scope.launch(Dispatchers.IO) {
                                ErrorLogRepository.fromContext(context).log("CSV export failed", ex)
                            }
                        }
                        Toast.makeText(context, "CSV export failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = androidx.compose.material3.rememberDatePickerState()

    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            SmsIngestService.start(context)
            kotlinx.coroutines.delay(1500)
            pullRefreshState.endRefresh()
        }
    }

    if (showDatePicker) {
        val pickerColors = DatePickerDefaults.colors(
            containerColor = CardBg,
            titleContentColor = Headline,
            headlineContentColor = Headline,
            weekdayContentColor = BodyMuted,
            subheadContentColor = Headline,
            navigationContentColor = Headline,
            yearContentColor = Headline,
            currentYearContentColor = Coral,
            selectedYearContentColor = PageBg,
            selectedYearContainerColor = Coral,
            dayContentColor = Headline,
            selectedDayContentColor = PageBg,
            selectedDayContainerColor = Coral,
            todayContentColor = Coral,
            todayDateBorderColor = Coral,
            disabledDayContentColor = FooterGrey
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showDatePicker = false
                    datePickerState.selectedDateMillis?.let { ms ->
                        vm.setDateFilter(ms, ms + 86_400_000L - 1)
                    }
                }) { Text("Apply", color = Coral) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = BodyMuted) }
            },
            colors = pickerColors
        ) {
            DatePicker(state = datePickerState, colors = pickerColors)
        }
    }

    Box(Modifier.fillMaxSize().background(PageBg).nestedScroll(pullRefreshState.nestedScrollConnection)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { HistoryHeader(userName = "" /* shown only on Home; History uses generic greeting */) }

            item {
                HistoryCard(
                    searchQuery = filter.searchQuery,
                    activeType = filter.type,
                    onSearchChange = { vm.setSearch(it) },
                    onTypeChange = { vm.setType(it) },
                    onCalendarClick = { showDatePicker = true },
                    onDownloadClick = {
                        val ts = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                        exportLauncher.launch("finance_transactions_$ts.csv")
                    },
                    isExporting = isExporting
                )
            }

            if (groups.isEmpty()) {
                item { EmptyState() }
            } else {
                groups.forEach { group ->
                    item(key = "g-${group.dateKey}") {
                        DaySection(group = group, onTxClick = onTxClick)
                    }
                }
                if (filter.searchQuery.isBlank() && filter.fromMs == 0L) {
                    item { LoadMorePill(onClick = { vm.loadMore() }) }
                }
            }
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = CardBg,
            contentColor = Headline
        )
    }
}

// ---------------------------------------------------------------------------
// Header (greeting + profile button — mirrors Home)
// ---------------------------------------------------------------------------
@Composable
private fun HistoryHeader(userName: String) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else      -> "Good evening"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = greeting, color = Headline, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (userName.isBlank()) "Welcome" else userName,
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
// History card (title + calendar/download + search + pills)
// ---------------------------------------------------------------------------
private data class HistoryChip(val label: String, val type: String?)

@Composable
private fun HistoryCard(
    searchQuery: String,
    activeType: String?,
    onSearchChange: (String) -> Unit,
    onTypeChange: (String?) -> Unit,
    onCalendarClick: () -> Unit,
    onDownloadClick: () -> Unit,
    isExporting: Boolean
) {
    val chips = listOf(
        HistoryChip("ALL", null),
        HistoryChip("CREDITED", TransactionType.CREDIT.name),
        HistoryChip("DEBIT",    TransactionType.DEBIT.name),
        HistoryChip("UNKNOWN",  TransactionType.UNKNOWN.name)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Transactions", color = FooterGrey, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Text(text = "History", color = Headline, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            IconTile(iconRes = R.drawable.ic_calendar_dark, contentDescription = "Pick date", onClick = onCalendarClick)
            Spacer(Modifier.width(10.dp))
            IconTile(
                iconRes = R.drawable.ic_download_dark,
                contentDescription = "Export CSV",
                onClick = onDownloadClick,
                loading = isExporting
            )
        }

        Spacer(Modifier.height(14.dp))

        // Search field (clickable text-display style; tap funnels into typed search)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(InnerDark)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_search_dark),
                contentDescription = "Search",
                tint = Coral,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            // Inline editable text using BasicTextField so the design stays clean.
            androidx.compose.foundation.text.BasicTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Headline,
                    fontSize = 13.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Coral),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (searchQuery.isEmpty()) {
                        Text(text = "Search by name, bank, date ...", color = FooterGrey, fontSize = 13.sp)
                    }
                    inner()
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { chip ->
                val selected = activeType == chip.type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Coral.copy(alpha = 0.15f) else Color.Transparent)
                        .border(1.dp, if (selected) Coral else Color(0x33FFFFFF), RoundedCornerShape(50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTypeChange(if (selected) null else chip.type) }
                        )
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = chip.label,
                        color = if (selected) Coral else Headline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun IconTile(iconRes: Int, contentDescription: String, onClick: () -> Unit, loading: Boolean = false) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(InnerDark)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !loading,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Headline)
        } else {
            Icon(painter = painterResource(id = iconRes), contentDescription = contentDescription, tint = Headline, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Day section + row (mirrors Home styling)
// ---------------------------------------------------------------------------
@Composable
private fun DaySection(group: DayGroup, onTxClick: (Long) -> Unit) {
    val net = group.transactions.sumOf { netValue(it) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = group.dateLabel, color = Headline, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${group.transactions.size} transactions",
                    color = FooterGrey,
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Net", color = FooterGrey, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = signedShort(net),
                    color = if (net < 0) Coral else IncomeGreen,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
        Spacer(Modifier.height(4.dp))

        group.transactions.forEachIndexed { i, tx ->
            HistoryRow(tx = tx, onClick = { onTxClick(tx.id) })
            if (i != group.transactions.lastIndex) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
            }
        }
    }
}

@Composable
private fun HistoryRow(tx: TransactionEntity, onClick: () -> Unit) {
    val outgoing = tx.type != TransactionType.CREDIT.name
    val color = if (outgoing) Coral else IncomeGreen
    val iconRes = if (outgoing) R.drawable.ic_expense else R.drawable.ic_income

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(InnerDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.counterparty?.takeIf { it.isNotBlank() } ?: tx.sender,
                color = Headline,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tx.bankName,
                color = FooterGrey,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (outgoing) "-" else "+"} ${moneyShort(tx.amount)}",
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            tx.balance?.let { bal ->
                Spacer(Modifier.height(2.dp))
                Text(text = "bal ${moneyShort(bal)}", color = FooterGrey, fontSize = 11.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Load more pill + empty state
// ---------------------------------------------------------------------------
@Composable
private fun LoadMorePill(onClick: () -> Unit) {
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
                .padding(horizontal = 32.dp, vertical = 14.dp)
        ) {
            Text(text = "Load more...", color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(18.dp)
    ) {
        Text(text = "No transactions match this view.", color = BodyMuted, fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
private fun netValue(tx: TransactionEntity): Double =
    if (tx.type == TransactionType.CREDIT.name) tx.amount else -tx.amount

private fun moneyShort(amount: Double): String = String.format(Locale.US, "%,.2f", amount)

private fun signedShort(amount: Double): String {
    val a = moneyShort(abs(amount))
    return if (amount < 0) "-$a" else "+$a"
}
