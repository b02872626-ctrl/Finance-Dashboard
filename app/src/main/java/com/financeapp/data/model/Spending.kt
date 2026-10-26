package com.financeapp.data.model

import com.financeapp.parsing.TransactionType

/**
 * The single definition of "spending" and "income" for every total in the
 * app (Home, Analytics, History day nets, account screens, onboarding stats).
 * Web/app.js mirrors it in isSpending()/isIncome() — keep them in sync.
 *
 * Money moving between the user's own accounts (CBE → Telebirr, CBE → CBE
 * Birr, …) is neither: counting it would double the same money. A row is an
 * own-account move when the parser typed it INTERNAL_TRANSFER, or when the
 * user filed it under [OWN_ACCOUNTS].
 */
object Spending {
    /** Built-in category for transfers between the user's own accounts. */
    const val OWN_ACCOUNTS = "Own accounts"

    /**
     * Custom category names testers created by hand before [OWN_ACCOUNTS]
     * existed. Migrated to it in AppDatabase.MIGRATION_9_10 and hidden from
     * the custom-category list. Compared lowercase.
     */
    val LEGACY_OWN_ACCOUNT_NAMES = setOf("internal", "internal transfer")

    val EXPENSE_TYPES = setOf(
        TransactionType.DEBIT.name,
        TransactionType.TRANSFER_OUT.name,
        TransactionType.PAYMENT.name
    )
}

fun TransactionEntity.isOwnAccountTransfer(): Boolean =
    type == TransactionType.INTERNAL_TRANSFER.name || category == Spending.OWN_ACCOUNTS

fun TransactionEntity.isSpending(): Boolean =
    type in Spending.EXPENSE_TYPES && !isOwnAccountTransfer()

fun TransactionEntity.isIncome(): Boolean =
    type == TransactionType.CREDIT.name && !isOwnAccountTransfer()

/**
 * Signed amount for "Net" lines: + for money in, − for money out, 0 for
 * own-account moves. Other non-credit types (UNKNOWN, …) stay negative, as
 * the day/section nets always treated them.
 */
fun TransactionEntity.netAmount(): Double = when {
    isOwnAccountTransfer() -> 0.0
    type == TransactionType.CREDIT.name -> amount
    else -> -amount
}
