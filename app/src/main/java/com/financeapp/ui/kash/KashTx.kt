package com.financeapp.ui.kash

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.isOwnAccountTransfer
import com.financeapp.data.model.netAmount
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.kash.components.KashDirection
import com.financeapp.ui.kash.components.KashMoney
import com.financeapp.ui.kash.components.KashTransactionRow
import com.financeapp.ui.kash.components.kashBank
import com.financeapp.util.KashDates

/*
 * How a TransactionEntity reads in the new UI. One place, so Home, History,
 * Account detail, Category detail and Review all agree.
 */

/** In / out / own-account transfer (not counted in totals). */
fun TransactionEntity.kashDirection(): KashDirection = when {
    isOwnAccountTransfer() -> KashDirection.Transfer
    type == TransactionType.CREDIT.name -> KashDirection.In
    else -> KashDirection.Out
}

/** Short account label for row sublines: "CBE", "Telebirr", "CBE Birr". */
fun TransactionEntity.accountShort(): String = kashBank(bankName).shortName

/** The row name: who was paid / who paid, else the bank. */
fun TransactionEntity.displayName(): String =
    counterparty?.trim()?.takeIf { it.isNotBlank() } ?: accountShort()

/** Masked account number "•••• 3121", or null. */
fun TransactionEntity.maskedAccount(): String? =
    accountNumber?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 }?.let { "•••• $it" }

/** "Telebirr · •••• 3121" or just "Telebirr". */
fun TransactionEntity.accountLine(): String =
    listOfNotNull(accountShort(), maskedAccount()).joinToString(" · ")

/** A day's net for a DayHeader: "−737.00" / "+160.00" / "0.00". */
fun dayNet(rows: List<TransactionEntity>): String {
    val net = rows.sumOf { it.netAmount() }
    val sign = when {
        net < 0 -> KashMoney.MINUS
        net > 0 -> KashMoney.PLUS
        else -> ""
    }
    return sign + KashMoney.amount(net)
}

/** Groups rows by calendar day, newest day first, keeping row order. */
fun groupByDay(rows: List<TransactionEntity>): List<Pair<Long, List<TransactionEntity>>> =
    rows.groupBy { KashDates.startOfDay(it.dateTime) }.toList().sortedByDescending { it.first }

/**
 * A transaction row. [showAccount] off on an account's own page;
 * [showCategory] off on a category's own page. [time] defaults to the time
 * of day (rows sit under a day header).
 */
@Composable
fun KashTxRow(
    tx: TransactionEntity,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showAccount: Boolean = true,
    showCategory: Boolean = true,
    time: String = KashDates.time(tx.dateTime),
    showDivider: Boolean = true,
) {
    KashTransactionRow(
        name = tx.displayName(),
        amount = tx.amount,
        direction = tx.kashDirection(),
        modifier = modifier,
        account = if (showAccount) tx.accountShort() else null,
        time = time,
        category = if (showCategory) tx.category?.takeIf { it.isNotBlank() } else null,
        showDivider = showDivider,
        onClick = onClick,
    )
}
