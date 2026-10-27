package com.financeapp.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashIcon
import com.financeapp.ui.kash.KashIconView
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.accountLine
import com.financeapp.ui.kash.accountShort
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashButtonVariant
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashDirection
import com.financeapp.ui.kash.components.KashDivider
import com.financeapp.ui.kash.components.KashFigure
import com.financeapp.ui.kash.components.KashIconButton
import com.financeapp.ui.kash.components.KashKeyValue
import com.financeapp.ui.kash.components.KashKeyValueList
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.displayName
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.kashDirection
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem

/**
 * Transaction (AppTransaction, AppTransactionNoReceipt, AppTransactionEC,
 * AppSmallTransaction). Four facts on the sheet; reference, charges and the
 * original SMS behind "More details".
 */
@Composable
fun TransactionScreen(
    tx: TransactionEntity,
    customCategories: List<String>,
    recentCategories: List<String>,
    onCategorySelected: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onBack: () -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val context = LocalContext.current
    val cal = LocalCalendarSystem.current
    val dir = tx.kashDirection()
    var pickerOpen by remember { mutableStateOf(false) }
    var newCategoryOpen by remember { mutableStateOf(false) }
    var moreOpen by rememberSaveable(tx.id) { mutableStateOf(false) }

    KashScreen(
        heroGap = 26.dp,
        heroBottom = 30.dp,
        hero = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KashIconButton(KashIcon.Back, "Back", onBack, onSlate = true)
                Spacer(Modifier.weight(1f))
                KashIconButton(KashIcon.Share, "Share", { shareTransaction(context, tx, cal) }, onSlate = true)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (dir) {
                        KashDirection.In -> "Received in ${tx.accountShort()}"
                        KashDirection.Out -> "Paid from ${tx.accountShort()}"
                        KashDirection.Transfer -> "Moved from ${tx.accountShort()}"
                    },
                    style = KashType.Body, color = KashColors.OnSlateMuted,
                )
                KashFigure(tx.amount, sign = KashMoney.sign(dir))
                Text(tx.displayName(), style = KashType.BodyL, color = KashColors.OnSlate, maxLines = 2, overflow = TextOverflow.Ellipsis)
                KashChip(
                    tx.category ?: "Add a category",
                    selected = false,
                    onClick = { pickerOpen = true },
                    onSlate = true,
                    trailing = KashIcon.ChevronDown,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(pad),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KashKeyValueList(
                buildList {
                    add(KashKeyValue(if (dir == KashDirection.In) "From" else "To", tx.displayName()))
                    add(KashKeyValue("Account", tx.accountLine(), numeric = true))
                    add(KashKeyValue("When", KashDates.full(tx.dateTime, cal), numeric = true))
                    tx.balance?.let { add(KashKeyValue("Balance after", KashMoney.amount(it), numeric = true)) }
                }
            )
            MoreDetails(tx, moreOpen) { moreOpen = !moreOpen }
            tx.receiptLink?.takeIf { it.isNotBlank() }?.let { url ->
                Spacer(Modifier.height(12.dp))
                KashButton(
                    "Open bank receipt",
                    {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            .onFailure { Toast.makeText(context, "Couldn't open the receipt. Try again later.", Toast.LENGTH_SHORT).show() }
                    },
                    Modifier.fillMaxWidth(),
                    KashButtonVariant.Secondary,
                    icon = KashIcon.External,
                )
            }
        }
    }

    if (pickerOpen) {
        CategoryPicker(
            tx = null,
            initial = tx.category,
            recent = recentCategories,
            custom = customCategories,
            applyToSimilar = false,
            onToggleApplyToSimilar = null,
            onClose = { pickerOpen = false },
            onNewCategory = { newCategoryOpen = true },
            onUse = { pickerOpen = false; onCategorySelected(it) },
            title = "Category",
        )
    }
    if (newCategoryOpen) {
        NewCategoryDialog(
            onCancel = { newCategoryOpen = false },
            onCreate = { name ->
                newCategoryOpen = false
                pickerOpen = false
                onAddCategory(name)
                onCategorySelected(name)
            },
        )
    }
}

@Composable
private fun MoreDetails(tx: TransactionEntity, open: Boolean, onToggle: () -> Unit) {
    val extra = buildList {
        tx.refNumber?.takeIf { it.isNotBlank() }?.let { add(KashKeyValue("Reference", it, numeric = true)) }
        tx.serviceCharge?.let { add(KashKeyValue("Service charge", KashMoney.amount(it), numeric = true)) }
        tx.vat?.takeIf { it > 0 }?.let { add(KashKeyValue("VAT", KashMoney.amount(it), numeric = true)) }
        tx.disasterFund?.takeIf { it > 0 }?.let { add(KashKeyValue("Disaster fund", KashMoney.amount(it), numeric = true)) }
        tx.totalCharged?.takeIf { it > 0 }?.let { add(KashKeyValue("Total charged", KashMoney.amount(it), numeric = true)) }
    }
    Column {
        Row(
            Modifier.fillMaxWidth().kashClickable(onClick = onToggle).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (open) "Fewer details" else "More details", style = KashType.Body, color = KashColors.Ink, modifier = Modifier.weight(1f))
            KashIconView(KashIcon.ChevronDown, size = 16.dp, tint = KashColors.Ink, modifier = Modifier.rotate(if (open) 180f else 0f))
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(bottom = 6.dp)) {
                if (extra.isNotEmpty()) KashKeyValueList(extra, card = false)
                Column(Modifier.padding(top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Original SMS", style = KashType.Label, color = KashColors.InkMuted)
                    Text(
                        tx.rawBody, style = KashType.Caption, color = KashColors.Ink,
                        modifier = Modifier.fillMaxWidth().clip(KashShapes.Field).background(KashColors.Well).padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
        KashDivider()
    }
}

/**
 * Plain-text summary for the system share sheet. Deliberately leaves out
 * the account balance and full account number.
 */
private fun shareTransaction(context: Context, tx: TransactionEntity, cal: String) {
    val amount = "${KashMoney.amount(tx.amount)} ETB"
    val who = tx.counterparty?.takeIf { it.isNotBlank() }
    val headline = when {
        tx.type == TransactionType.CREDIT.name && who != null -> "Received $amount from $who"
        tx.type == TransactionType.CREDIT.name -> "Received $amount"
        who != null -> "Paid $amount to $who"
        else -> "Paid $amount"
    }
    val text = buildString {
        append(headline).append(" via ").append(tx.bankName)
        append("\n").append(KashDates.full(tx.dateTime, cal))
        tx.refNumber?.takeIf { it.isNotBlank() }?.let { append("\nRef: ").append(it) }
        tx.receiptLink?.takeIf { it.isNotBlank() }?.let { append("\nReceipt: ").append(it) }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share transaction")) }
        .onFailure { Toast.makeText(context, "Couldn't open share sheet", Toast.LENGTH_SHORT).show() }
}
