package com.financeapp

import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.SmsParserEngine
import com.financeapp.parsing.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the SMS parsing engine.
 * These run on JVM with no Android dependencies — pure Kotlin logic.
 */
class SmsParserTest {

    @Test
    fun `CBE new 'successfully transferred' format extracts counterparty from parens`() {
        // Real corpus sample of the post-mid-2026 CBE transfer SMS that the
        // older "transfered" parser silently rejected. Tests the relaxed
        // canParse + the toNewRe regex + the Disaster Recovery alt label.
        val sms = SmsMessage(
            sender = "CBE",
            body = "Dear  Nahusenay G/amlak Teka You have successfully transferred ETB 250.61 " +
                "from account 1********4607 to account 1********9258 (Firehiwot Asmelash Belay). " +
                "Service charge of ETB 0.50 and VAT(15%) of ETB0.08 and Disaster Recovery(5%) of 0.03 " +
                "with total of ETB250.61 .Your current balance is ETB-243.21.",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER_OUT.name, tx!!.type)
        assertEquals(250.61, tx.amount, 0.001)
        assertEquals("Firehiwot Asmelash Belay", tx.counterparty)
        assertEquals("1********4607", tx.accountNumber)
        assertEquals(0.50, tx.serviceCharge!!, 0.001)
        assertEquals(0.08, tx.vat!!, 0.001)
        assertEquals(0.03, tx.disasterFund!!, 0.001)
        assertEquals(250.61, tx.totalCharged!!, 0.001)
        assertEquals(-243.21, tx.balance!!, 0.001)
    }

