package com.financeapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.financeapp.viewmodel.BankAnalyticsViewModel
import java.util.Locale
import kotlin.math.abs

// =============================================================================
// Account detail — pixel-faithful reproduction of
//   design/Accounts/references/Account_details_light.png
//
//   1. Header — same "Sources / Accounts" greeting as the list page
//   2. Single balance card — bank logo + balance + account number +
//      transaction count
//   3. "Recent Activities" + net amount + transaction rows
//   4. Load more pill
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
fun BankAnalyticsScreen(vm: BankAnalyticsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()

    if (state.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize().background(PageBg),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = Headline) }
        return
    }

    var visibleCount by rememberSaveable(state.bankName) { mutableIntStateOf(8) }
    val visibleTxns = state.transactions.take(visibleCount)
    val net = visibleTxns.sumOf { netValue(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        AccountsHeader(onBack = onBack)
        Spacer(Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AccountBalanceCard(
                    bankName = state.bankName,
                    balance = state.totalBalance,
                    accountNumber = state.accountNumber,
                    txCount = state.txCount
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activities",
                        color = Headline,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
            }

            item {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
            }

            if (visibleTxns.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(CardBg)
                            .padding(20.dp)
                    ) {
                        Text(text = "No activity recorded for this account.", color = BodyMuted, fontSize = 13.sp)
                    }
                }
            } else {
                items(visibleTxns)
                if (visibleCount < state.transactions.size) {
                    item {
                        LoadMorePill(onClick = { visibleCount = (visibleCount + 10).coerceAtMost(state.transactions.size) })
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.items(txns: List<TransactionEntity>) {
    items(items = txns, key = { it.id }) { tx ->
        Column {
            ActivityRow(tx = tx)
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
        }
    }
}

// ---------------------------------------------------------------------------
// Re-uses the same Accounts header structure (but with a back affordance built
// into the profile button area when the user wants to leave).
// ---------------------------------------------------------------------------
@Composable
private fun AccountsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
                Text(text = "Sources", color = Headline, fontSize = 13.sp)
            }
            Text(
                text = "Accounts",
                color = Headline,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
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
// Hero balance card for the selected bank
// ---------------------------------------------------------------------------
@Composable
private fun AccountBalanceCard(
    bankName: String,
    balance: Double,
    accountNumber: String?,
    txCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = moneyShort(balance),
                color = Headline,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.weight(1f)
            )
            BankLogo(bankName = bankName, size = 36.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(text = bankName, color = Headline, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        accountNumber?.takeIf { it.isNotBlank() }?.let {
            Text(text = "Account - $it", color = BodyMuted, fontSize = 12.sp)
            Spacer(Modifier.height(2.dp))
        }
        Text(text = "$txCount transactions Tracked", color = BodyMuted, fontSize = 12.sp)
    }
}

@Composable
private fun BankLogo(bankName: String, size: androidx.compose.ui.unit.Dp) {
    val logoRes = bankLogoFor(bankName)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (logoRes != null) {
            Image(
                painter = painterResource(id = logoRes),
                contentDescription = bankName,
                modifier = Modifier
                    .size(size)
                    .padding(3.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = bankInitial(bankName),
                color = Color(0xFF231E1A),
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun bankLogoFor(name: String): Int? {
    val n = name.lowercase(Locale.US)
    return when {
        "telebirr" in n        -> R.drawable.logo_telebirr
        "abyssinia" in n       -> R.drawable.logo_boa
        "boa" == n             -> R.drawable.logo_boa
        "commercial bank" in n -> R.drawable.logo_cbe
        "cbe" in n             -> R.drawable.logo_cbe
        else                   -> null
    }
}

private fun bankInitial(name: String): String =
    name.trim().split(Regex("\\s+")).firstOrNull()?.firstOrNull()?.uppercase() ?: "?"

// ---------------------------------------------------------------------------
// Activity row (same style as History/Home)
// ---------------------------------------------------------------------------
@Composable
private fun ActivityRow(tx: TransactionEntity) {
    val outgoing = tx.type != TransactionType.CREDIT.name
    val color = if (outgoing) Coral else IncomeGreen
    val iconRes = if (outgoing) R.drawable.ic_expense else R.drawable.ic_income

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
// Load more pill
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
