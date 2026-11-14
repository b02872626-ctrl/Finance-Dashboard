package com.financeapp.ui.screens

import com.financeapp.data.model.TransactionEntity

/** Which slice of the pending rows Review shows. */
enum class ReviewTab(val label: String) { TODAY("Today"), MONTH("This month") }

/**
 * The Review list's pure rules: how pending rows split into today, this
 * month and older, what the top of the screen says, and the tab badge.
 * See ReviewListTest.
 */
object ReviewList {
    data class Split(
        val today: List<TransactionEntity>,
        /** This month, today included. */
        val month: List<TransactionEntity>,
        val older: List<TransactionEntity>,
    )

    /** [pending] newest first; [todayStart] and [monthStart] are inclusive bounds. */
    fun split(pending: List<TransactionEntity>, todayStart: Long, monthStart: Long): Split {
        val sorted = pending.sortedByDescending { it.dateTime }
        return Split(
            today = sorted.filter { it.dateTime >= todayStart },
            month = sorted.filter { it.dateTime >= monthStart },
            older = sorted.filter { it.dateTime < monthStart },
        )
    }

    /** The big figure and its unit: "5" + " to sort today", or "All" + " sorted". */
    fun headline(tab: ReviewTab, count: Int): Pair<String, String> = when {
        count == 0 -> "All" to " sorted"
        tab == ReviewTab.TODAY -> count.toString() to " to sort today"
        else -> count.toString() to " to sort this month"
    }

    fun subtitle(tab: ReviewTab, count: Int): String = when {
        count > 0 -> "Newest first. Tap Sort and say what it was."
        tab == ReviewTab.TODAY -> "Today is done. Nice."
        else -> "This month is done. Nice."
    }

    /** The Review tab's badge: this month's pending count, none at zero, capped at 99+. */
    fun badge(monthPending: Int): String? = when {
        monthPending <= 0 -> null
        monthPending > 99 -> "99+"
        else -> monthPending.toString()
    }

    /** The snackbar after a sort: "Yonas Girma is Rent. New ones will be too." */
    fun sortedText(name: String, category: String, rule: Boolean): String =
        "$name is $category." + if (rule) " New ones will be too." else ""
}
