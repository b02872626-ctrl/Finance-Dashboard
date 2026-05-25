package com.financeapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.R
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.AppSurface
import com.financeapp.ui.theme.CharcoalText
import com.financeapp.ui.theme.CoralPrimary
import com.financeapp.ui.theme.MutedGray
import com.financeapp.ui.theme.PureWhite
import com.financeapp.ui.theme.SlateGray
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

// =============================================================================
// Onboarding screens — pixel-faithful reproduction of
//   design/onboarding/reference/onboarding_Welcome_{Dark, Light, Light-1,
//   Light-2, Light-3}.png
//
// 5 sequential steps:
//   1. Welcome           (Dark)     — Get started / Sign in
//   2. Allow SMS access  (Light-3)  — 3 benefit cards + Allow Access
//   3. Ready to import   (Light-2)  — txn count + bank toggles + Import
//   4. What do you track (Light-1)  — category toggles + Continue
//   5. You're all set    (Light)    — stats + chart + Go to dashboard
// =============================================================================

private val PageBg     = AppBackground       // #0E1614
private val CardBg     = Color(0xFF231E1A)   // warm dark brown card surface
private val InnerDark  = Color(0xFF15110F)   // recessed surface (icon backings, OFF toggle track)
private val Headline   = AppBlue             // teal #7FE3CB
private val BodyMuted  = Color(0xFFB7B3AC)   // body / subhead grey
private val FooterGrey = Color(0xFF7C7872)
private val Coral      = CoralPrimary        // #FB5D53
private val ChartGrey  = Color(0xFF504C46)   // non-highlight bars

@Composable
fun OnboardingScreen(
    hasSmsAccess: Boolean,
    transactions: List<TransactionEntity>,
    onRequestSmsAccess: () -> Unit,
    onFinish: () -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(1) }

    // Auto-advance once SMS access is granted on step 2.
    LaunchedEffect(hasSmsAccess) {
        if (step == 2 && hasSmsAccess) step = 3
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .systemBarsPadding()
    ) {
        when (step) {
            1 -> WelcomeStep(
                onGetStarted = { step = 2 },
                onSignIn = onFinish // user is already authenticated to reach this screen
            )
            2 -> SmsAccessStep(onAllow = onRequestSmsAccess)
            3 -> ReadyToImportStep(
                transactions = transactions,
                onImport = { step = 4 },
                onReview = { step = 4 }
            )
            4 -> CategoriesStep(onContinue = { step = 5 })
            5 -> AllSetStep(transactions = transactions, onGoDashboard = onFinish)
        }
    }
}

