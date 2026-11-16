package com.financeapp

import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.Spending
import com.financeapp.data.model.TransactionEntity
import com.financeapp.ui.screens.amountLabel
import com.financeapp.ui.screens.pickLabel
import com.financeapp.viewmodel.ShowOnly
import com.financeapp.viewmodel.TransactionsFilter
import com.financeapp.viewmodel.TxDirection
import com.financeapp.viewmodel.TxFilterRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** All transactions: what the search box, the chips and the Filters sheet keep. */
class TxFilterRulesTest {
    private fun tx(
        bank: String = "Commercial Bank of Ethiopia", type: String = "DEBIT", amount: Double = 560.0,
        who: String = "Shoa Supermarket", category: String? = "Food", status: String = ReviewStatus.CONFIRMED, sender: String = "CBE",
    ) = TransactionEntity(
        sender = sender, bankName = bank, type = type, amount = amount, balance = null,
        counterparty = who, accountNumber = null, refNumber = "FT1", dateTime = 0L,
        serviceCharge = null, vat = null, disasterFund = null, totalCharged = null, rawBody = "",
        category = category, reviewStatus = status,
    )

    @Test
    fun `search finds a name, a category or the amount as typed`() {
        assertTrue(TxFilterRules.matches(tx(), TransactionsFilter(searchQuery = "shoa")))
        assertTrue(TxFilterRules.matches(tx(), TransactionsFilter(searchQuery = "food")))
        assertTrue(TxFilterRules.matches(tx(amount = 1250.0), TransactionsFilter(searchQuery = "1,250")))
        assertFalse(TxFilterRules.matches(tx(), TransactionsFilter(searchQuery = "rent")))
    }

    @Test
    fun `amount search reads decimals as typed and ignores a lone point`() {
        assertTrue(TxFilterRules.matchesSearch(tx(amount = 1250.1, who = "x"), "1250.1"))
        assertTrue(TxFilterRules.matchesSearch(tx(amount = 1250.5, who = "x"), "1,250.50"))
        assertFalse(TxFilterRules.matchesSearch(tx(amount = 560.0, who = "x"), "."))
        assertFalse(TxFilterRules.matchesSearch(tx(amount = Double.NaN, who = "x"), "5"))
    }

    @Test
    fun `accounts, categories and amount narrow it down`() {
        val f = TransactionsFilter(accounts = setOf("Telebirr"))
        assertFalse(TxFilterRules.matches(tx(), f))
        assertTrue(TxFilterRules.matches(tx(bank = "Telebirr"), f))
        assertTrue(TxFilterRules.matches(tx(), TransactionsFilter(categories = setOf("food"))))
        assertFalse(TxFilterRules.matches(tx(), TransactionsFilter(minAmount = 600.0)))
        assertTrue(TxFilterRules.matches(tx(), TransactionsFilter(minAmount = 100.0, maxAmount = 560.0)))
    }

    @Test
    fun `show only picks widen each other`() {
        val f = TransactionsFilter(showOnly = setOf(ShowOnly.NOT_SORTED, ShowOnly.BY_HAND))
        assertTrue(TxFilterRules.matches(tx(status = ReviewStatus.PENDING), f))
        assertTrue(TxFilterRules.matches(tx(sender = "manual"), f))
        assertFalse(TxFilterRules.matches(tx(), f))
        assertTrue(TxFilterRules.matches(tx(category = Spending.OWN_ACCOUNTS), TransactionsFilter(showOnly = setOf(ShowOnly.BETWEEN_MINE))))
    }

    @Test
    fun `in and out keep own-account moves out`() {
        assertFalse(TxFilterRules.matches(tx(category = Spending.OWN_ACCOUNTS), TransactionsFilter(direction = TxDirection.OUT)))
        assertTrue(TxFilterRules.matches(tx(type = "CREDIT"), TransactionsFilter(direction = TxDirection.IN)))
    }

    @Test
    fun `chip labels`() {
        assertEquals("Account", pickLabel("Account", emptyList()))
        assertEquals("CBE +1", pickLabel("Account", listOf("CBE", "BoA")))
        assertEquals("Amount", amountLabel(TransactionsFilter()))
        assertEquals("Up to 500", amountLabel(TransactionsFilter(maxAmount = 500.0)))
    }
}
