package com.financeapp.ui.screens

import com.financeapp.data.model.TransactionEntity

/** Which slice of the pending rows Review shows. */
enum class ReviewTab(val label: String) { TODAY("Today"), MONTH("This month"), EARLIER("Earlier") }

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
        tab == ReviewTab.MONTH -> count.toString() to " to sort this month"
        else -> count.toString() to " from before"
    }

    fun subtitle(tab: ReviewTab, count: Int): String = when {
        count > 0 && tab == ReviewTab.EARLIER -> "Older months, newest first. Sort what you remember."
        count > 0 -> "Newest first. Tap Sort and say what it was."
        tab == ReviewTab.TODAY -> "Today is done. Nice."
        tab == ReviewTab.MONTH -> "This month is done. Nice."
        else -> "Everything from before is sorted."
    }

    /** How many rows each tab holds; This month counts today's too. */
    fun count(tab: ReviewTab, split: Split): Int = when (tab) {
        ReviewTab.TODAY -> split.today.size
        ReviewTab.MONTH -> split.month.size
        ReviewTab.EARLIER -> split.older.size
    }

    /**
     * Where Review opens, and where it moves when the tab being looked at
     * runs out: the first of Today, This month, Earlier with something to
     * sort; Today when all three are done. [from] is never moved backwards.
     */
    fun landing(split: Split, from: ReviewTab = ReviewTab.TODAY): ReviewTab =
        ReviewTab.entries.filter { it.ordinal >= from.ordinal }.firstOrNull { count(it, split) > 0 }
            ?: if (from == ReviewTab.TODAY) ReviewTab.TODAY else from

    /** A tab count for the tab well: capped like the badge. */
    fun tabCount(n: Int): String = if (n > 99) "99+" else n.toString()

    /** [rows] (newest first) grouped by the month they fall in, newest month first. */
    fun byMonth(rows: List<TransactionEntity>, monthStartOf: (Long) -> Long): List<Pair<Long, List<TransactionEntity>>> =
        rows.sortedByDescending { it.dateTime }.groupBy { monthStartOf(it.dateTime) }.toList()

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
