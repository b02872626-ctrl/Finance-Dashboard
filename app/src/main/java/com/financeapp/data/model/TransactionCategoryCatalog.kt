package com.financeapp.data.model

import java.util.Locale

object TransactionCategoryCatalog {
    val defaultCategories = listOf(
        "Food",
        "Coffee and refreshments",
        "Bills",
        "Loan",
        "Drinks and fun",
        "Transport"
    )

    fun normalize(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")

    fun allCategories(customCategories: List<String>): List<String> =
        (defaultCategories + customCategories.map(::normalize))
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
}
