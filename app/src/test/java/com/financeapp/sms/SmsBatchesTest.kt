package com.financeapp.sms

import com.financeapp.parsing.SmsParserEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsBatchesTest {

    @Test
    fun `banks come smallest first and keep their own order`() {
        val sms = listOf("CBE" to 1, "127" to 2, "CBE" to 3, "BOA" to 4, "127" to 5, "CBE" to 6)
        val batches = SmsBatches.byBank(sms) { SmsParserEngine.senderBankName(it.first) }
        assertEquals(listOf("Bank of Abyssinia", "Telebirr", "Commercial Bank of Ethiopia"), batches.map { it.first })
        assertEquals(listOf(1, 3, 6), batches.last().second.map { it.second })
    }

    @Test
    fun `sender spellings of one bank share a card`() {
        val sms = listOf("BOA", "Abyssinia", "boa")
        val batches = SmsBatches.byBank(sms) { SmsParserEngine.senderBankName(it) }
        assertEquals(1, batches.size)
        assertEquals("Bank of Abyssinia", batches.single().first)
    }

    @Test
    fun `progress moves one bank at a time and never goes back`() {
        val start = SmsIngestWorker.ScanProgress.start(listOf("A" to listOf(1, 2), "B" to listOf(1, 2, 3)))
        assertEquals(0, start.done)
        assertEquals(5, start.total)
        val mid = start.withBank(0, 2).withBank(1, 1)
        assertEquals(3, mid.done)
        assertTrue(mid.banks[0].finished)
        assertFalse(mid.banks[1].finished)
    }

    @Test
    fun `unknown senders keep their own name`() {
        assertEquals("SomeNewBank", SmsParserEngine.senderBankName(" SomeNewBank "))
        assertEquals("Telebirr", SmsParserEngine.senderBankName("127"))
    }
}
