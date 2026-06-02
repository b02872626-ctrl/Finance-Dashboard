package com.financeapp.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.R
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.components.CategorySelector
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.CoralPrimary
import com.financeapp.ui.theme.PureWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =============================================================================
// Transaction Detail — pixel-faithful reproduction of
//   design/history/Accounts_light_Option 9.png  (default)
//   design/history/Accounts_light_Option 11.png (Show original SMS expanded)
// =============================================================================

private val PageBg     = AppBackground
private val CardBg     = Color(0xFF231E1A)
private val InnerDark  = Color(0xFF15110F)
private val Headline   = AppBlue
private val Coral      = CoralPrimary
private val BodyMuted  = Color(0xFFB7B3AC)
private val FooterGrey = Color(0xFF7C7872)
private val DividerCol = Color(0x1FFFFFFF)

@Composable
fun TransactionDetailScreen(
    tx: TransactionEntity,
    categories: List<String>,
    onCategorySelected: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showRaw by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    val category = tx.category ?: typeLabel(tx.type)
    val outgoing = tx.type != TransactionType.CREDIT.name
    val balanceColor = if (outgoing) Coral else Headline

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row — back + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
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
                Spacer(Modifier.width(8.dp))
                Text(text = "Transaction Details", color = Headline, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }

            // Hero card — category / amount / bank · account
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showCategorySheet = true }
                    )
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Text(text = category, color = BodyMuted, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${moneyShort(tx.amount)} ETB",
                    color = Headline,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tx.bankName,
                        color = Headline,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    tx.accountNumber?.takeIf { it.isNotBlank() }?.let { acct ->
                        Text(text = acct, color = FooterGrey, fontSize = 14.sp)
                    }
                }
            }

            // Details card — labeled rows
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardBg)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                tx.counterparty?.takeIf { it.isNotBlank() }?.let {
                    DetailRow(label = receiverLabel(tx.type), value = it)
                }
                tx.accountNumber?.takeIf { it.isNotBlank() }?.let {
                    DetailRow(label = "${receiverLabel(tx.type)} Account", value = it)
                }
                tx.refNumber?.takeIf { it.isNotBlank() }?.let {
                    DetailRow(label = "Reference Num", value = it)
                }
                DetailRow(
                    label = "Date & time",
                    // Respects the user's Settings → Calendar toggle (Gregorian/Ethiopian).
                    value = com.financeapp.util.CalendarFormatter.formatLong(
                        timestampMs = tx.dateTime,
                        calendarSystem = com.financeapp.util.LocalCalendarSystem.current
                    )
                )
                tx.balance?.let {
                    DetailRow(
                        label = if (outgoing) "Balance after expence" else "Balance after income",
                        value = "ETB ${moneyShort(it)}",
                        valueColor = balanceColor,
                        valueBold = true
                    )
                }
                tx.serviceCharge?.takeIf { it > 0 }?.let {
                    DetailRow(label = "Service charge", value = "ETB ${moneyShort(it)}")
                }
                tx.vat?.takeIf { it > 0 }?.let {
                    DetailRow(label = "VAT", value = "ETB ${moneyShort(it)}")
                }
                tx.disasterFund?.takeIf { it > 0 }?.let {
                    DetailRow(label = "Disaster fund", value = "ETB ${moneyShort(it)}")
                }
                tx.totalCharged?.takeIf { it > 0 }?.let {
                    DetailRow(
                        label = "Total charge",
                        value = "ETB ${moneyShort(it)}",
                        valueColor = Coral,
                        valueBold = true
                    )
                }
            }

            // Receipt button (only if there's a URL)
            tx.receiptLink?.takeIf { it.isNotBlank() }?.let { url ->
                CoralPill(text = "Open ${tx.bankName.split(" ").first()} receipt") {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure {
                        Toast.makeText(context, "Couldn't open receipt link", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Show / hide original SMS
            DarkPill(
                text = if (showRaw) "Hide original SMS" else "Show original SMS",
                onClick = { showRaw = !showRaw }
            )

            if (showRaw) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(InnerDark)
                        .padding(16.dp)
                ) {
                    Text(
                        text = tx.rawBody,
                        color = BodyMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }

    if (showCategorySheet) {
        CategoryPickerSheet(
            currentCategory = tx.category,
            categories = categories,
            onDismiss = { showCategorySheet = false },
            onSelect = {
                onCategorySelected(it)
                showCategorySheet = false
            },
            onAdd = onAddCategory
        )
    }
}

// ---------------------------------------------------------------------------
// Row + buttons
// ---------------------------------------------------------------------------
@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Headline,
    valueBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Headline, fontSize = 14.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 14.sp,
            fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoralPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(Coral)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = PureWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DarkPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(CardBg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Headline, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------------------
// Category picker — minimal, lets existing CategorySelector slot in dark form
// ---------------------------------------------------------------------------
@Composable
private fun CategoryPickerSheet(
    currentCategory: String?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(CardBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Category", color = Headline, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DividerCol))
            CategorySelector(
                categories = categories,
                selectedCategory = currentCategory,
                onCategorySelected = onSelect,
                onAddCategory = onAdd
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
private fun moneyShort(amount: Double): String = String.format(Locale.US, "%,.2f", amount)

private fun formatDateTime(epochMs: Long): String {
    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.US)
    return fmt.format(Date(epochMs))
}

private fun receiverLabel(type: String): String = when (type) {
    TransactionType.CREDIT.name -> "Sender"
    else -> "Receiver"
}

private fun typeLabel(type: String): String = when (type) {
    TransactionType.CREDIT.name -> "Income"
    TransactionType.DEBIT.name -> "Expense"
    TransactionType.TRANSFER_OUT.name -> "Transfer"
    TransactionType.PAYMENT.name -> "Payment"
    else -> "Unknown"
}
