package com.financeapp.data.model

import java.util.Locale

object TransactionCategoryCatalog {
    /**
     * Default category list shown in the picker. Order matters — the most
     * common categories appear first. The Daily Review queue groups these
     * into Common / Lifestyle / Money in/out for the bottom sheet.
     *
     * NOTE: "Coffee and refreshments", "Loan", and "Drinks and fun" are
     * legacy entries that pre-date the Daily Review redesign. Kept so
     * older transactions don't lose their category.
     */
    val defaultCategories = listOf(
        // Common
        "Food",
        "Coffee and refreshments",
        "Transport",
        "Bills",
        "Shopping",
        "Health",
        // Lifestyle
        "Drinks and fun",
        "Entertainment",
        "Rent",
        "Fees",
        "Loan",
        // Money in/out
        "Transfer",
        "Income",
        "Other"
    )

    /** Sheet sections used by the Daily Review category picker. */
    val sections: List<Pair<String, List<String>>> = listOf(
        "Common"        to listOf("Food", "Transport", "Bills", "Shopping", "Health", "Coffee and refreshments"),
        "Lifestyle"     to listOf("Drinks and fun", "Entertainment", "Rent", "Fees", "Loan"),
        "Money in/out"  to listOf("Transfer", "Income", "Other"),
    )

    fun normalize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    fun allCategories(customCategories: List<String>): List<String> =
        (defaultCategories + customCategories.map(::normalize))
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
}
