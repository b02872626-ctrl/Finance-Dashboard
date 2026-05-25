package com.financeapp

import com.financeapp.data.model.TransactionEntity
import com.financeapp.export.TransactionCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class TransactionCsvTest {

    @Test
    fun `CSV header is stable`() {
        val csv = TransactionCsv.build(emptyList())
        val header = csv.substringBefore("\n")
        assertEquals(
            "id,dateTimeMillis,dateTimeLocal,sender,bankName,type,category,amount,currency,balance,counterparty,accountNumber,refNumber,serviceCharge,vat,disasterFund,totalCharged,rawBody,createdAtMillis",
            header
        )
    }

    @Test
    fun `CSV escapes commas quotes and newlines`() {
        val originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val tx = TransactionEntity(
                id = 1,
                sender = "CBE",
                bankName = "Commercial Bank of Ethiopia",
                type = "CREDIT",
                category = "Food",
                amount = 1000.0,
                balance = 2000.0,
                counterparty = "Mary, Jane",
                accountNumber = "1*****8166",
                refNumber = "REF\"123",
                dateTime = 0L,
                serviceCharge = null,
                vat = null,
                disasterFund = null,
                totalCharged = null,
                currency = "ETB",
                rawBody = "Hello \"world\"\nNew line",
                createdAt = 0L
            )

            val csv = TransactionCsv.build(listOf(tx))
            assertTrue(csv.contains("\"Mary, Jane\""))
            assertTrue(csv.contains("\"REF\"\"123\""))
            assertTrue(csv.contains("\"Hello \"\"world\"\"\nNew line\""))
            assertTrue(csv.contains(",1970-01-01 00:00:00,"))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }
}
