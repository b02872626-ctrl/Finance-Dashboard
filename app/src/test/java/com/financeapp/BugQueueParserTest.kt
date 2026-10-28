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
}
