package com.financeapp.ui.goals

import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalType
import com.financeapp.ui.kash.KashIcon

/**
 * The 12 goal icons that replace emoji (owner's call, Goals design). The
 * key is stored in [GoalEntity.emoji] so no database change is needed; a
 * goal without a known key (none, or an old emoji) shows its kind's icon.
 */
object GoalIcons {
    data class Option(val key: String, val icon: KashIcon, val name: String)

    val ALL = listOf(
        Option("laptop", KashIcon.Laptop, "Laptop"),
        Option("phone", KashIcon.Phone, "Phone"),
        Option("school", KashIcon.School, "School"),
        Option("rings", KashIcon.Rings, "Wedding"),
        Option("house", KashIcon.Home, "Home"),
        Option("car", KashIcon.Car, "Car"),
        Option("plane", KashIcon.Plane, "Travel"),
        Option("people", KashIcon.People, "Ekub"),
        Option("gift", KashIcon.Gift, "Gift"),
        Option("shield", KashIcon.Shield, "Safety net"),
        Option("repeat", KashIcon.Repeat, "Every month"),
        Option("target", KashIcon.Target, "Goal"),
    )

    fun defaultKey(type: String): String = when (type) {
        GoalType.OPEN_ENDED -> "shield"
        GoalType.RECURRING -> "repeat"
        else -> "target"
    }

    /** The goal's icon key: its own when it's one of [ALL], else its kind's. */
    fun keyOf(goal: GoalEntity): String =
        goal.emoji?.takeIf { k -> ALL.any { it.key == k } } ?: defaultKey(goal.type)

    fun iconOf(goal: GoalEntity): KashIcon = ALL.first { it.key == keyOf(goal) }.icon

    fun icon(key: String): KashIcon = ALL.firstOrNull { it.key == key }?.icon ?: KashIcon.Target
}
