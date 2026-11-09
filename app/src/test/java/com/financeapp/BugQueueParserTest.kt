package com.financeapp

import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.SmsParserEngine
import com.financeapp.parsing.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The admin "Unparsed SMS" bug queue (Oct 2026). Bodies are verbatim from
 * the queue. Real transactions must parse; notices must be ignored (null).
 */
class BugQueueParserTest {

    private fun parse(sender: String, body: String) =
        SmsParserEngine.parse(SmsMessage(sender = sender, body = body, timestamp = 1_757_000_000_000L))

    // ── Real money: must become transactions ────────────────────────────

    @Test fun `CBE Fast Loan disbursement is money in`() {
        val tx = parse(
            "CBE",
            "Congratulations, Dear Yeabsira Endegena Andarge. Your loan of ETB 400.00 for 30 days has been " +
                "disbursed successfully. Please repay ETB 460.00 by 03 Oct, 2026. CBE Fast Loan.",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(400.0, tx.amount, 0.001)
        assertEquals("CBE Fast Loan (30 days)", tx.counterparty)
    }

    @Test fun `Telebirr Siinqee repayment is money out`() {
        val tx = parse(
            "127",
            "Dear Yeabsira You have repaid 0.34 ETB, for your Siinqee_30_Days contract. Your current unpaid " +
                "credit amount is 1,230.95 ETB. Thank you for using telebirr Ethio telecom",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(0.34, tx.amount, 0.001)
        assertEquals(1230.95, tx.balance!!, 0.001)
        assertEquals("Siinqee 30 Days loan repayment", tx.counterparty)
    }

    @Test fun `Telebirr reference is never the word is`() {
        val tx = parse(
            "127",
            "Dear YEABSIRA  You have received ETB 995.00 from Natnael Fanna(2519****8812)  on 21/07/2025 13:58:23. " +
                "Your transaction number is CGL1DEF2GH. Your current balance is ETB 1,000.00.",
        )
        assertNotNull(tx)
        assertEquals("CGL1DEF2GH", tx!!.refNumber)
    }

    // ── Notices: no money moved, must be ignored ────────────────────────

    @Test fun `notices are ignored`() {
        val notices = listOf(
            "127" to "Dear Yeabsira, You have successfully activated the Financial Marketplace service. Thank you for using telebirr Ethio telecom",
            "127" to "Dear Yeabsira The balance in account 1000000016027413O4(ETB) of account type Customer E-Money Account of identity 251973797336 - Yeabsira Endegena Andarge is insufficient.",
            "127" to "Sorry, You have insufficient balance for the requested transaction. Your account balance is ETB 0.00. Please deposit additional amount and try again. Thank you for using telebirr",
            "127" to "Dear Yeabsira, Your outstanding credit balance of ETB 1235.11 is 5 days overdue. Please pay promptly to keep your credit limit & save yourself from unnecessary legal charge, ha",
            "127" to "Dear Yeabsira, Your outstanding credit balance of ETB 1230.95 is 1 day overdue.Please pay promptly to save yourself from additional charges & be able to use your credit limit.",
            "CBE" to "EKSIPOO FI FEESTIVAALA CBE ADEY 2019 ************ Giddugala Konveenshinii Addis Internaashinaaliitti Hagayya 23, 2018 irraa eegalee hanga Qaammee 5tti Eksipoo fi Feestivaala",
            "CBE" to "Dear Yeabsira Endegena Andarge, Click https://apps.cbe.com.et:100/dfs/tl/MTc1MjA2OTE3MV9UTF8yMDI2MDkwMzE2NDc0ODYwNQ== for T&C to view the full Terms and Conditions",
            "CBE" to "ሲቢኢ አደይ ኤክስፖ እና ፌስቲቫል 2019 ****** በአዲስ ኢንተርናሽናል ኮንቬንሽን ማዕከል ከነሐሴ 23 እስከ ጳጉሜ 5 ቀን 2018 ዓ.ም",
            "BOA" to "የ5 ዶላር ዋጋ ያለውን የአፖሎ ቨርቹዋል ካርድ ለአጭር ጊዜ በነፃ ያግኙ! ውድ YEABSIRA ፣ የአፖሎ መተግበሪያን ያውርዱ፣",
        )
        notices.forEach { (sender, body) -> assertNull(body.take(60), parse(sender, body)) }
    }

    // ── Admin Parser tab "Fix next" (Nov 2026): already parsed by today's
    // rules; listed only because old installs sent stale statuses. Masked.

    @Test fun `Dashen check payment received is money in`() {
        val tx = parse(
            "DashenBank",
            "Dear Customer, KULU NETWORK PLC has transferred ETB 165,565.22 as check payment to your account " +
                "'0145******011' on 09/10/2026 at 03:39:35 PM. Your current balance is ETB 171,754.53.\n" +
                "Dashen Bank - Always one step ahead!.\n Feedback: https://forms.gle/XXXXXXXXXXXXXXXXX",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.CREDIT.name, tx!!.type)
        assertEquals(165565.22, tx.amount, 0.001)
        assertEquals(171754.53, tx.balance!!, 0.001)
        assertEquals("KULU NETWORK PLC", tx.counterparty)
    }

    @Test fun `Dashen check payment debited is money out`() {
        val tx = parse(
            "DashenBank",
            "Dear Customer, ABEBE KEBEDE TESFAYE has debited ETB 24,000.00 as check payment from your account " +
                "'0145******011' on 13/02/2024. Your current balance is ETB 263,890.00.\n" +
                "Dashen Bank - Always one step ahead!",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(24000.0, tx.amount, 0.001)
        assertEquals("ABEBE KEBEDE TESFAYE", tx.counterparty)
    }

    @Test fun `Awash school fees payment is a payment`() {
        val tx = parse(
            "Awash Bank",
            "Dear Customer,  School fees payment of 2,200.00 ETB to SUNRISE SCHOOL for 3300000001 - Student Name " +
                "on 2025-06-21 14:15:58. Ref 250621000000000.  Receipt  Link: https://awashpay.awashbank.com:8225/-XXXXXXXXXX-XXXXXX.\n" +
                "Earned ProCoins: 20. ProCoin Balance: 45.00. Help us do better – Please take our mini survey: " +
                "https://shorturl.at/WISQB  Thank You. Awash Bank!",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.PAYMENT.name, tx!!.type)
        assertEquals(2200.0, tx.amount, 0.001)
        assertEquals("SUNRISE SCHOOL", tx.counterparty)
    }

    @Test fun `Awash school fees payment with charges is a payment`() {
        val tx = parse(
            "Awash Bank",
            "Dear Customer,  School fees payment of 4,200.00 ETB charge- 0.00 VAT 0 EDRRF 0 ETB to SUNRISE SCHOOL " +
                "for 3300000002 - Student Name on 2026-10-09 20:44:46. Ref 261009000000000.  Receipt  Link: " +
                "https://awashpay.awashbank.com:8225/-XXXXXXXXXX-XXXXXX.\nHelp us do better – Please take our mini " +
                "survey: https://shorturl.at/WISQB  Thank You. Awash Bank!",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.PAYMENT.name, tx!!.type)
        assertEquals(4200.0, tx.amount, 0.001)
        assertEquals("SUNRISE SCHOOL", tx.counterparty)
    }

    @Test fun `CBE debited for merchant is money out`() {
        val tx = parse(
            "CBE",
            "Dear Abebe your Account 1********0000 has been debited for CAMPUS CONSUMERS COOPERATIVES PLC  with " +
                "ETB 210. Your Current Balance is ETB 12238.53. Thank you for Banking with CBE!. For feedback " +
                "https://shorturl.at/auUX0 https://apps.cbe.com.et:100/BranchReceipt/FT00000XXXXX&00000000",
        )
        assertNotNull(tx)
        assertEquals(TransactionType.DEBIT.name, tx!!.type)
        assertEquals(210.0, tx.amount, 0.001)
        assertEquals(12238.53, tx.balance!!, 0.001)
        assertEquals("CAMPUS CONSUMERS COOPERATIVES PLC", tx.counterparty)
    }
}
