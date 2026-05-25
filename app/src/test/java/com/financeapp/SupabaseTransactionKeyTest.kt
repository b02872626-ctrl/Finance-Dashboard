package com.financeapp

import com.financeapp.data.model.TransactionEntity
import com.financeapp.remote.SupabaseTransactionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseTransactionKeyTest {

    @Test
    fun `stable key ignores local room id when ref is present`() {
        val first = sampleTransaction(id = 1, refNumber = "FT26094DXLMX82338166")
        val second = sampleTransaction(id = 99, refNumber = "FT26094DXLMX82338166")

        assertEquals(SupabaseTransactionKey.from(first), SupabaseTransactionKey.from(second))
    }

    @Test
    fun `stable key falls back to body fingerprint when ref is missing`() {
        val first = sampleTransaction(id = 1, refNumber = null, rawBody = "Hello   world")
        val second = sampleTransaction(id = 77, refNumber = null, rawBody = "  hello world  ")

        assertEquals(SupabaseTransactionKey.from(first), SupabaseTransactionKey.from(second))
    }

    @Test
    fun `stable key changes for different transactions`() {
        val first = sampleTransaction(id = 1, refNumber = "REF-1")
        val second = sampleTransaction(id = 2, refNumber = "REF-2")

        val firstKey = SupabaseTransactionKey.from(first)
        val secondKey = SupabaseTransactionKey.from(second)

        assertTrue(firstKey > 0)
        assertTrue(secondKey > 0)
        assertNotEquals(firstKey, secondKey)
    }

    private fun sampleTransaction(
        id: Long,
        refNumber: String?,
        rawBody: String = "Dear user your account was credited"
    ) = TransactionEntity(
        id = id,
        sender = "CBE",
        bankName = "Commercial Bank of Ethiopia",
        type = "CREDIT",
        amount = 600.0,
        balance = 1000.0,
        counterparty = "Alice",
        accountNumber = "1*****8166",
        refNumber = refNumber,
        dateTime = 1_775_000_000_000L,
        serviceCharge = null,
        vat = null,
        disasterFund = null,
        totalCharged = null,
        rawBody = rawBody,
        createdAt = 1_775_000_100_000L
    )
}
