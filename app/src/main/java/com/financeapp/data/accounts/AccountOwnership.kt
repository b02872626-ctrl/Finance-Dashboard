package com.financeapp.data.accounts

import com.financeapp.data.model.TransactionEntity

/**
 * Whose account an account is, and whether it's gone quiet (owner, Nov 2026).
 *
 * A phone can get bank SMS for other people's accounts ("Dear Hana, your
 * Account 1****2222 …" on someone else's phone, when the number was given to
 * the bank). Every bank SMS greets the account holder, so the greeting tells
 * us whose account it is. Accounts with no SMS for [STALE_MS] keep an old
 * balance that shouldn't count in today's total.
 */
object AccountOwnership {
    const val STALE_MS = 90L * 24 * 60 * 60 * 1000

    private val GREETING = Regex(
        """^\s*Dear\s+([A-Za-z][A-Za-z'.]*)""",
        RegexOption.IGNORE_CASE,
    )
    private val GENERIC = setOf(
        "customer", "customers", "valued", "esteemed", "sir", "madam", "client", "member", "user",
        "our", "all", "beneficiary", "account", "subscriber", "telebirr", "cbe",
    )

    /** "Dear Hana, your Account …" → "hana"; null for "Dear Customer" or no greeting. */
    fun greetingName(body: String?): String? {
        val name = GREETING.find(body ?: return null)?.groupValues?.get(1)?.trimEnd('.', '\'')?.lowercase() ?: return null
        return name.takeIf { it.length >= 2 && it !in GENERIC }
    }

    /** The name most of these SMS greet, if any. */
    fun holder(rows: List<TransactionEntity>): String? =
        rows.mapNotNull { greetingName(it.rawBody) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /**
     * Names that are the user: the first word of their name (Settings or the
     * sign-in account) and the name most of all their SMS greet. Both count,
     * because the Settings name is often a nickname or an English name the
     * banks never use; trusting it alone marked every one of the owner's own
     * accounts as someone else's (1.0.32).
     */
    fun ownerNames(all: List<TransactionEntity>, userName: String?): Set<String> {
        val fromSettings = userName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()?.takeIf { it.length >= 2 }
        return setOfNotNull(fromSettings, holder(all))
    }

    /** Same person: equal, or one is a short form of the other ("abe" / "abebech"). */
    fun samePerson(a: String, b: String): Boolean =
        a == b || (minOf(a.length, b.length) >= 3 && (a.startsWith(b) || b.startsWith(a)))

    /** The holder's name when this account's SMS greet someone other than the user; else null. */
    fun otherHolder(rows: List<TransactionEntity>, owners: Set<String>): String? {
        if (owners.isEmpty()) return null
        val h = holder(rows) ?: return null
        return h.takeIf { owners.none { o -> samePerson(o, h) } }
    }

    /** No SMS for [STALE_MS]. */
    fun isStale(rows: List<TransactionEntity>, nowMs: Long): Boolean {
        val last = rows.maxOfOrNull { it.dateTime } ?: return false
        return nowMs - last > STALE_MS
    }

    fun displayName(name: String): String = name.replaceFirstChar { it.uppercase() }
}