// ---------------------------------------------------------------------------
// Step 1 — Welcome
// ---------------------------------------------------------------------------
@Composable
private fun WelcomeStep(onGetStarted: () -> Unit, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(72.dp))

        Text(
            text = "Tracking\nyour finance\nshouldn't feel like\nhomework",
            color = Headline,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.5).sp
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = "We help you track your finances\nwith out asking you to write down\nyour expenses daily",
            color = BodyMuted,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        // Private & Secure card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(CardBg)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(InnerDark),
                contentAlignment = Alignment.Center
            ) {
                AssetIcon(R.drawable.ic_shield, size = 18.dp, tint = Coral)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Private & Secure",
                    color = Headline,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Your SMS data is processed\nlocally and never shared.",
                    color = BodyMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        CoralPillButton(
            text = "Get started",
            onClick = onGetStarted,
            trailingArrow = false,
            textColor = Headline
        )
        Spacer(Modifier.height(12.dp))
        DarkPillButton(text = "Sign in", onClick = onSignIn)

        Spacer(Modifier.height(20.dp))
        FooterCaption()
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------------------
// Step 2 — Allow SMS access
// ---------------------------------------------------------------------------
@Composable
private fun SmsAccessStep(onAllow: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        AssetIcon(R.drawable.ic_message, size = 50.dp, tint = Coral)

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Allow SMS access",
            color = Headline,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Connect your bank alerts to your dashboard\nfor a seamless financial overview.",
            color = BodyMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        BenefitCard(
            iconRes = R.drawable.ic_search,
            iconSize = 22.dp,
            title = "detect transaction messages",
            description = "Our system scans only for\nincoming bank and merchant\ntransaction alerts."
        )
        Spacer(Modifier.height(14.dp))
        BenefitCard(
            iconRes = R.drawable.ic_auto_import_spending,
            iconSize = 22.dp,
            title = "auto-import spending",
            description = "Every purchase is automatically\ncategorized, keeping your budget\nupdated in real-time."
        )
        Spacer(Modifier.height(14.dp))
        BenefitCard(
            iconRes = R.drawable.ic_reduce_manual_entry,
            iconSize = 22.dp,
            title = "reduce manual entry",
            description = "Stop typing in every coffee and\ngrocery bill. Let our atelier handle\nthe paperwork."
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(28.dp))

        CoralPillButton(text = "Allow Access", onClick = onAllow, textColor = PureWhite)
        Spacer(Modifier.height(18.dp))
        FooterCaption()
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun BenefitCard(iconRes: Int, iconSize: Dp, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.TopStart) {
            AssetIcon(iconRes, size = iconSize, tint = Coral)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                color = Headline,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                color = BodyMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3 — Ready to import
// ---------------------------------------------------------------------------
@Composable
private fun ReadyToImportStep(
    transactions: List<TransactionEntity>,
    onImport: () -> Unit,
    onReview: () -> Unit
) {
    // Real bank list if available; fall back to the design mock for visual fidelity.
    val realBanks = remember(transactions) {
        transactions.groupBy { it.bankName }
            .map { (bank, txs) -> bank to (txs.firstOrNull()?.accountNumber ?: "") }
            .take(4)
    }
    val banks = if (realBanks.size >= 2) realBanks else listOf(
        "Commercial Bank"   to "1000  ••••  8291",
        "Bank of Abyssinia" to "90  ••••  1104",
        "Telebirr"          to "251  ••••  9920",
        "Awash bank"        to "0924  ••••  9920"
    )

    val toggles = remember(banks) {
        mutableStateMapOf<String, Boolean>().apply {
            banks.forEachIndexed { i, (name, _) -> put(name, i < 3) } // first 3 ON
        }
    }
    val activeCount = toggles.count { it.value }

    val txCount = if (transactions.isNotEmpty()) transactions.size else 1375

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Ready to import",
                color = Headline,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "We've successfully analyzed your\nSMS records. Here's what we found across\nyour accounts.",
                color = BodyMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // Big count card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardBg)
                    .padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = NumberFormat.getNumberInstance(Locale.US).format(txCount),
                    color = Headline,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "TRANSACTIONS DETECTED",
                    color = Headline,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(14.dp))
                // Date chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(InnerDark)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📅", fontSize = 13.sp)   // 📅 calendar emoji
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Last 18 months",
                            color = BodyMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Detected sources header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detected Sources",
                    color = Headline,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$activeCount ACTIVE",
                    color = FooterGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            ThinDivider()

            banks.forEach { (name, masked) ->
                ToggleRow(
                    title = name,
                    subtitle = masked,
                    checked = toggles[name] == true,
                    onCheckedChange = { toggles[name] = it }
                )
                ThinDivider()
            }

            Spacer(Modifier.height(140.dp))
        }

        // Sticky bottom CTAs in a raised card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(CardBg)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column {
                CoralPillButton(
                    text = "Import transactions",
                    onClick = onImport,
                    trailingArrow = true,
                    textColor = PureWhite
                )
                Spacer(Modifier.height(10.dp))
                OutlinePillButton(text = "Review first", onClick = onReview)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 4 — What do you track?
// ---------------------------------------------------------------------------
private data class TrackCategory(val title: String, val subtitle: String)

@Composable
private fun CategoriesStep(onContinue: () -> Unit) {
    val categories = listOf(
        TrackCategory("Food",      "Dining, groceries, snacks"),
        TrackCategory("Transport", "Commute, fuel, repairs"),
        TrackCategory("Bills",     "Rent, utilities, taxes"),
        TrackCategory("Airtime",   "Data, call credit, sub")
    )
    val toggles = remember {
        mutableStateMapOf<String, Boolean>().apply {
            categories.forEachIndexed { i, c -> put(c.title, i < 3) }   // first 3 ON
        }
    }
    val selectedCount = toggles.count { it.value }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = "What do you track?",
                color = Headline,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Select the categories most relevant to\nyour daily spending.",
                color = BodyMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Categories",
                    color = Headline,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$selectedCount SELECTED",
                    color = FooterGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            ThinDivider()

            categories.forEach { c ->
                ToggleRow(
                    title = c.title,
                    subtitle = c.subtitle,
                    checked = toggles[c.title] == true,
                    onCheckedChange = { toggles[c.title] = it }
                )
                ThinDivider()
            }

            Spacer(Modifier.height(120.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(CardBg)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column {
                CoralPillButton(
                    text = "Continue",
                    onClick = onContinue,
                    trailingArrow = true,
                    textColor = PureWhite
                )
                Spacer(Modifier.height(10.dp))
                AddMoreCategoriesButton(onClick = { /* TODO: open add-category dialog */ })
            }
        }
    }
}

@Composable
private fun AddMoreCategoriesButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF004D44)),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = PureWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Add more Categories",
                color = Headline,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 5 — You're all set!
