package com.financeapp

import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.SmsParserEngine
import com.financeapp.parsing.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Parser tests for CBEBirr (CBE's mobile money product). Bodies are
 * verbatim from the user's Samsung S22 inbox.
 */
class CbeBirrParserTest {

    private fun sms(body: String, sender: String = "CBEBirr", ts: Long = 1_716_550_000_000L) =
        SmsMessage(sender = sender, body = body, timestamp = ts)

    // ── Credit ────────────────────────────────────────────────────────────
    @Test fun `credit — basic wallet top-up`() {
        val body = "Dear nahusenay, your CBE Birr account has been credited with 200.00Br. " +
                "on 24/05/26 14:14,Txn ID DEO31DWV8EJ. Your balance is 204.60Br. Thank you!"
        val tx = SmsParserEngine.parse(sms(body))
        assertNotNull(tx)
        assertEquals("CBE Birr", tx!!.bankName)
        assertEquals(TransactionType.CREDIT.name, tx.type)
        assertEquals(200.00, tx.amount, 0.001)
        assertEquals(204.60, tx.balance!!, 0.001)
        assertEquals("DEO31DWV8EJ", tx.refNumber)
    }

    @Test fun `credit — large amount with comma`() {
        val body = "Dear nahusenay, your CBE Birr account has been credited with 5,200.00Br. " +
                "on 21/05/26 09:01,Txn ID DEL71DNZGZJ. Your balance is 5,207.56Br. Thank you!"
        val tx = SmsParserEngine.parse(sms(body))
        assertNotNull(tx)
        assertEquals(5200.00, tx!!.amount, 0.001)
        assertEquals(5207.56, tx.balance!!, 0.001)
    }

    // ── ATM Withdrawal (DEBIT with disaster fund + VAT) ───────────────────
    @Test fun `withdrawal — captures amount disaster fund and VAT`() {
        val body = "Dear nahusenay, you have withdrawn 200.00Br. from CBE ATM on 24/05/26 14:21," +
                "Txn ID DEO11DWVQAX. You have paid 0.15Br. for 5% Disaster Risk Response Fund " +
                "& 15% Tax and Services Charge 0.70Br.. Your CBE Birr account balance is 3.75Br." +
                "Thank You! For your feedback please click the link https://shorturl.at/gy3A0"
        val tx = SmsParserEngine.parse(sms(body))
        assertNotNull(tx)
        assertEquals("CBE Birr", tx!!.bankName)
        assertEquals(TransactionType.DEBIT.name, tx.type)
        assertEquals(200.00, tx.amount, 0.001)
        assertEquals(0.15, tx.disasterFund!!, 0.001)
        assertEquals(0.70, tx.vat!!, 0.001)
        assertEquals(200.85, tx.totalCharged!!, 0.001)
        assertEquals(3.75, tx.balance!!, 0.001)
        assertEquals("DEO11DWVQAX", tx.refNumber)
        assertEquals("CBE ATM", tx.counterparty)
    }

    @Test fun `withdrawal — larger amount with comma`() {
        val body = "Dear nahusenay, you have withdrawn 3,000.00Br. from CBE ATM on 15/04/26 10:22," +
                "Txn ID DDF91ALEUHV. You have paid 2.11Br. for 5% Disaster Risk Response Fund " +
                "& 15% Tax and Services Charge 10.50Br.. Your CBE Birr account balance is 2,498.27Br."
        val tx = SmsParserEngine.parse(sms(body))
        assertNotNull(tx)
        assertEquals(3000.00, tx!!.amount, 0.001)
        assertEquals(2.11, tx.disasterFund!!, 0.001)
        assertEquals(10.50, tx.vat!!, 0.001)
        assertEquals(2498.27, tx.balance!!, 0.001)
    }

    // ── P2P Transfer ──────────────────────────────────────────────────────
    @Test fun `transfer — captures recipient name and dual refs`() {
        val body = "Dear nahusenay, you have successfully transferred 5,100.00Br. to " +
                "1000469454607-NAHUSENAY G/AMLAK TEKA on 21-05-2026 09:04:50." +
                "Txn ID DEL41DNZRPK,FT26141QJJK9.Your CBEBirr account balance is 107.56Br." +
                "Thank You for Choosing CBE Birr ! For your feedback please click the link https://shorturl.at/gy3A0"
        val tx = SmsParserEngine.parse(sms(body))
        assertNotNull(tx)
        assertEquals("CBE Birr", tx!!.bankName)
        assertEquals(TransactionType.TRANSFER_OUT.name, tx.type)
        assertEquals(5100.00, tx.amount, 0.001)
        assertEquals(107.56, tx.balance!!, 0.001)
        assertEquals("NAHUSENAY G/AMLAK TEKA", tx.counterparty)
        assertEquals("DEL41DNZRPK", tx.refNumber)
    }

    @Test fun `transfer — smaller amount, different recipient`() {
        val body = "Dear nahusenay, you have successfully transferred 200.00Br. to " +
                "1000573888058-KALKIDAN TARIKU BERIGA on 11-05-2026 19:11:09." +
                "Txn ID DEB81CVIRBS,FT26131H8N8F.Your CBEBirr account balance is 215.13Br."
        val tx = SmsParserEngine.parse(sms(body))
        assertNotNull(tx)
        assertEquals(200.00, tx!!.amount, 0.001)
        assertEquals("KALKIDAN TARIKU BERIGA", tx.counterparty)
    }

    // ── Skip cases (not real transactions) ────────────────────────────────
    @Test fun `cash-out voucher is not a real transaction`() {
        val body = "Dear Customer, as per your request for CBE Birr ATM cash out voucher, " +
                "the voucher number is 12195, Amount 200.00Br.. The voucher will expire " +
                "after 24 Hr at 25/05/26 14:13. Cash out the money from your nearby CBE ATM."
        val tx = SmsParserEngine.parse(sms(body))
        // Unknown parser returns UNKNOWN, which the repo filters out.
        // Just verify it doesn't get parsed as one of our real types.
        if (tx != null) {
            // If we caught it as something, it must be UNKNOWN.
            assertEquals(TransactionType.UNKNOWN.name, tx.type)
        }
    }

    @Test fun `OTP message is not a real transaction`() {
        val body = " <#> 462907 Please keep this code private and do not share it with anyone." +
                "Your Transaction OTP is 462907 Yf9mxp4+Cps"
        val tx = SmsParserEngine.parse(sms(body))
        if (tx != null) assertEquals(TransactionType.UNKNOWN.name, tx.type)
    }

    @Test fun `wrong PIN is not a real transaction`() {
        val body = "Sorry, you have entered the wrong PIN. Please check and try again. Thank you!"
        val tx = SmsParserEngine.parse(sms(body))
        if (tx != null) assertEquals(TransactionType.UNKNOWN.name, tx.type)
    }

    // ── Sanity: financial sender allowlist ────────────────────────────────
    @Test fun `CBEBirr is a recognized financial sender`() {
        assert(SmsParserEngine.isFinancialSms("CBEBirr"))
        assert(SmsParserEngine.isFinancialSms("cbebirr"))
    }

    @Test fun `non-CBEBirr sender does not match these parsers`() {
        val body = "Dear nahusenay, your CBE Birr account has been credited with 200.00Br. " +
                "on 24/05/26 14:14,Txn ID DEO31DWV8EJ. Your balance is 204.60Br."
        // Same body but sender = "CBE" — the CBEBirr parsers must reject it.
        // Verify by checking the bank name doesn't become "CBE Birr".
        val tx = SmsParserEngine.parse(sms(body, sender = "CBE"))
        if (tx != null) {
            // Either Unknown or one of the CBE parsers — but NOT CBEBirr.
            assert(tx.bankName != "CBE Birr") {
                "CBE sender should not be parsed by CBEBirr parsers"
            }
        }
    }
}
