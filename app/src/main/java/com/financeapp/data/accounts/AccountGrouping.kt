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
     *
     * Only the digits after the last mask character count: Berhan writes the
     * same account as "*********9067" (debits) and "103002XXXXX67" (credits),
     * and joining the digits on both sides of the X's gave "0267", a second,
     * made-up account. Such a short tail ("67") is matched to the full
     * suffix by [sameSuffix].
     */
    fun accountSuffix(accountNumber: String?): String? {
        val raw = accountNumber?.trim()?.trimEnd('.') ?: return null
        val tail = raw.takeLastWhile { it.isDigit() }
        val masked = raw.any { it == '*' || it == 'X' || it == 'x' }
        return when {
            tail.length >= 4 -> tail.takeLast(4)
            masked && tail.length >= 2 -> tail
            masked -> null
            else -> raw.filter { it.isDigit() }.takeIf { it.length >= 4 }?.takeLast(4)
        }
    }

    /** Same account: equal suffixes, or a short masked tail ("67") ending the full one ("9067"). */
    fun sameSuffix(a: String, b: String): Boolean =
        a == b || (a.length < 4 && b.endsWith(a)) || (b.length < 4 && a.endsWith(b))

    /** [accountKey]s naming the same account, short masked tails included. */
    fun sameAccountKey(a: String, b: String): Boolean {
        val (bankA, sufA) = parseAccountKey(a) ?: return a == b
        val (bankB, sufB) = parseAccountKey(b) ?: return false
        return bankA == bankB && sameSuffix(sufA, sufB)
    }

    /**
     * Identity of one account for hiding: "bank#1234". Same shape as
     * [AccountGroup.key] for a bank split into several accounts.
     */
    fun accountKey(bankName: String, suffix: String): String = "$bankName#$suffix"

    /** [accountKey] of the account a row belongs to; null when the SMS named no account. */
    fun accountKeyOf(bankName: String, sender: String, accountNumber: String?): String? =
        accountSuffix(accountNumber)?.let { accountKey(bankName.ifBlank { sender }, it) }

    /** "bank#1234" back to (bank, "1234"); null for anything else. */
    fun parseAccountKey(key: String): Pair<String, String>? {
        val i = key.lastIndexOf('#')
        if (i <= 0 || i == key.lastIndex) return null
        return key.substring(0, i) to key.substring(i + 1)
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
            val bySuffix = mergeShortTails(
                txns.filter { accountSuffix(it.accountNumber) != null }
                    .groupBy { accountSuffix(it.accountNumber)!! }
            )
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

    /** Folds a short tail ("67") into the one full suffix that ends with it ("9067"). */
    private fun mergeShortTails(bySuffix: Map<String, List<TransactionEntity>>): Map<String, List<TransactionEntity>> {
        val full = bySuffix.keys.filter { it.length >= 4 }
        val out = bySuffix.filterKeys { it.length >= 4 }.mapValues { it.value.toMutableList() }.toMutableMap()
        bySuffix.filterKeys { it.length < 4 }.forEach { (tail, rows) ->
            val match = full.filter { it.endsWith(tail) }
            if (match.size == 1) out.getValue(match[0]).addAll(rows)
            else out[tail] = rows.toMutableList()
        }
        return out
    }

    private fun latestAccountNumber(txns: List<TransactionEntity>): String =
        txns.filter { !it.accountNumber.isNullOrBlank() }
            .maxByOrNull { it.dateTime }
            ?.accountNumber ?: ""
}