// ---------------------------------------------------------------------------
@Composable
private fun AllSetStep(transactions: List<TransactionEntity>, onGoDashboard: () -> Unit) {
    val expenseTypes = setOf(
        TransactionType.DEBIT.name,
        TransactionType.TRANSFER_OUT.name,
        TransactionType.PAYMENT.name
    )
    val txCount = if (transactions.isNotEmpty()) transactions.size else 1375
    val topMerchant = remember(transactions) {
        transactions
            .filter { it.type in expenseTypes }
            .groupBy { (it.counterparty ?: "").trim().takeIf(String::isNotBlank) ?: it.bankName }
            .maxByOrNull { (_, txs) -> txs.sumOf { it.amount } }
            ?.key
            ?: "Kaldis Coffee"
    }
    val monthly = remember(transactions) { computeMonthly(transactions, expenseTypes) }
    val avg = if (monthly.values.any { it > 0 }) monthly.values.average() else 12_400.0

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = "You're all set!",
                color = Headline,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Your financial identity has been\nmeticulously curated. Here is a brief\nlook at your journey so far.",
                color = BodyMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            // Card 1 — Activity overview
            StatCard {
                Text("Activity overview", color = FooterGrey, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = NumberFormat.getNumberInstance(Locale.US).format(txCount),
                    color = Coral,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Transactions\nimported",
                    color = Headline,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 30.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            // Card 2 — Frequent Merchant
            StatCard {
                Text("Frequent Merchant", color = FooterGrey, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Coral, fontWeight = FontWeight.Bold)) {
                            append(topMerchant)
                        }
                        append("\n")
                        withStyle(SpanStyle(color = Headline, fontWeight = FontWeight.SemiBold)) {
                            append("is your Biggest\nmerchant:")
                        }
                    },
                    fontSize = 24.sp,
                    lineHeight = 30.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            // Card 3 — Monthly Lifestyle (chart + spent line)
            StatCard {
                Text("Monthly Lifestyle", color = FooterGrey, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                MonthlyBarChart(monthly)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = Coral, fontWeight = FontWeight.Bold)) {
                            append("You've Spent\n")
                        }
                        withStyle(SpanStyle(color = Coral, fontWeight = FontWeight.Bold)) {
                            append(NumberFormat.getNumberInstance(Locale.US).format(avg.toLong()))
                            append(" in average")
                        }
                        append("\n")
                        withStyle(SpanStyle(color = Headline, fontWeight = FontWeight.SemiBold)) {
                            append("every month")
                        }
                    },
                    fontSize = 22.sp,
                    lineHeight = 28.sp
                )
            }

            Spacer(Modifier.height(120.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(CardBg)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            CoralPillButton(
                text = "Go to dashboard",
                onClick = onGoDashboard,
                trailingArrow = true,
                textColor = PureWhite
            )
        }
    }
}

