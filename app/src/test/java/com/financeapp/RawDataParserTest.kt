package com.financeapp

import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.SmsParserEngine
import com.financeapp.parsing.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Wordings found by replaying the i_raw_data export (Oct 2026) through the
 * parser. Bodies keep the banks' exact wording; people's names and numbers
 * are replaced.
 */
class RawDataParserTest {

    private fun parse(sender: String, body: String) =
        SmsParserEngine.parse(SmsMessage(sender = sender, body = body, timestamp = 1_757_000_000_000L))

    private fun assertTx(sender: String, body: String, type: TransactionType, amount: Double, counterparty: String? = null) {
        val tx = parse(sender, body)
        assertNotNull("not parsed: $body", tx)
        assertEquals(body, type.name, tx!!.type)
        assertEquals(body, amount, tx.amount, 0.001)
        if (counterparty != null) assertEquals(body, counterparty, tx.counterparty)
    }

    // ── Dashen ──────────────────────────────────────────────────────────

    @Test fun `Dashen cash withdrawal`() = assertTx(
        "DashenBank",
        "Dear Customer, you have withdrawn ETB 2,000.00 from your account '5***1234' on 2026-03-01. Your account balance is ETB 1,250.00.",
        TransactionType.DEBIT, 2000.0, "Cash withdrawal",
    )

    @Test fun `Dashen transfer in`() = assertTx(
        "DashenBank",
        "Dear Customer, ABEBE KEBEDE has transferred ETB 1,500.00 to your account '5***1234'. Your account balance is ETB 2,750.00.",
        TransactionType.CREDIT, 1500.0, "ABEBE KEBEDE",
    )

    // ── NIB ─────────────────────────────────────────────────────────────

    @Test fun `NIB credit`() = assertTx(
        "NIB",
        "Dear Customer, Your Account 70001234 has been Credited with ETB 3,000.00. Ref: FT26123ABCD. Current Balance: ETB 3,450.00",
        TransactionType.CREDIT, 3000.0,
    )

    // ── Bank of Abyssinia ───────────────────────────────────────────────

    @Test fun `BOA POS hold is a card payment`() = assertTx(
        "BOA",
        "Dear Customer, ETB 450.00 is Locked From Your Account Number 1*****38 Due to POS TRANSACTION at SHOA SUPERMARKET. Your Available Balance is ETB 1,200.00",
        TransactionType.PAYMENT, 450.0, "SHOA SUPERMARKET",
    )

    @Test fun `BOA old credit wording with sender name`() = assertTx(
        "BOA",
        "Dear Customer, A/C No. 1*****38 has been credited By ABEBE KEBEDE ALEMUwith ETB 1,000.00 on 03/12/2022 @PRODUCTION-HEAD OFFICE. Info: Internet Credit-ABEBE KEBEDE ALEMU. Avail. bal: ETB 1,050.00. Join our telegram channel for latest updates https://t.me/BoAEth",
        TransactionType.CREDIT, 1000.0, "ABEBE KEBEDE ALEMU",
    )

    @Test fun `BOA old cash deposit names the deposit`() = assertTx(
        "BOA",
        "Dear Customer, A/C No. 1*****38 has been credited By with ETB 50.00 on 26/09/2022 @BOLE ARABSA BRANCH. Info: Cash Deposit-ABEBE KEBEDE. Avail. bal: ETB 50.00. Join our telegram channel for latest updates https://t.me/BoAEth",
        TransactionType.CREDIT, 50.0, "Cash Deposit",
    )

    // ── Awash ───────────────────────────────────────────────────────────

    @Test fun `Awash transfer to CBE`() = assertTx(
        "Awash Bank",
        "Dear Customer, You have transferred to COMMERCIAL BANK OF ETHIOPIA Amount 400 ETB To 1000123456789 (ABEBE KEBEDE). VAT: 0.36  Your Balance is  ETB 120.85. Receipt Link: https://awashpay.awashbank.com:8225/-E3F132FD0B73-1S3P51.",
        TransactionType.TRANSFER_OUT, 400.0, "ABEBE KEBEDE",
    )

    @Test fun `Awash transfer to other bank`() = assertTx(
        "Awash Bank",
        "Dear Customer , You have transferred to other bank ETB  300  To 74701531 (ABEBE KEBEDE) In Bank of Abyssinia with charge of 1.80 VAT: 0.27  EDRRF 0.09 ETB. Your available Balance is  ETB 365.93.",
        TransactionType.TRANSFER_OUT, 300.0, "ABEBE KEBEDE",
    )

