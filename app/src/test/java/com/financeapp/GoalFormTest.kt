package com.financeapp

import com.financeapp.data.model.GoalEntity
import com.financeapp.data.model.GoalType
import com.financeapp.ui.screens.GoalForm
import com.financeapp.util.KashDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The New goal / Edit goal form rules. Made-up goals. */
class GoalFormTest {

    private val day = 86_400_000L
    private val today = KashDates.startOfDay(1_800_000_000_000L)

    @Test fun anAmountByADateNeedsNameAmountAndAFutureDate() {
        val empty = GoalForm.check(GoalForm.Fields(type = GoalType.DATE_BOUND), today)
        assertNotNull(empty.name); assertNotNull(empty.amount); assertNotNull(empty.date)

        val past = GoalForm.Fields(GoalType.DATE_BOUND, name = "Laptop", amount = "60,000", deadlineDay = today - day)
        assertEquals("Pick a day from today on", GoalForm.check(past, today).date)

        val ok = past.copy(deadlineDay = today + 30 * day)
        assertTrue(GoalForm.check(ok, today).none)
    }

    @Test fun aSafetyNetNeedsMonthsNotAnAmount() {
        val f = GoalForm.Fields(GoalType.OPEN_ENDED, name = "Safety net", amount = "", months = "3")
        assertTrue(GoalForm.check(f, today).none)
        assertNotNull(GoalForm.check(f.copy(months = "0"), today).months)
        assertNotNull(GoalForm.check(f.copy(months = "99"), today).months)
    }

    @Test fun aMonthlyGoalNeedsAnAmountAndNoDate() {
        val f = GoalForm.Fields(GoalType.RECURRING, name = "Monthly", amount = "")
        assertEquals("Enter how much a month", GoalForm.check(f, today).amount)
        assertTrue(GoalForm.check(f.copy(amount = "5000"), today).none)
    }

    @Test fun draftKeepsOnlyWhatTheKindUses() {
        val f = GoalForm.Fields(
            GoalType.DATE_BOUND, name = "  Laptop ", emoji = "💻", amount = "60,000.50",
            months = "3", deadlineDay = today + 10 * day, accountKey = "CBE#1234",
        )
        val d = GoalForm.draft(f)
        assertEquals("Laptop", d.name)
        assertEquals("💻", d.emoji)
        assertEquals(60_000.5, d.amount!!, 0.001)
        assertNull(d.months)
        // The deadline is the end of the chosen day.
        assertEquals(today + 11 * day - 1, d.deadline)
        assertEquals("CBE#1234", d.accountKey)

        val net = GoalForm.draft(f.copy(type = GoalType.OPEN_ENDED))
        assertNull(net.amount); assertNull(net.deadline); assertEquals(3, net.months)
    }

    @Test fun editStartsFromTheGoal() {
        val g = GoalEntity(
            name = "Laptop", emoji = "💻", type = GoalType.DATE_BOUND, targetAmount = 60_000.0,
            deadline = today + 10 * day + 5_000, linkedAccountKey = "CBE#1234",
        )
        val f = GoalForm.fieldsOf(g)
        assertEquals("60000", f.amount)
        assertEquals(today + 10 * day, f.deadlineDay)
        assertEquals("CBE#1234", f.accountKey)
        assertTrue(GoalForm.check(f, today).none)
    }

    @Test fun safetyNetPreview() {
        assertEquals("That's about 38,900 ETB at your spending", GoalForm.safetyNetPreview("3", 12_950.0))
        assertEquals("Kash works out the amount once it has a month of your spending", GoalForm.safetyNetPreview("3", 0.0))
        assertNull(GoalForm.safetyNetPreview("", 12_950.0))
    }

    @Test fun dateLabels() {
        val ms = java.util.Calendar.getInstance().apply { clear(); set(2027, 5, 15, 12, 0) }.timeInMillis
        assertEquals("15 June 2027", GoalForm.dateLabel(ms, "GREGORIAN"))
        assertEquals("Sene 8, 2019", GoalForm.dateLabel(ms, "ETHIOPIAN"))
    }
}
