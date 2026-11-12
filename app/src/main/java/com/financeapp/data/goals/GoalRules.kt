package com.financeapp.data.goals

import com.financeapp.data.accounts.AccountGrouping
import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalStatus

/**
 * One account feeds one goal. An account is taken while a goal that is
 * still being saved for (ACTIVE) is linked to it; a reached or archived
 * goal lets go of it.
 */
object GoalRules {

    /** The active goal already using [accountKey], other than [exceptGoalId]; null when it is free. */
    fun goalUsing(goals: List<GoalEntity>, accountKey: String, exceptGoalId: String? = null): GoalEntity? =
        goals.firstOrNull { g ->
            g.id != exceptGoalId && g.status == GoalStatus.ACTIVE &&
                g.linkedAccountKey?.let { AccountGrouping.sameAccountKey(it, accountKey) } == true
        }
}
