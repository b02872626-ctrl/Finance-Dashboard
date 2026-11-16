package com.financeapp.ui.screens

import com.financeapp.ui.kash.components.KashTip
import com.financeapp.ui.kash.components.KashTipSet

/**
 * The new-user tips, word for word from the approved flow ("Tip · …"
 * screens). Each tab shows its set the first time it opens with something
 * to point at. Search and filters on Home wait for the new Home list.
 */
object AppTips {
    val home = KashTipSet(
        "tips_home", "HOME",
        listOf(
            KashTip("balance", "Total balance", "All your accounts added up. Tap the eye to show or hide it."),
            KashTip("accounts", "Your accounts", "Each bank you use has its own card. Tap one to see just that account."),
        ),
    )

    /** Home, the visit after the Home tips: where sorting lives. */
    val reviewTab = KashTipSet(
        "tips_review_tab", "NEW",
        listOf(KashTip("reviewtab", "Sort your transactions here", "Say what your newest transactions were, today’s first. Once you sort someone, their new payments sort themselves.")),
    )

    val review = KashTipSet(
        "tips_review", "REVIEW",
        listOf(
            KashTip("sortbtn", "Sort the newest first", "Today’s transactions come first. Tap Sort and say what each one was."),
            KashTip("tabs", "Today, then this month", "When today’s are done, Review moves on to the rest of this month."),
        ),
    )

    /** The sort card, first time: drawn inside the sheet (it is its own window). */
    const val SORTING_KEY = "tips_sorting"

    val allTransactions = KashTipSet(
        "tips_all_transactions", "ALL TRANSACTIONS",
        listOf(
            KashTip("search", "Find anything", "Every transaction lives here now. Search by name, amount or reference."),
            KashTip("filters", "Narrow it down", "Money in or out, a month, an account, a category. The sliders button has every filter."),
        ),
    )

    val analytics = KashTipSet(
        "tips_analytics", "ANALYTICS",
        listOf(
            KashTip("daily", "Your daily spend", "What you’ve spent today, updated with every SMS. This week and this month sit below to compare."),
            KashTip("chart", "Spending over time", "Switch between a week, a month or 30 days. The dashed line is your average."),
        ),
    )

    /** Goals with no goal yet: point at the kinds to start with. */
    val goalsEmpty = KashTipSet(
        "tips_goals_empty", "GOALS",
        listOf(
            KashTip("kinds", "Start with one", "Pick what you’re saving for. Kash then fills it from the account you choose, straight from the bank SMS."),
        ),
    )

    /** A goal's page, first time: where its money comes from (approved FD_GoalTip). */
    fun goalSource(accountLabel: String?) = KashTipSet(
        "tips_goal_source", "FIRST TIME ON A GOAL",
        listOf(
            KashTip(
                "source", "Where the money comes from",
                if (accountLabel != null) "This goal counts what lands in $accountLabel. Tap here to see how it’s counted, or switch to another account."
                else "This goal counts the saves you add. Tap here to see how it’s counted, or link an account.",
            ),
        ),
    )

    /** Goals with a goal: how it fills, and where to start another. */
    fun goals(goalName: String, accountLabel: String?) = KashTipSet(
        "tips_goals", "GOALS",
        listOf(
            KashTip(
                "goal", "It fills by itself",
                if (accountLabel != null) "Money that lands in $accountLabel counts toward $goalName, straight from the bank SMS. Tap it to see what came in."
                else "Money that lands in its account counts toward $goalName, straight from the bank SMS. Tap it to see what came in.",
            ),
            KashTip("newgoal", "Save for more", "Start another goal: a set amount by a date, the same amount every month, or a safety net."),
        ),
    )
}
