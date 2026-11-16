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
