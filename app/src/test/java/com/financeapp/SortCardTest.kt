package com.financeapp

import com.financeapp.data.model.Spending
import com.financeapp.ui.screens.SortCard
import com.financeapp.ui.screens.SortCard.Use
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The sort card offers the user's own categories, most used first, and "Add" for a new name. */
class SortCardTest {

    @Test
    fun `most used first, then the latest pick, then A to Z`() {
        val yours = SortCard.yours(
            custom = listOf("Gym", "Rent", "Coffee"),
            usage = listOf(Use("Rent", 3), Use("Lunch", 9), Use("Taxi", 3)),
            recent = listOf("Taxi"),
        )
        assertEquals(listOf("Lunch", "Taxi", "Rent", "Coffee", "Gym", Spending.OWN_ACCOUNTS), yours)
    }

    @Test
    fun `a name counts once however it is spelled`() {
        val yours = SortCard.yours(custom = listOf("rent"), usage = listOf(Use("Rent", 2), Use("RENT", 1), Use("Food", 2)))
        assertEquals(listOf("Rent", "Food", Spending.OWN_ACCOUNTS), yours)
    }

    @Test
    fun `no built-in catalog, a new user sees only Own accounts`() {
        assertEquals(listOf(Spending.OWN_ACCOUNTS), SortCard.yours(custom = emptyList(), usage = emptyList()))
    }

    @Test
    fun `own accounts is always there, last, however much it is used`() {
        val usage = listOf(Use("Food", 5), Use(Spending.OWN_ACCOUNTS, 20))
        assertEquals(listOf("Food", Spending.OWN_ACCOUNTS), SortCard.yours(emptyList(), usage))
    }

    @Test
    fun `search narrows the list and offers to add a name nobody has`() {
        val yours = listOf("Rent", "Groceries", "Gym")
        assertEquals(SortCard.Search(yours, null), SortCard.search("", yours))
        assertEquals(SortCard.Search(listOf("Groceries", "Gym"), "g"), SortCard.search("g", yours))
        assertEquals(SortCard.Search(listOf("Rent"), null), SortCard.search("rent", yours))
        val fresh = SortCard.search("School fees", yours)
        assertEquals(emptyList<String>(), fresh.matches)
        assertEquals("School fees", fresh.addName)
    }

    @Test
    fun `a new name is cut to the limit`() {
        val long = "A".repeat(50)
        assertEquals(SortCard.MAX_NAME, SortCard.search(long, emptyList()).addName!!.length)
        assertNull(SortCard.search("   ", emptyList()).addName)
    }

    @Test
    fun `built-in names saved without being used stay out`() {
        val yours = SortCard.yours(
            custom = listOf("Edom", "Bills", "Health", "Food", "Transport"),
            usage = listOf(Use("Edom", 4), Use("Food", 2)),
        )
        assertEquals(listOf("Edom", "Food", Spending.OWN_ACCOUNTS), yours)
    }
}
