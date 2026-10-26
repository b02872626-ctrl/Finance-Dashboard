package com.financeapp

import com.financeapp.data.model.Spending
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.isIncome
import com.financeapp.data.model.isOwnAccountTransfer
import com.financeapp.data.model.isSpending
import com.financeapp.data.model.netAmount
import com.financeapp.parsing.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingTest {

    private fun tx(type: TransactionType, amount: Double = 100.0, category: String? = null) =
        TransactionEntity(
            sender = "CBE",
            bankName = "Commercial Bank of Ethiopia",
            type = type.name,
            amount = amount,
            balance = null,
            counterparty = "Someone",
            accountNumber = null,
            refNumber = null,
            dateTime = 0L,
            serviceCharge = null,
            vat = null,
            disasterFund = null,
            totalCharged = null,
            category = category,
            rawBody = ""
        )

    @Test
    fun `debit, transfer out and payment are spending`() {
        listOf(TransactionType.DEBIT, TransactionType.TRANSFER_OUT, TransactionType.PAYMENT).forEach {
            assertTrue(it.name, tx(it).isSpending())
            assertFalse(it.name, tx(it).isIncome())
        }
    }

    @Test
    fun `credit is income`() {
        assertTrue(tx(TransactionType.CREDIT).isIncome())
        assertFalse(tx(TransactionType.CREDIT).isSpending())
    }

    @Test
    fun `own accounts category is neither spending nor income`() {
        val out = tx(TransactionType.TRANSFER_OUT, category = Spending.OWN_ACCOUNTS)
        val inn = tx(TransactionType.CREDIT, category = Spending.OWN_ACCOUNTS)
        assertTrue(out.isOwnAccountTransfer())
        assertFalse(out.isSpending())
        assertFalse(inn.isIncome())
        assertEquals(0.0, out.netAmount(), 0.0)
        assertEquals(0.0, inn.netAmount(), 0.0)
    }

    @Test
    fun `parser-typed internal transfer is excluded`() {
        val t = tx(TransactionType.INTERNAL_TRANSFER)
        assertTrue(t.isOwnAccountTransfer())
        assertFalse(t.isSpending())
        assertFalse(t.isIncome())
        assertEquals(0.0, t.netAmount(), 0.0)
    }

    @Test
    fun `net keeps sign for normal rows`() {
        assertEquals(100.0, tx(TransactionType.CREDIT).netAmount(), 0.0)
        assertEquals(-100.0, tx(TransactionType.DEBIT).netAmount(), 0.0)
        // Unknown rows keep the old behaviour (counted as money out in nets).
        assertEquals(-100.0, tx(TransactionType.UNKNOWN).netAmount(), 0.0)
    }

    @Test
    fun `other categories still count`() {
        assertTrue(tx(TransactionType.DEBIT, category = "Transfer").isSpending())
        assertTrue(tx(TransactionType.DEBIT, category = "Food").isSpending())
    }
}
