package com.financeapp

import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.SmsParserEngine
import com.financeapp.parsing.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * NAH-29 (Nov 2026): real money SMS that were silently dropped as "ignored".
 * Found by replaying the i_raw_data export (ParserCoverageReport →
 * build/parser-ignored.txt). Bodies are real; names, accounts, phones,
 * references and links are masked.
 */
class DroppedMoneyParserTest {

    private fun parse(sender: String, body: String) =
        SmsParserEngine.parse(SmsMessage(sender = sender, body = body, timestamp = 1_757_000_000_000L))

    // ── BoA: the "Link your Fayda" footer (since 1.0.12) dropped every alert ──

    @Test fun `BoA credit with Fayda footer is money in`() {
        val tx = parse(
            "BOA",
            "Dear Abebe, your account 1*****62 was credited with ETB 400.00 by  Abebe Kebede Alemu . " +
                "Available Balance: ETB 658.67.\nReceipt: https://cs.bankofabyssinia.com/slip/?trx=FT26000AAAA10104\n" +
                "Link your Fayda: https://cs.bankofabyssinia.com/fayda_connect \n" +
                "For help, call 8397 (24/7 Toll-Free). Bank of Abyssinia.",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(400.0, tx.amount, 0.001)
        assertEquals(658.67, tx.balance!!, 0.001)
    }

    @Test fun `BoA debit with Fayda footer is money out`() {
        val tx = parse(
            "BOA",
            "Dear Abebe, your account 1*****62 was debited with ETB 402.30. Available Balance: ETB 256.37.\n" +
                "Receipt: https://cs.bankofabyssinia.com/slip/?trx=FT26000BBBB08962\n" +
                "Link your Fayda: https://cs.bankofabyssinia.com/fayda_connect \n" +
                "For help, call 8397 (24/7 Toll-Free). Bank of Abyssinia.",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(402.30, tx.amount, 0.001)
    }

    @Test fun `Fayda notices and the BoA prize promo are still ignored`() {
        assertNull(parse("BOA", "Dear Customer, please link your Fayda ID with your account at any branch. Bank of Abyssinia."))
        assertNull(
            parse(
                "BOA",
                "ያልታሰበ ሲሳይ! ባንካችን አቢሲንያ በአፖሎ ዲጂታል ባንክ መተግበሪያ ሂሳብ በመክፈት ከ1,000 ብር በላይ ከቆጠቡና " +
                    "መተግበሪያውን ካጋሩ ደንበኞች መካከል 2 ዕድለኞች ወጪያቸው ተችሎ የባንኮክ ጉዞ አሸናፊዎች ሆነዋል።",
            ),
        )
    }

    // ── Berhan: new "debited 2000 on 2026-05-02" wording (from May 2026) ──

    @Test fun `Berhan debit without ETB before the amount`() {
        val tx = parse(
            "Berhan Bank",
            "Dear ABEBE KEBEDE ALEMU; A/C No. *********1234 has been debited 2000 on 2026-05-02 Ref. APPMAAAAAAA. " +
                "Bal. ETB3,024.87. Receipt: https://transactioninfo.berhanonline.et/ereceipt/99900000000000000.pdf " +
                "Berhan Stress Free Banking!",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(2000.0, tx.amount, 0.001)
        assertEquals(3024.87, tx.balance!!, 0.001)
        assertEquals("APPMAAAAAAA", tx.refNumber)
    }

    @Test fun `Berhan debit old wording still parses`() {
        val tx = parse(
            "Berhan Bank",
            "Dear ABEBE KEBEDE ALEMU; A/C No. *********1234 has been debited ETB100.00 on 21-03-2026 Ref. APPMBBBBBBB. " +
                "Bal. ETB79.94. Receipt: https://transactioninfo.berhanonline.et/ereceipt/99900000000000001.pdf " +
                "Berhan Stress Free Banking!",
        )
        assertEquals(100.0, tx!!.amount, 0.001)
    }

    // ── Awash ──

    @Test fun `Awash debit written as a negative amount`() {
        val tx = parse(
            "Awash Bank",
            "Dear Customer, your Account 013208******00 has been Debited with ETB -2930.75 on 2025-07-19 13:19:59. " +
                "Your balance now is ETB 370.76. For any complaint or enquiry, please call 8980. Thank You. Awash Bank.",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(2930.75, tx.amount, 0.001)
        assertEquals(370.76, tx.balance!!, 0.001)
    }

    @Test fun `Awash You have sent with the PIN alert footer`() {
        val tx = parse(
            "Awash Bank",
            "Dear Customer, You have sent ETB 1,400 To (0134000000000) - ABEBE KEBEDE G/YOHANNIS by Transaction ID: " +
                "260100000000000 charge- 1.00 VAT- 0.15 Date 2026-01-12 22:18:07 . Your Available Balance is 257.77. " +
                "Download the receipt by link https://awashpay.awashbank.com:8225/-2KAAAAAAAA-3EAAAAA. You have earned " +
                "3 ProCoins and your total ProCoin Balance is 83.00.\n\nAlert: Awash Bank will never ask for your PIN, " +
                "password, or OTP. Do not share your confidential information with anyone.",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER_OUT.name, tx!!.type)
        assertEquals(1400.0, tx.amount, 0.001)
        assertEquals("ABEBE KEBEDE G/YOHANNIS", tx.counterparty)
        assertEquals(257.77, tx.balance!!, 0.001)
        assertEquals(1.0, tx.serviceCharge!!, 0.001)
        assertEquals(0.15, tx.vat!!, 0.001)
        assertEquals("260100000000000", tx.refNumber)
    }

    @Test fun `Awash Telebirr transfer`() {
        val tx = parse(
            "Awash Bank",
            "Dear Customer; Telebirr Transfer of 50.00 ETB to Abebe Kebede Alemu  - 251900000000 from 01320000000500/BANK,  " +
                "Reason- hi, Charge 5.00 VAT: 0.75 EDRRF 0.25 ETB. Your Balance is  ETB 668.09 . " +
                "Receipt Link: https://awashpay.awashbank.com:8225/-2KBBBBBBBB-5TBBBB. Contact Center 8980.",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER_OUT.name, tx!!.type)
        assertEquals(50.0, tx.amount, 0.001)
        assertEquals("Abebe Kebede Alemu", tx.counterparty)
        assertEquals(668.09, tx.balance!!, 0.001)
        assertEquals(5.0, tx.serviceCharge!!, 0.001)
        assertEquals(0.75, tx.vat!!, 0.001)
        assertEquals(0.25, tx.disasterFund!!, 0.001)
    }

    @Test fun `Awash received from another Awash customer`() {
        val tx = parse("Awash Bank", "Dear ABEBE KEBEDE ALEMU, you have received 50.00 from Awash Bank-HANA TESFAYE.Thank You! Awash Bank.")
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(50.0, tx.amount, 0.001)
        assertEquals("HANA TESFAYE", tx.counterparty)
    }

    @Test fun `Awash PIN and OTP messages are still ignored`() {
        assertNull(parse("Awash Bank", "Dear Customer, Your AwashBirr Pro activation code is 000000 and expires at 07/Jun/2025 21:13."))
        assertNull(parse("Awash Bank", "Your OTP is 123456. Do not share it with anyone. Awash Bank"))
    }

    // ── Dashen: amounts under 1 birr ──

    @Test fun `Dashen debit of less than one birr`() {
        val tx = parse(
            "DashenBank",
            "Dear Customer, your account '0145******011' is debited with ETB .90 on 07/07/2025. " +
                "Your current balance is ETB 1,460,767.98.\nDashen Bank - Always one step ahead!",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(0.90, tx.amount, 0.001)
        assertEquals("0145******011", tx.accountNumber)
    }

    // ── CBE: card withdrawals at an ATM ──

    @Test fun `CBE ATM withdrawal`() {
        val tx = parse(
            "CBE",
            "Dear Abebe Kebede Alemu, an ATM withdrawal of ETB1500.00 was made from your account 1********1234. " +
                "If you authorized this transaction, no further action is required. If not, please contact the bank " +
                "immediately. Service charge of ETB 5.25 and VAT(15%) of ETB0.79 and Disaster Recovery(5%) of 0.26 " +
                "with total of ETB1506.30 .Your current balance is ETB903.56. Thanks for Banking with CBE. " +
                "https://mbreciept.cbe.com.et/v2-aaaaaaaaaaaaaaaaaaaa  for feedback: https://forms.gle/aaaaaaaaaaaaaaaaa",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(1500.0, tx.amount, 0.001)
        assertEquals("CBE ATM", tx.counterparty)
        assertEquals("1********1234", tx.accountNumber)
        assertEquals(5.25, tx.serviceCharge!!, 0.001)
        assertEquals(0.79, tx.vat!!, 0.001)
        assertEquals(0.26, tx.disasterFund!!, 0.001)
        assertEquals(1506.30, tx.totalCharged!!, 0.001)
        assertEquals(903.56, tx.balance!!, 0.001)
    }

    // ── BoA 2023–24 "has been credited … for a transfer made by …" (154 in the Oct export) ──

    private fun boaMadeBy(by: String, desc: String) =
        "Dear Abebe, your account 9*****24 has been credited with ETB 350.00 on 27/04/2024 by BDR0AAAA0F " +
            "for a transfer made by $by through Branch Transfer with transfer description $desc. " +
            "The available balance in the account is ETB 510.49. Use our SWIFT, ABYSETAA. " +
            "For any assistance, call Toll-Free (24/7) on 8397. Bank of Abyssinia."

    @Test fun `BoA made-by credit reads account, balance and sender`() {
        val tx = parse("BOA", boaMadeBy("ABEBE KEBEDE ALEMU", "Transfer-TRANSFER"))
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(350.0, tx.amount, 0.001)
        assertEquals(510.49, tx.balance!!, 0.001)
        assertEquals("9*****24", tx.accountNumber)
        assertEquals("ABEBE KEBEDE ALEMU", tx.counterparty)
    }

    @Test fun `BoA made-by credit from Telebirr names Telebirr`() {
        val tx = parse("BOA", boaMadeBy("A/R TELE BIRR", "Telebirr to BoA Account-20240427165357"))
        assertEquals("Telebirr", tx!!.counterparty)
    }

    @Test fun `BoA made-by credit from another bank names EthSwitch`() {
        val tx = parse("BOA", boaMadeBy("EthSwich PAY amp; SET FOR BANK TO BANK", "Transfer-ETSWITCH Transfer"))
        assertEquals("Other bank (EthSwitch)", tx!!.counterparty)
    }

    @Test fun `BoA cash deposit names the depositor`() {
        val tx = parse("BOA", boaMadeBy("", "Cash Deposit-ABEBE KEBEDE ALEMU"))
        assertEquals("ABEBE KEBEDE ALEMU", tx!!.counterparty)
        assertEquals(350.0, tx.amount, 0.001)
    }

    // ── Telebirr: "Don't share the link" made a ticket payment look like an OTP ──

    @Test fun `Telebirr ticket payment is money out`() {
        val tx = parse(
            "127",
            "Dear Customer You have paid ETB 200.00 to CENTURY GENERAL TRADING PLC (500000) for Ticket Purchase   " +
                "on 05/01/2024 16:27:18 via Agent 200000. Your Ticket No. is BA00AAAAZD.You can get your ticket in this link " +
                "https://payment.ethiotelecom.et/qr-code/500000/BA00AAAAZD . Don't share the link to others. " +
                "Thank you for using telebirr Ethio telecom",
        )
        assertNotNull(tx)
        assertEquals(200.0, tx!!.amount, 0.001)
    }

    @Test fun `telebirr verification code is still ignored`() {
        assertNull(parse("127", "Dear Customer, 725000 is your telebirr verification code, please don't share it with another person. Thank you for using telebirr.  Ethio telecom."))
    }

    // ── Berhan Bank (Nov 2026 Berhan export, 173 SMS) ──

    private fun berhanCredit(note: String) =
        "Dear Abebe, A/C No. 103000XXXXX00 has been credited ETB 16,000 on 26-Jun-2026. " +
            "Reference is 17000000000000000001. Balance is ETB 31,882.18. $note . Berhan Stress Free Banking"

    @Test fun `Berhan RTGS credit names the sending bank`() {
        val tx = parse("Berhan Bank", berhanCredit("Rtrgs From [ Awinetaa ], Ref ["))
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(16000.0, tx.amount, 0.001)
        assertEquals(31882.18, tx.balance!!, 0.001)
        assertEquals("from Awash", tx.counterparty)
    }

    @Test fun `Berhan credit reads transfer-by, cheque, interest and purpose`() {
        assertEquals("OMEGA SAVING &CREDIT COOP SOC.WITH LIM.LIAB",
            parse("Berhan Bank", berhanCredit("transfer by OMEGA SAVING &CREDIT COOP SOC.WITH LIM.LIAB"))!!.counterparty)
        assertEquals("Cheque deposit", parse("Berhan Bank", berhanCredit("By Cheque No 7000000"))!!.counterparty)
        assertEquals("Interest", parse("Berhan Bank", berhanCredit("9122 - Deposit Account Active Credit"))!!.counterparty)
        assertEquals("Content Creator Payment", parse("Berhan Bank", berhanCredit("For Content Creator Payment"))!!.counterparty)
    }

    @Test fun `Berhan credit to the holder keeps the old fallback`() {
        val tx = parse("Berhan Bank", berhanCredit("To Abebe Kebede"))
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals("from Berhan Bank 103000XXXXX00", tx.counterparty)
    }

    @Test fun `Berhan transfer to Telebirr is money out`() {
        val tx = parse(
            "Berhan Bank",
            "Dear Abebe, You have transferred ETB 800 from your account 101XXXXXXX000 to TeleBirr number 251900000000. " +
                "Reference No. is: VMAAAAAAAZC. 08-Aug-2026 19:18:03 Through Android. Thank you for banking with us.",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.TRANSFER_OUT.name, tx!!.type)
        assertEquals(800.0, tx.amount, 0.001)
        assertEquals("Telebirr 251900000000", tx.counterparty)
        assertEquals("101XXXXXXX000", tx.accountNumber)
        assertEquals("VMAAAAAAAZC", tx.refNumber)
    }
}
