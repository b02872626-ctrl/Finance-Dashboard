package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Berhan Bank outgoing (debit) SMS.
 *
 * Real-world sample (from corpus):
 *   "Dear NATNAEL GETINET DAGNE; A/C No. *********9067 has been debited
 *    ETB100.00 on 21-03-2026 Ref. APPMN01BXYT. Bal. ETB79.94.
 *    Receipt: https://transactioninfo.berhanonline.et/ereceipt/99921032026298550.pdf
 *    Berhan Stress Free Banking!"
 *
 * Notable Berhan idioms (vs CBE / BoA / Awash):
 *  - Customer name in CAPS, terminated by semicolon: `Dear NAME;`
 *  - Account masked with leading asterisks: `*********9067`
 *  - Amount has NO space after ETB: `ETB100.00`
 *  - Date format `dd-MM-yyyy` (hyphen-separated, no time component)
 *  - Reference label is `Ref.` (period after, not colon)
 *  - Balance label is `Bal.` (short form)
 *  - Always ends with the tagline "Berhan Stress Free Banking!"
 *  - Receipt URL on `transactioninfo.berhanonline.et` — captured via the
 *    engine's URL-extraction fallback so it appears in TransactionEntity.receiptLink.
 *
 * The body does NOT name the counterparty for debits (Berhan doesn't
 * distinguish ATM withdrawal vs P2P vs bill-pay in this format) — so
 * `counterparty` is left null and the engine's withMerchantKey() fallback
 * fills in "to Berhan Bank ********9067" for display.
 */
class BerhanBankDebitParser : BankParser {

    private val amountRe   = Regex("""debited\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val accountRe  = Regex("""A/C\s+No\.\s*(\S+)""", RegexOption.IGNORE_CASE)
    private val dateRe     = Regex("""on\s+(\d{2})-(\d{2})-(\d{4})""")
    private val refRe      = Regex("""Ref\.\s*(\S+?)\.""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""Bal\.\s*ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.contains("Berhan", ignoreCase = true) &&
        body.contains("has been debited", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null

            // Berhan uses dd-MM-yyyy with no time-of-day. Use SMS arrival time
            // for the time component so analytics still has a sensible HH:mm.
            val ts = dateRe.find(body)?.let { m ->
                val day = m.groupValues[1]; val mon = m.groupValues[2]; val yr = m.groupValues[3]
                // Reuse the existing parser util which handles dd/MM/yyyy by
                // swapping the separator.
                ParserUtils.parseDateTime("$day/$mon/$yr", null)
            } ?: sms.timestamp

            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Berhan Bank",
                type          = TransactionType.DEBIT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = null,  // Berhan debit SMS don't name the recipient
                accountNumber = accountRe.find(body)?.groupValues?.get(1),
                refNumber     = refRe.find(body)?.groupValues?.get(1)?.trim(),
                dateTime      = ts,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