    @Test fun `Awash IPS credit names the sender`() = assertTx(
        "Awash Bank",
        "Dear Customer, Your Account 01320******500 has been credited with 1,000.00 ETB on 2025-11-13 08:49:42 PM by ABEBE KEBEDE via IPS from Commercial Bank of Ethiopia For any complaint or enquiry, please call 8980. Thank You. Awash Bank",
        TransactionType.CREDIT, 1000.0, "ABEBE KEBEDE",
    )

    @Test fun `Awash IPS credit without a name uses the bank`() = assertTx(
        "Awash Bank",
        "Dear Customer, Your Account 01320******500 has been credited with 9,500.00 ETB on 2025-10-09 07:07:17 PM by IPS from Commercial Bank of Ethiopia For any complaint or enquiry, please call 8980. Thank You. Awash Bank",
        TransactionType.CREDIT, 9500.0, "Commercial Bank of Ethiopia",
    )

    @Test fun `Awash note about money landing in another bank is ignored`() = assertNull(parse(
        "Awash Bank",
        "Dear ABEBE KEBEDE, ALMAZ BEKELE  has transferred 5000.00 birr to your ABYSSINIA account from Awash Bank. Thank you.",
    ))

    // ── CBE ─────────────────────────────────────────────────────────────

    @Test fun `CBE short app transfer`() = assertTx(
        "CBE", "Dear Abebe, You have transfered ETB 200.00 to Almaz",
        TransactionType.TRANSFER_OUT, 200.0, "Almaz",
    )

    @Test fun `CBE Fast Loan repayment`() = assertTx(
        "CBE", "Dear Abebe Kebede, ETB 420.00 is paid on 07 Sep, 2026 for Loan Id 26249. CBE Fast Loan.",
        TransactionType.DEBIT, 420.0, "CBE Fast Loan repayment",
    )

    @Test fun `CBE Fast Loan auto-collection`() = assertTx(
        "CBE", "Dear Abebe Kebede, ETB 478.63 is debited via auto-collection on 27 Oct, 2026 for Loan Id 280373. CBE Fast Loan.",
        TransactionType.DEBIT, 478.63, "CBE Fast Loan repayment",
    )

    @Test fun `CBE pending payment request is ignored`() = assertNull(parse(
        "CBE", "Dear Abebe Kebede, your payment request of ETB 115.0 is pending. Please await approval from the payer. Thank you for using our service.",
    ))

    // ── CBE Birr ────────────────────────────────────────────────────────

    @Test fun `CBE Birr old sent wording`() = assertTx(
        "CBEBirr",
        "Dear ABEBE, you have sent 200.00Br. to ALMAZ BEKELE on 26/06/25 14:26,Txn ID CFQ9PVCXT9. Your CBE Birr account balance is 1.72Br.Thank you!",
        TransactionType.TRANSFER_OUT, 200.0, "ALMAZ BEKELE",
    )

    @Test fun `CBE Birr old received wording`() = assertTx(
        "CBEBirr",
        "Dear ABEBE, you received 600.00Br. from 251900000000 - almaz bekele on 10/07/25 19:21,Txn ID CGA8QHQB8K.Your CBE Birr account balance is 621.87Br. Thank you!",
        TransactionType.CREDIT, 600.0, "almaz bekele",
    )

    @Test fun `CBE Birr merchant payment`() = assertTx(
        "CBEBirr",
        "Dear ABEBE, you paid 692.04Br. to 232320 - Chapafinancialtechnology s.c on 02/08/25 11:55,Txn ID CH29RIVKBR.Your CBE Birr account balance is 44.20Br. Thank you!",
        TransactionType.PAYMENT, 692.04, "Chapafinancialtechnology s.c",
    )

    @Test fun `CBE Birr reversal is money back`() = assertTx(
        "CBEBirr",
        "Dear User, reversal of transaction CL84105E978 has been successfully completed on 08/12/25  at 8:51 AM and 200.00Br. is credited to your account. Your CBE Birr  balance is 207.81Br..Thank you!",
        TransactionType.CREDIT, 200.0, "Reversal",
    )

    @Test fun `CBE Birr made transfer to a bank`() {
        val tx = parse(
            "CBEBirr",
            "Dear ABEBE, You have made 1,000.00 Br. transfer to ALMAZ BEKELE by Account Number 1000123456789 - Commercial Bank of Ethiopia on 12/05/26 10:00. Charge 5.00Br. Tax 0.75Br.",
        )
        assertNotNull(tx)
        assertEquals(1000.0, tx!!.amount, 0.001)
    }

    // ── Telebirr ────────────────────────────────────────────────────────

