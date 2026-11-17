package com.financeapp.ui.screens

import com.financeapp.data.model.Spending
import com.financeapp.data.model.TransactionCategoryCatalog

/**
 * The sort card (Review, a transaction's category, Add transaction): what it
 * offers and in what order. Pure; see SortCardTest.
 *
 * Only the user's own categories: every name they made ([yours]'s `custom`)
 * or put on a transaction (`usage`), most used first, then the most recently
 * picked, then A–Z. No built-in catalog (owner's call, 14 Nov 2026): a
 * built-in name shows only once a transaction of the user's carries it.
 * "Own accounts" is the one default: always last, on every transaction
 * (owner's call, 17 Nov 2026), so a move between the user's accounts can
 * always be kept out of spending.
 */
object SortCard {
    /** How many transactions carry [category]. */
    data class Use(val category: String, val count: Int)

    /** What the search box shows: the names that match, or the name to add when none matches exactly. */
    data class Search(val matches: List<String>, val addName: String?)

    const val MAX_NAME = 32

    fun yours(
        custom: List<String>,
        usage: List<Use>,
        recent: List<String> = emptyList(),
    ): List<String> {
        val counts = HashMap<String, Int>()
        val names = LinkedHashMap<String, String>() // lowercase → as written
        fun add(raw: String, count: Int) {
            val name = TransactionCategoryCatalog.normalize(raw)
            if (name.isBlank() || name.equals(Spending.OWN_ACCOUNTS, true)) return
            if (name.lowercase() in Spending.LEGACY_OWN_ACCOUNT_NAMES) return
            val key = name.lowercase()
            names.putIfAbsent(key, name)
            counts[key] = (counts[key] ?: 0) + count
        }
        usage.forEach { add(it.category, it.count) }
        // Built-in names the user never put on a transaction stay out
        // (older versions saved them to the list without being asked).
        custom.filterNot { c ->
            TransactionCategoryCatalog.defaultCategories.any { it.equals(TransactionCategoryCatalog.normalize(c), true) } &&
                usage.none { it.count > 0 && it.category.trim().equals(c.trim(), true) }
        }.forEach { add(it, 0) }
        val recentRank = recent.map { it.lowercase() }.withIndex().associate { (i, n) -> n to i }
        val sorted = names.keys
            .sortedWith(compareBy({ -(counts[it] ?: 0) }, { recentRank[it] ?: Int.MAX_VALUE }, { it }))
            .map { names.getValue(it) }
        return sorted + Spending.OWN_ACCOUNTS
    }

    fun search(query: String, yours: List<String>): Search {
        val typed = TransactionCategoryCatalog.normalize(query).take(MAX_NAME)
        if (typed.isBlank()) return Search(yours, null)
        val matches = yours.filter { it.contains(typed, ignoreCase = true) }
        val exact = yours.any { it.equals(typed, ignoreCase = true) }
        return Search(matches, if (exact) null else typed)
    }
}
