package com.financeapp.data.accounts

import com.financeapp.data.model.TransactionEntity

/**
 * One account the user holds — a bank, or a specific account within a bank
 * when the user has more than one there (e.g. two CBE accounts).
 */
data class AccountGroup(
    /** Stable identity: bank name, or "bank#suffix" for multi-account banks. */
    val key: String,
    val bankName: String,
    /** Card title: full bank name for single-account banks, "CBE 1" / "CBE 2" otherwise. */
    val label: String,
    /** Masked number as last seen in an SMS, e.g. "1********6155". */
    val accountNumber: String,
    /** Last 4 digits; null when the bank isn't split into accounts. */
    val accountSuffix: String?,
    val transactions: List<TransactionEntity>
)

object AccountGrouping {

    fun shortBankLabel(name: String): String = when {
        name.contains("Commercial Bank", ignoreCase = true) -> "CBE"
        name.contains("Telebirr", ignoreCase = true)        -> "Telebirr"
        name.contains("Abyssinia", ignoreCase = true)       -> "BOA"
        name.contains("Awash", ignoreCase = true)           -> "Awash"
        name.contains("Dashen", ignoreCase = true)          -> "Dashen"
        else -> name
    }

    /**
     * Last 4 digits of a masked account number. Banks vary the mask length
     * between SMS formats ("1*****8166" vs "1********8166"), so the suffix is
     * the only part that identifies the same account across formats.
     */
    fun accountSuffix(accountNumber: String?): String? {
        val digits = accountNumber?.filter { it.isDigit() } ?: return null
        return if (digits.length >= 4) digits.takeLast(4) else null
    }

    /**
     * Groups transactions into accounts. A bank with one known account (or
     * none, like Telebirr) stays a single group holding all its rows. A bank
     * with two or more account suffixes is split, numbered by first-seen
     * date so "CBE 1" stays "CBE 1" as new SMS arrive. In that case rows
     * without an account number can't be attributed and are left out.
     */
    fun group(all: List<TransactionEntity>): List<AccountGroup> =
        all.groupBy { it.bankName.ifBlank { it.sender } }.flatMap { (bankName, txns) ->
            val bySuffix = txns
                .filter { accountSuffix(it.accountNumber) != null }
                .groupBy { accountSuffix(it.accountNumber)!! }
            if (bySuffix.size <= 1) {
                listOf(
                    AccountGroup(
                        key = bankName,
                        bankName = bankName,
                        label = bankName,
                        accountNumber = latestAccountNumber(txns),
                        accountSuffix = null,
                        transactions = txns
                    )
                )
            } else {
                val short = shortBankLabel(bankName)
                bySuffix.entries
                    .sortedBy { (_, rows) -> rows.minOf { it.dateTime } }
                    .mapIndexed { i, (suffix, rows) ->
                        AccountGroup(
                            key = "$bankName#$suffix",
                            bankName = bankName,
                            label = "$short ${i + 1}",
                            accountNumber = latestAccountNumber(rows),
                            accountSuffix = suffix,
                            transactions = rows
                        )
                    }
            }
        }

    private fun latestAccountNumber(txns: List<TransactionEntity>): String =
        txns.filter { !it.accountNumber.isNullOrBlank() }
            .maxByOrNull { it.dateTime }
            ?.accountNumber ?: ""
}
