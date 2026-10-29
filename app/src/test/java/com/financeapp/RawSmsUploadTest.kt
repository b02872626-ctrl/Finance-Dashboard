package com.financeapp

import com.financeapp.data.repository.TransactionRepository
import com.financeapp.parsing.SmsMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which bank SMS get uploaded: anything the parser ignores (one-time codes,
 * promos) stays on the phone — Play SMS policy and Web/privacy.html.
 */
class RawSmsUploadTest {

    private fun status(body: String) =
        TransactionRepository.rawParseStatus(SmsMessage(sender = "CBE", body = body, timestamp = 0L))

    @Test
    fun `one-time code from a bank is ignored, so never uploaded`() {
        assertEquals(TransactionRepository.RAW_IGNORED, status("Your OTP is 482913. Do not share it with anyone."))
        assertEquals(TransactionRepository.RAW_IGNORED, status("<#> 551203 is your CBE Birr verification code"))
    }

    @Test
    fun `bank notice with no money in it is ignored`() {
        assertEquals(TransactionRepository.RAW_IGNORED, status("Dear customer, our branches will be closed on Monday."))
    }

    @Test
    fun `transaction is uploaded as parsed`() {
        assertEquals(
            "PARSED",
            status(
                "Dear Yohannes your Account 1*****8166 has been Credited with ETB 6,000.00 " +
                    "from Robel Tadese, on 31/03/2026 at 18:44:07 with Ref No FT26090Y2Q8F " +
                    "Your Current Balance is ETB 142,306.95. Thank you for Banking with CBE!"
            ),
        )
    }

    @Test
    fun `money-shaped message no parser reads is uploaded as unparsed`() {
        assertEquals("UNPARSED", status("Xyz ETB 120.00 was debited somewhere odd"))
    }
}
