package com.financeapp

import com.financeapp.data.model.TransactionEntity
import com.financeapp.remote.SupabaseTransactionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SupabaseTransactionKeyTest {

    @Test
    fun `same SMS gives the same id whatever the parser made of it`() {
        // A newer parser reads the same SMS differently: other type, ref,
        // counterparty. It must still update the same cloud row.
        val old = sampleTransaction(type = "UNKNOWN", refNumber = null, counterparty = null)
        val new = sampleTransaction(type = "CREDIT", refNumber = "FT26094DXLMX82338166", counterparty = "Alice")
        assertEquals(SupabaseTransactionKey.from(old), SupabaseTransactionKey.from(new))
    }

    @Test
    fun `local room id does not matter`() {
        assertEquals(
            SupabaseTransactionKey.from(sampleTransaction(id = 1)),
            SupabaseTransactionKey.from(sampleTransaction(id = 99)),
        )
    }

    @Test
    fun `different SMS text gives different ids`() {
        assertNotEquals(
            SupabaseTransactionKey.from(sampleTransaction(rawBody = "credited with ETB 600.00")),
            SupabaseTransactionKey.from(sampleTransaction(rawBody = "credited with ETB 601.00")),
        )
    }

    @Test
    fun `manual entries with the same text stay separate`() {
        val a = sampleTransaction(sender = "manual", rawBody = "[MANUAL ENTRY] DEBIT • ETB 50.0 • Shop", refNumber = "manual-a")
        val b = a.copy(refNumber = "manual-b")
        assertNotEquals(SupabaseTransactionKey.from(a), SupabaseTransactionKey.from(b))
    }

    /**
     * Reference values computed independently (Python hashlib, the same
     * formula as public.kash_txn_key in supabase/dedupe_transactions_by_sms.sql).
     * If this fails, the app and the database disagree on ids.
     */
    @Test
    fun `matches the database formula`() {
        assertEquals(8067348164951004845L, SupabaseTransactionKey.of("CBE", "Dear user your account was credited", null))
        assertEquals(
            3497805380770644474L,
            SupabaseTransactionKey.of(
                "127",
                "Dear Yeabsira You have repaid 0.34 ETB, for your Siinqee_30_Days contract. Your current unpaid credit amount is 1,230.95 ETB. Thank you for using telebirr Ethio telecom",
                null,
            ),
        )
        assertEquals(4028194181244726996L, SupabaseTransactionKey.of("manual", "[MANUAL ENTRY] DEBIT • ETB 50.0 • Shop", "manual-abc"))
    }

    private fun sampleTransaction(
        id: Long = 1,
        sender: String = "CBE",
        type: String = "CREDIT",
        refNumber: String? = "FT26094DXLMX82338166",
        counterparty: String? = "Alice",
        rawBody: String = "Dear user your account was credited",
    ) = TransactionEntity(
        id = id,
        sender = sender,
        bankName = "Commercial Bank of Ethiopia",
        type = type,
        amount = 600.0,
        balance = 1000.0,
        counterparty = counterparty,
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