    @Test
    fun `CBE credit SMS is parsed correctly`() {
        val sms = SmsMessage(
            sender = "CBE",
            body = "Dear Yohannes your Account 1*****8166 has been Credited with ETB 6,000.00 " +
                "from Robel Tadese, on 31/03/2026 at 18:44:07 with Ref No FT26090Y2Q8F " +
                "Your Current Balance is ETB 142,306.95. Thank you for Banking with CBE! " +
                "https://apps.cbe.com.et:100/?id=FT26090Y2Q8F82338166",
            timestamp = System.currentTimeMillis()
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(6000.00, tx.amount, 0.001)
        assertEquals(142306.95, tx.balance!!, 0.001)
        assertEquals("Robel Tadese", tx.counterparty)
        assertEquals("1*****8166", tx.accountNumber)
        assertEquals("FT26090Y2Q8F", tx.refNumber)
        assertEquals("Commercial Bank of Ethiopia", tx.bankName)
        assertEquals("https://apps.cbe.com.et:100/?id=FT26090Y2Q8F82338166", tx.receiptLink)
    }

    @Test
    fun `CBE credit SMS with comma in name parses counterparty correctly`() {
        val sms = SmsMessage(
            sender = "CBE",
            body = "Dear Yohannes your Account 1*****8166 has been Credited with ETB 500.00 " +
                "from Mary, Jane on 01/04/2026 at 10:00:00 with Ref No TESTREF123 " +
                "Your Current Balance is ETB 1,000.00.",
            timestamp = System.currentTimeMillis()
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals("Mary", tx!!.counterparty)
    }

    @Test
    fun `CBE received SMS with receipt URL path is parsed correctly`() {
        val sms = SmsMessage(
            sender = "CBE",
            body = "Dear Nahusenay G/amlak Teka You have received ETB 500.00 from account 1**8741 " +
                "(Lisanework Mesfin Kuma) to your account 1**4607. Your current balance is ETB568.97. " +
                "Thanks for Banking with CBE. https://Mbreciept.cbe.com.et/FT260974GL3D-69454607",
            timestamp = System.currentTimeMillis()
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(500.00, tx.amount, 0.001)
        assertEquals(568.97, tx.balance!!, 0.001)
        assertEquals("Lisanework Mesfin Kuma", tx.counterparty)
        assertEquals("1**4607", tx.accountNumber)
        assertEquals("FT260974GL3D-69454607", tx.refNumber)
        assertEquals("https://Mbreciept.cbe.com.et/FT260974GL3D-69454607", tx.receiptLink)
    }

    @Test
    fun `CBE debit SMS is parsed correctly`() {
        val sms = SmsMessage(
            sender = "CBE",
            body = "Dear Yohannes Tadesse your Account 1****8166 has been debited with ETB 1005.75 " +
                "including Service charge ETB0.00ETB0.00 and VAT(15%) . " +
                "Your Current Balance is ETB 146479.08. Thank you for Banking with CBE!",
            timestamp = System.currentTimeMillis()
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(1005.75, tx.amount, 0.001)
        assertEquals(146479.08, tx.balance!!, 0.001)
        assertEquals("Commercial Bank of Ethiopia", tx.bankName)
    }

    @Test
    fun `CBE transfer SMS is parsed correctly`() {
        val sms = SmsMessage(
            sender = "CBE",
            body = "Dear Yohannes, You have transfered ETB 600.00 to Fetlework Ansa on 03/04/2026 " +
                "at 22:46:55 from your account 1*****8166. Your account has been debited with " +
                "a S.charge of ETB 0.50 and VAT(15%) of ETB0.08 and Disaster Fund (5%) of ETB0.03, " +
                "with a total of ETB 600.61. Your Current Balance is ETB 78,471.86. " +
                "Thank you for Banking with CBE! https://apps.cbe.com.et:100/?id=FT26094DXLMX82338166",
            timestamp = System.currentTimeMillis()
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER_OUT.name, tx!!.type)
        assertEquals(600.00, tx.amount, 0.001)
        assertEquals(78471.86, tx.balance!!, 0.001)
        assertEquals("Fetlework Ansa", tx.counterparty)
        assertEquals(0.50, tx.serviceCharge!!, 0.001)
        assertEquals(0.08, tx.vat!!, 0.001)
        assertEquals(0.03, tx.disasterFund!!, 0.001)
        assertEquals(600.61, tx.totalCharged!!, 0.001)
        assertEquals("FT26094DXLMX82338166", tx.refNumber)
    }

    @Test
    fun `Telebirr payment SMS is parsed correctly`() {
        val sms = SmsMessage(
            sender = "127",
            body = "Dear yohannes You have paid ETB 307.50 for goods purchased from " +
                "500423 - CHAPA FINANCIAL TECHNOLOGY SHARE COMPANY on 01/04/2026 15:33:26. " +
                "Your transaction number is  DD18GI0O82. Your current balance is ETB 786.98. " +
                "To download your payment information please click this link: " +
                "https://transactioninfo.ethiotelecom.et/receipt/DD18GI0O82 " +
                "Thank you for using telebirr Ethio telecom",
            timestamp = System.currentTimeMillis()
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.PAYMENT.name, tx!!.type)
        assertEquals(307.50, tx.amount, 0.001)
        assertEquals(786.98, tx.balance!!, 0.001)
        assertEquals("DD18GI0O82", tx.refNumber)
        assertEquals("Telebirr", tx.bankName)
        assertTrue(tx.counterparty!!.contains("CHAPA"))
        assertEquals("https://transactioninfo.ethiotelecom.et/receipt/DD18GI0O82", tx.receiptLink)
    }

    @Test
    fun `Telebirr withdrawal SMS is parsed correctly`() {
        val sms = SmsMessage(
            sender = "127",
            body = "Dear YEABSIRA The request to withdraw ETB 200.00 from your telebirr account 251973797336 " +
                "via secret code 149086 on 2026-04-06 11:30:02 using Bank of Abyssinia ATM with transaction number " +
                "DD66MGD9OM is successfully completed. The service fee (including 15% VAT) is ETB 1.15. " +
                "Your current Account balance is ETB 1,965.81. To download your payment information please click this link: " +
                "https://transactioninfo.ethiotelecom.et/receipt/DD66MGD9OM",
            timestamp = 0L
        )

        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(200.00, tx.amount, 0.001)
        assertEquals(1965.81, tx.balance!!, 0.001)
        assertEquals("DD66MGD9OM", tx.refNumber)
        assertEquals("Telebirr", tx.bankName)
        assertEquals("251973797336", tx.accountNumber)
        assertEquals("Bank of Abyssinia ATM", tx.counterparty)
        assertEquals(1.15, tx.totalCharged!!, 0.001)
        assertEquals("https://transactioninfo.ethiotelecom.et/receipt/DD66MGD9OM", tx.receiptLink)
        assertTrue(tx.dateTime > 0L)
    }

    @Test
    fun `Non-financial sender returns null`() {
        val sms = SmsMessage(sender = "GoogleOTP", body = "Your OTP is 123456", timestamp = 0L)
        assertNull(SmsParserEngine.parse(sms))
    }

    @Test
    fun `isFinancialSms returns true for CBE and 127`() {
        assertTrue(SmsParserEngine.isFinancialSms("CBE"))
        assertTrue(SmsParserEngine.isFinancialSms("cbe"))
        assertTrue(SmsParserEngine.isFinancialSms("127"))
        assertFalse(SmsParserEngine.isFinancialSms("GoogleOTP"))
    }

    @Test
    fun `Completely garbled CBE SMS returns UNKNOWN without crashing`() {
        val sms = SmsMessage(
            sender = "CBE",
            body = "!!!! GARBLED NONSENSE @@@@ 🤯 / nothing useful here",
            timestamp = System.currentTimeMillis()
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.UNKNOWN.name, tx!!.type)
    }

    @Test
    fun `Empty body does not crash parser`() {
        val sms = SmsMessage(sender = "CBE", body = "", timestamp = 0L)
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.UNKNOWN.name, tx!!.type)
    }

    @Test
    fun `Amount with comma parses correctly`() {
        assertEquals(6000.00, ParserUtils.parseAmount("6,000.00") ?: 0.0, 0.001)
        assertEquals(142306.95, ParserUtils.parseAmount("142,306.95") ?: 0.0, 0.001)
        assertEquals(0.0, ParserUtils.parseAmount(null) ?: 0.0, 0.001)
    }

    @Test
    fun `DateTime parsing is correct`() {
        val ts = ParserUtils.parseDateTime("31/03/2026", "18:44:07")
        assertNotNull(ts)
        assertTrue(ts!! > 0)
    }

    // ---- Tests trained from real-world corpus (i_raw_data_rows.csv) ----

    @Test
    fun `Telebirr P2P received with parenthetical phone extracts counterparty`() {
        val sms = SmsMessage(
            sender = "127",
            body = "Dear Nahusenay \n" +
                "You have received ETB 142.00 from Abel Abebe(2519****8602)  on 20/10/2025 13:52:01. " +
                "Your transaction number is CJK1KUMOTB. Your current E-Money Account balance is ETB 1,741.56.\n" +
                "Thank you for using telebirr\nEthio telecom",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(142.00, tx.amount, 0.001)
        assertEquals("Abel Abebe", tx.counterparty)
        assertEquals(1741.56, tx.balance!!, 0.001)
        assertEquals("CJK1KUMOTB", tx.refNumber)
    }

    @Test
    fun `Telebirr transfer to bank account extracts counterparty`() {
        val sms = SmsMessage(
            sender = "127",
            body = "Dear Nahusenay\n" +
                "You have transferred ETB 2,000.00 successfully from your telebirr account 251947343024 " +
                "to Commercial Bank of Ethiopia account number 1000439946826 on 19/10/2025 10:58:03. " +
                "Your telebirr transaction number is CJJ4K37VOY and your bank transaction number is FT25292P9B3V. " +
                "The service fee is  ETB 7.83 and  15% VAT on the service fee is ETB 1.17. " +
                "Your current balance is ETB 1,873.56.",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        // Telebirr → CBE is the user moving their own money between accounts,
        // so we now tag it as INTERNAL_TRANSFER (not TRANSFER_OUT) so the
        // dashboard's expense totals don't double-count.
        assertEquals(TransactionType.INTERNAL_TRANSFER.name, tx!!.type)
        assertEquals(2000.00, tx.amount, 0.001)
        // Counterparty is now shortened to "to CBE 1000439946826" for the chip.
        assertTrue(tx.counterparty!!.contains("CBE"))
        assertTrue(tx.counterparty!!.contains("1000439946826"))
        assertEquals("CJJ4K37VOY", tx.refNumber)
        assertEquals(7.83, tx.serviceCharge!!, 0.001)
        assertEquals(1.17, tx.vat!!, 0.001)
        assertEquals(1873.56, tx.balance!!, 0.001)
    }

    @Test
    fun `Telebirr telecom-package payment uses package name as counterparty`() {
        val sms = SmsMessage(
            sender = "127",
            body = "Dear Nahusenay\n" +
                "You have paid ETB 63.00 for package Daily unlimited Internet purchase made for 947343024 " +
                "on 19/10/2025 12:54:50. Your transaction number is  CJJ0K5O3AS. Your current balance is ETB 1,810.56.",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.PAYMENT.name, tx!!.type)
        assertEquals(63.00, tx.amount, 0.001)
        assertEquals("Telecom package: Daily unlimited Internet", tx.counterparty)
        assertEquals("CJJ0K5O3AS", tx.refNumber)
    }

    @Test
    fun `CBE debit with modern Service charge of ETB extracts fees and total`() {
        val sms = SmsMessage(
            sender = "CBE",
            body = "Dear Nahusenay your Account 1*********4607 has been debited with ETB3,600.00 ." +
                "Service charge of  ETB10 and VAT(15%) of ETB1.50 with a total of ETB3611. " +
                "Your Current Balance is ETB 46.58. Thank you for Banking with CBE! " +
                "https://apps.cbe.com.et:100/?id=FT25292XBN2C69454607",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(3600.00, tx.amount, 0.001)
        assertEquals(10.0, tx.serviceCharge!!, 0.001)
        assertEquals(1.50, tx.vat!!, 0.001)
        assertEquals(3611.0, tx.totalCharged!!, 0.001)
        assertEquals(46.58, tx.balance!!, 0.001)
        assertEquals("FT25292XBN2C69454607", tx.refNumber)
    }

    @Test
    fun `CBE transfer with 15 percent VAT format extracts VAT`() {
        val sms = SmsMessage(
            sender = "CBE",
            body = "Dear Nahusenay, You have transfered ETB 5,300.00 to Abreham Negusse on 31/10/2025 " +
                "at 19:35:40 from your account 1*********4607. Your account has been debited with " +
                "a S.charge of ETB 2.00 and 15% VAT of ETB0.30, with a total of ETB5302.30. " +
                "Your Current Balance is ETB 24,691.93. " +
                "https://apps.cbe.com.et:100/?id=FT25305L5K5169454607",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER_OUT.name, tx!!.type)
        assertEquals(5300.00, tx.amount, 0.001)
        assertEquals("Abreham Negusse", tx.counterparty)
        assertEquals(2.00, tx.serviceCharge!!, 0.001)
        assertEquals(0.30, tx.vat!!, 0.001)
        assertEquals(5302.30, tx.totalCharged!!, 0.001)
    }

    @Test
    fun `CBE credit-by merchant SMS parses amount and counterparty`() {
        val sms = SmsMessage(
            sender = "CBE",
            body = "Dear Nahusenay your Account 1********4607 has been credited by DAWIT WONDWOSSEN/MDM DEVELOPERS " +
                "with ETB 44600.00. Your Current Balance is ETB 46049.85. Thank you for Banking with CBE! " +
                "https://apps.cbe.com.et:100/BranchReceipt/FT25324GLDGL&69454607",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(44600.00, tx.amount, 0.001)
        assertEquals("DAWIT WONDWOSSEN/MDM DEVELOPERS", tx.counterparty)
        assertEquals(46049.85, tx.balance!!, 0.001)
    }

    @Test
    fun `Telebirr Mela loan disbursement is parsed as CREDIT with contract ref`() {
        val sms = SmsMessage(
            sender = "127",
            body = "Dear Nahusenay,   \n" +
                "Your credit request with CKQ2FYZCJC contract number is successful. " +
                "The credit amount is ETB 500.00 and facilitation fee ETB 32.50 with due date 25/01/2026 " +
                "and the daily fee will be from 0.50% to 0,80% depending on your credit limit. " +
                "Your current available credit limit ETB1,700.00.",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(500.00, tx.amount, 0.001)
        assertEquals("CKQ2FYZCJC", tx.refNumber)
        assertEquals(32.50, tx.serviceCharge!!, 0.001)
        assertTrue(tx.counterparty!!.contains("Mela loan"))
        assertTrue(tx.counterparty!!.contains("25/01/2026"))
    }

    @Test
    fun `Telebirr Mela repayment paid-successfully variant is parsed as DEBIT`() {
        val sms = SmsMessage(
            sender = "127",
            body = "Dear Nahusenay,   \n" +
                "your outstanding Credit amount has been paid successfully. The paid amount is ETB 538.90, " +
                "your monthly outstanding amount is ETB 0.00 and your total outstanding amount is ETB 0.00.",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(538.90, tx.amount, 0.001)
        assertEquals("Mela loan repayment", tx.counterparty)
    }

    @Test
    fun `Telebirr Mela repaid variant with days contract is parsed as DEBIT`() {
        val sms = SmsMessage(
            sender = "127",
            body = "Dear Nahusenay\n" +
                "You have repaid 3,659.67 ETB, for your Mela 50 days contract. " +
                "Your current unpaid credit amount is 0.00 ETB.",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(3659.67, tx.amount, 0.001)
        assertTrue(tx.counterparty!!.contains("50 days"))
    }

    @Test
    fun `Telebirr wallet-credit SMS is parsed as CREDIT`() {
        val sms = SmsMessage(
            sender = "127",
            body = "Dear Nahusenay \n" +
                "Your telebirr account has been credited with ETB 500.00. " +
                "Your current telebirr E-Money Account balance is ETB 500.44.",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(500.00, tx.amount, 0.001)
        assertEquals(500.44, tx.balance!!, 0.001)
        assertEquals("Telebirr wallet top-up", tx.counterparty)
    }

    @Test
    fun `Lottery message is ignored`() {
        val sms = SmsMessage(
            sender = "127",
            body = "Dear customer, you get 1 point and 1 lottery ticket with lottery ID: TL1234567890.",
            timestamp = 0L
        )
        assertNull(SmsParserEngine.parse(sms))
    }

    @Test
    fun `Berhan Bank debit SMS extracts amount, account, ref, balance`() {
        val sms = SmsMessage(
            sender = "Berhan Bank",
            body = "Dear NATNAEL GETINET DAGNE; A/C No. *********9067 has been debited " +
                "ETB100.00 on 21-03-2026 Ref. APPMN01BXYT. Bal. ETB79.94. " +
                "Receipt: https://transactioninfo.berhanonline.et/ereceipt/99921032026298550.pdf " +
                "Berhan Stress Free Banking!",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals("Berhan Bank", tx.bankName)
        assertEquals(100.00, tx.amount, 0.001)
        assertEquals("*********9067", tx.accountNumber)
        assertEquals("APPMN01BXYT", tx.refNumber)
        assertEquals(79.94, tx.balance!!, 0.001)
        // Engine fallback should fill counterparty since the body doesn't name one
        assertTrue(tx.counterparty!!.contains("Berhan"))
    }

    @Test
    fun `Berhan Bank simple credit SMS is CREDIT type with no counterparty`() {
        val sms = SmsMessage(
            sender = "Berhan Bank",
            body = "Dear Natnael, A/C No. 103002XXXXX67 has been credited ETB 16,000 " +
                "on 23-Mar-2026. Reference is 17742610528660003539. " +
                "Balance is ETB 16,029.7. To Natnael Getinet . Berhan Stress Free Banking",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(16000.00, tx.amount, 0.001)
        assertEquals("103002XXXXX67", tx.accountNumber)
        assertEquals("17742610528660003539", tx.refNumber)
        assertEquals(16029.7, tx.balance!!, 0.001)
    }

    @Test
    fun `Berhan Bank IPS-incoming credit is tagged as INTERNAL_TRANSFER from CBE`() {
        val sms = SmsMessage(
            sender = "Berhan Bank",
            body = "Dear Natnael, A/C No. 103002XXXXX67 has been credited ETB 5,000 " +
                "on 01-May-2026. Reference is 17776111847890465681. " +
                "Balance is ETB 5,034.95. Ips_incoming Txn From {cbetetaa__1000482226926} . " +
                "Berhan Stress Free Banking",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        // Cross-bank IPS = own-account movement, should not count as income
        assertEquals(TransactionType.INTERNAL_TRANSFER.name, tx!!.type)
        assertEquals(5000.00, tx.amount, 0.001)
        assertTrue(tx.counterparty!!.contains("CBE"))
        assertTrue(tx.counterparty!!.contains("1000482226926"))
    }

    @Test
    fun `Berhan Amharic Fayda marketing SMS is ignored`() {
        val sms = SmsMessage(
            sender = "Berhan Bank",
            body = "ለክቡራን ደንበኞቻችን በሙሉ! የኢትዮጵያ ብሔራዊ ባንክ ኅዳር 25 ቀን 2018 ዓ.ም ባወጣው መመርያ ላይ " +
                "ሁሉም የባንክ ደንበኞች የትኛውንም የባንክ አገልግሎት ለማግኘት እና ሒሳባቸውን ለማንቀሳቀስ የባንክ ሂሳብ ቁጥራቸውን " +
                "ከብሔራዊ መታወቂያ(ፋይዳ) ጋር እንዲያጣምሩ አሳስቧል። ብርሃን ባንክ",
            timestamp = 0L
        )
        assertNull(SmsParserEngine.parse(sms))
    }

    @Test
    fun `CBE 2026 'A debit transaction has occurred' format parses correctly`() {
        // Real corpus sample of a CBE debit variant introduced ~2026.
        // Uses "A debit transaction of ETB X has occurred on your account Y"
        // instead of "your Account Y has been debited with ETB X".
        // Also uses "Disaster Recovery" not "Disaster Fund" and drops the
        // "ETB" prefix from some sub-amounts.
        val sms = SmsMessage(
            sender = "CBE",
            body = "Dear Yabsira Endegena Andarge A debit transaction of ETB 15.0. " +
                "has occurred on your account 1********9408. Service charge of ETB 0.00 " +
                "and VAT(15%) of 0.0 and Disaster Recovery(5%) of 0.00 with total of ETB15.00 " +
                ".Your current balance is ETB7.58. Thank you",
            timestamp = 0L
        )
        val tx = SmsParserEngine.parse(sms)
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(15.0, tx.amount, 0.001)
        assertEquals("1********9408", tx.accountNumber)
        assertEquals(0.0, tx.vat!!, 0.001)
        assertEquals(0.0, tx.disasterFund!!, 0.001)
        assertEquals(15.00, tx.totalCharged!!, 0.001)
        assertEquals(7.58, tx.balance!!, 0.001)
    }

    @Test
    fun `Berhan English Fayda marketing SMS is ignored`() {
        val sms = SmsMessage(
            sender = "Berhan Bank",
            body = "Dear Customer, In accordance with the directive from the National Bank " +
                "of Ethiopia, all customers are required to link their Fayda ID to access " +
                "banking services and continue using their existing accounts. Berhan Bank",
            timestamp = 0L
        )
        assertNull(SmsParserEngine.parse(sms))
    }
}