@Composable
private fun StatCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(20.dp)
    ) {
        content()
    }
}

@Composable
private fun MonthlyBarChart(monthly: LinkedHashMap<String, Double>) {
    val pairs = if (monthly.isEmpty() || monthly.values.all { it <= 0 }) {
        // Design mock so the chart renders meaningfully on first install.
        linkedMapOf(
            "Nov" to 5_500.0,
            "Dec" to 6_200.0,
            "Jan" to 7_000.0,
            "Feb" to 13_500.0,
            "Mar" to 7_400.0,
            "April" to 8_900.0
        )
    } else monthly

    val maxValue = pairs.values.max().coerceAtLeast(1.0)
    val highlightKey = pairs.maxByOrNull { it.value }?.key

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            pairs.forEach { (label, value) ->
                val ratio = (value / maxValue).coerceIn(0.18, 1.0)
                val isHighlight = label == highlightKey
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height((110 * ratio).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(if (isHighlight) Coral else ChartGrey)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            pairs.keys.forEach { label ->
                Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                    Text(label, color = BodyMuted, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun computeMonthly(
    transactions: List<TransactionEntity>,
    expenseTypes: Set<String>
): LinkedHashMap<String, Double> {
    val labels = listOf("Nov", "Dec", "Jan", "Feb", "Mar", "April")
    val result = linkedMapOf<String, Double>()
    labels.forEach { result[it] = 0.0 }
    if (transactions.isEmpty()) return result

    val cal = Calendar.getInstance()
    val nowMonth = cal.get(Calendar.MONTH)
    val nowYear = cal.get(Calendar.YEAR)

    transactions.filter { it.type in expenseTypes }.forEach { tx ->
        val c = Calendar.getInstance().apply { timeInMillis = tx.dateTime }
        val monthsBack = (nowYear - c.get(Calendar.YEAR)) * 12 + (nowMonth - c.get(Calendar.MONTH))
        if (monthsBack in 0..5) {
            val key = labels[5 - monthsBack]
            result[key] = (result[key] ?: 0.0) + tx.amount
        }
    }
    return result
}

// ---------------------------------------------------------------------------
// Shared primitives
// ---------------------------------------------------------------------------
@Composable
private fun CoralPillButton(
    text: String,
    onClick: () -> Unit,
    trailingArrow: Boolean = false,
    textColor: Color = PureWhite
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(Coral)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (trailingArrow) {
                Spacer(Modifier.width(10.dp))
                AssetIcon(R.drawable.ic_arrow_to_right, size = 16.dp, tint = textColor)
            }
        }
    }
}

@Composable
private fun DarkPillButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(50))
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Headline, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun OutlinePillButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FooterCaption() {
    Text(
        text = "DESIGNED FOR PRIVACY  ·  LOCAL PROCESSING",
        color = FooterGrey,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun ThinDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x14FFFFFF))
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Headline,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = BodyMuted,
                fontSize = 13.sp
            )
        }
        CoralToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CoralToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val trackColor = if (checked) Coral else Color(0xFF3A3633)
    val knobPadding = 3.dp
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = knobPadding)
                .size(22.dp)
                .clip(CircleShape)
                .background(PureWhite)
        )
    }
}

@Composable
private fun AssetIcon(
    iconRes: Int,
    size: Dp,
    tint: Color = Coral
) {
    androidx.compose.material3.Icon(
        painter = painterResource(id = iconRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size)
    )
}
