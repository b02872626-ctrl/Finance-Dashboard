package com.financeapp

import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.TransactionEntity
import com.financeapp.remote.CloudCategoryRestore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug that bit once: after a reinstall every SMS was read again as an
 * untouched row, uploaded with no category, and wiped the categories the
 * user had saved in the cloud.
 */
class CloudCategoryRestoreTest {

    private val day = 24L * 3600 * 1000
    private val now = 1_800_000_000_000L

    private fun tx(
        category: String? = null,
        status: String = ReviewStatus.PENDING,
        dateTime: Long = now,
    ) = TransactionEntity(
        sender = "CBE", bankName = "CBE", type = "DEBIT", amount = 250.0, balance = null,
        counterparty = "Abebe Kebede", accountNumber = null, refNumber = "FT00000000TEST",
        dateTime = dateTime, serviceCharge = null, vat = null, disasterFund = null,
        totalCharged = null, category = category, reviewStatus = status, rawBody = "test",
    )

    @Test
    fun `a freshly read row never sends its review state, so the cloud category stays`() {
        assertFalse(CloudCategoryRestore.sendsReviewState(tx()))
    }

    @Test
    fun `rows the user decided on still upload their category and status`() {
        assertTrue(CloudCategoryRestore.sendsReviewState(tx("Food", ReviewStatus.CONFIRMED)))
        assertTrue(CloudCategoryRestore.sendsReviewState(tx("Rent", ReviewStatus.CHANGED)))
        assertTrue(CloudCategoryRestore.sendsReviewState(tx(status = ReviewStatus.SKIPPED)))
        assertTrue(CloudCategoryRestore.sendsReviewState(tx(status = ReviewStatus.NOT_A_TXN)))
        // Undo after confirming keeps the category on the row: still sent.
        assertTrue(CloudCategoryRestore.sendsReviewState(tx("Food", ReviewStatus.PENDING)))
    }

    @Test
    fun `normal sync pulls the last 90 days`() {
        assertEquals(now - 90 * day, CloudCategoryRestore.pullSinceMs(now, emptyList()))
        assertEquals(now - 90 * day, CloudCategoryRestore.pullSinceMs(now, listOf(tx(dateTime = now - day))))
    }

    @Test
    fun `after a reinstall the pull reaches the oldest re-read row`() {
        val history = listOf(tx(dateTime = now - 2 * day), tx(dateTime = now - 400 * day), tx(dateTime = now - 30 * day))
        assertEquals(now - 400 * day, CloudCategoryRestore.pullSinceMs(now, history))
    }

    @Test
    fun `own categories come back, built-in and known ones are not added twice`() {
        val restored = CloudCategoryRestore.customCategoriesToRestore(
            listOf("Food", "Gym", "gym", null, " ", "Church  gifts", "Internal", "Savings"),
            existingCustom = listOf("Savings"),
        )
        assertEquals(listOf("Gym", "Church gifts"), restored)
    }
}
