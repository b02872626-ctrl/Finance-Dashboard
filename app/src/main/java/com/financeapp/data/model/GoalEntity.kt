package com.financeapp.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * The three kinds of savings goal. Same names as the `type` check in
 * supabase/add_goals.sql, so a goal can sync to i_goals later unchanged.
 */
object GoalType {
    /** A fixed amount by a date ("Laptop, 60,000 ETB by June"). */
    const val DATE_BOUND = "DATE_BOUND"
    /** A safety net sized from spending ("3 months of spending"). No date. */
    const val OPEN_ENDED = "OPEN_ENDED"
    /** The same amount every month, for good ("5,000 ETB a month"). */
    const val RECURRING = "RECURRING"

    val ALL = listOf(DATE_BOUND, OPEN_ENDED, RECURRING)
}

object GoalStatus {
    const val ACTIVE = "ACTIVE"
    const val ACHIEVED = "ACHIEVED"
    const val ARCHIVED = "ARCHIVED"
}

/**
 * One savings goal (Goals section). Mirrors public.i_goals ([linkedAccountKey]
 * is `linked_account_key`, supabase/add_goal_linked_account.sql); synced by
 * remote/GoalCloudSync.kt.
 *
 * Money reaches a goal two ways (see GoalProgress):
 *  - automatically: money that lands in [linkedAccountKey] (the user's
 *    savings account, found from SMS) after [createdAt];
 *  - by hand: a [GoalContributionEntity] the user logs.
 */
@Entity(tableName = "goals", indices = [Index(value = ["status", "priority"])])
data class GoalEntity(
    /** UUID, so the row keeps the same id when it syncs to i_goals. */
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String? = null,
    /** See [GoalType]. */
    val type: String,
    /**
     * DATE_BOUND: the total to reach. RECURRING: the amount per month.
     * OPEN_ENDED: null (the target is [targetMonths] × average monthly spending).
     */
    val targetAmount: Double? = null,
    /** OPEN_ENDED only: how many months of spending to keep aside. */
    val targetMonths: Int? = null,
    /** DATE_BOUND only: epoch ms of the deadline (end of that day). */
    val deadline: Long? = null,
    /** Lower comes first when money from one account is shared between goals. */
    val priority: Int = 100,
    val note: String? = null,
    /** See [GoalStatus]. */
    val status: String = GoalStatus.ACTIVE,
    /**
     * Account whose incoming money counts toward this goal, as an
     * AccountGrouping key ("Commercial Bank of Ethiopia#1234", or the bank
     * name when the user has one account there). Null: saves are logged by hand.
     */
    val linkedAccountKey: String? = null,
    /** Money in the linked account counts from this moment on. */
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val achievedAt: Long? = null,
    val isSynced: Boolean = false,
)

/** A save the user logged by hand. Mirrors public.i_goal_contributions (amount > 0). */
@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["goalId", "contributedAt"])],
)
data class GoalContributionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val goalId: String,
    val amount: Double,
    val contributedAt: Long = System.currentTimeMillis(),
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
)

/**
 * A goal or save deleted on the phone whose cloud row is still to be
 * deleted (offline first: it goes on the next sync pass). The id is the
 * goal's or the contribution's; both are UUIDs, so they never clash.
 */
@Entity(tableName = "goal_deletions")
data class GoalDeletionEntity(
    @PrimaryKey val id: String,
    /** [KIND_GOAL] or [KIND_CONTRIBUTION]. */
    val kind: String,
    val deletedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val KIND_GOAL = "GOAL"
        const val KIND_CONTRIBUTION = "CONTRIBUTION"
    }
}