    @Test fun `Telebirr agent cash out`() = assertTx(
        "127",
        "Dear Abebe Your telebirr account 251900000000 has been debited with ETB 600.00 on 09/07/2025 09:05:42 at telebirr Agent 271404. Your transaction number is CG94EQ7R96. The service fee is  ETB 8.00 and  15% VAT on the service fee is ETB 1.20. Your current Account balance is ETB 68.33.",
        TransactionType.DEBIT, 600.0, "Cash withdrawal (agent 271404)",
    )

    @Test fun `Telebirr ATM withdrawal reversed`() = assertTx(
        "127",
        "Dear Customer Your transaction for ATM withdraw request with telebirr receipt number DCQ59JHV1X and with amount ETB 200.00 is reversed to your account on  2026-03-26 20:19:11. Your current balance is ETB 1,912.19. Your transaction number is DCQ59JHY0L is successfully completed.",
        TransactionType.CREDIT, 200.0, "ATM withdrawal reversed",
    )

    @Test fun `Telebirr failed package reversal is ignored`() = assertNull(parse(
        "127",
        "Dear Customer Your transaction for Package Purchase request with telebirr receipt number BGR2ULWIZ6 is reversed to your account on  2024-07-27 11:36:29. Your current balance is ETB 119.20. Your transaction number BGR1ULWNDR is successfully completed.",
    ))

    @Test fun `Telebirr bank transfer in Amharic`() = assertTx(
        "127",
        "ውድ Abebe በግብይት ቁጥር  CJC4F8XFS0 በ2025-10-12 09:02:18,  2,000.00 ብር ከ Commercial Bank of Ethiopia ወደ ቴሌብር አካውንትዎ 251900000000 - Abebe Kebede ተላልፏል፡፡ አሁን ያሎት ቀሪ ሂሳብ  2,076.42 ብር ነው፡፡",
        TransactionType.CREDIT, 2000.0, "Commercial Bank of Ethiopia",
    )

    @Test fun `Telebirr Mela partial repayment`() = assertTx(
        "127",
        "Dear Abebe,    Your outstanding credit amount has been partially paid successfully.  The paid amount is ETB 3,000.00, your monthly outstanding amount is ETB 5,912.16 and your total outstanding amount was ETB 8,912.16 Thank you for using telebirr  Ethio telecom in partnership with CBE",
        TransactionType.DEBIT, 3000.0, "Telebirr Mela repayment",
    )

    @Test fun `Telebirr savings deposit is not spending`() = assertTx(
        "127",
        "Dear Abebe You have successfully deposited ETB 300.00 to your Saving Account on 18/09/2024 19:26:59. Your telebirr transaction number is BII6M6VBTW. Your current Saving balance is ETB 300.00 and Your current telebirr Account balance is ETB 448.24.",
        TransactionType.INTERNAL_TRANSFER, 300.0, "To telebirr savings",
    )

    @Test fun `Telebirr savings withdrawal is not income`() = assertTx(
        "127",
        "Dear Abebe You have successfully Withdraw ETB 50.00 from your saving account on 16/03/2024 16:05:10. Your transaction number is BCG9ESNJGT. Your e-money account balance is ETB 76.38.",
        TransactionType.INTERNAL_TRANSFER, 50.0, "From telebirr savings",
    )

    @Test fun `Telebirr Ethiotel Credit repayment is not counted again`() = assertTx(
        "127",
        "Dear Abebe You have successfully paid a credit amount of ETB 59.54 on 2026-05-21 08:58:52. Your current outstanding credit amount ETB 0.00",
        TransactionType.INTERNAL_TRANSFER, 59.54, "Ethiotel Credit repayment",
    )

    @Test fun `Telebirr parking payment`() = assertTx(
        "127",
        "Dear Abebe You have paid  for Parking Payment  ETB 40.00 to 5471 - Addis Ababa Traffic Management Authority Parking Service; with Payment reference number PT672355 on 24/05/2026 21:54:06. The service fee is  ETB 0.20 and  15% VAT on the service fee is ETB 0.03. Your transaction number is DEO0ABCDEF",
        TransactionType.PAYMENT, 40.0,
    )

    @Test fun `Telebirr traffic penalty with double spaces`() = assertTx(
        "127",
        "Dear Abebe\nYou have paid  for traffic\u00A0penalty  ETB 500.00 to 515016 - Addis Ababa Traffic Management Authority; with Payment reference number VT301912 on 18/03/2026 17:01:14. The service fee is  ETB 2.50 and  15% VAT on the service fee is ETB 0.37. Your transaction number is DCI4WLTE3U",
        TransactionType.PAYMENT, 500.0,
    )
}
