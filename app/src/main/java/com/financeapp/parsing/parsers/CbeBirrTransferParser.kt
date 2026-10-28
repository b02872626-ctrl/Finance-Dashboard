package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses CBEBirr P2P transfer-out SMS.
 *
 * Example:
 *   "Dear nahusenay, you have successfully transferred 5,100.00Br. to
 *    1000469454607-NAHUSENAY G/AMLAK TEKA on 21-05-2026 09:04:50.Txn ID
 *    DEL41DNZRPK,FT26141QJJK9.Your CBEBirr account balance is 107.56Br."
 *
 * Recipient text is "ACCOUNTNUM-NAME"; we capture just the NAME as the
 * counterparty so apply-to-similar can match across multiple transfers.
 */
class CbeBirrTransferParser : BankParser {

    private val amountRe       = Regex("""transferred\s+([\d,]+\.?\d*)\s*Br""", RegexOption.IGNORE_CASE)
    // "you have made 100.00Br. transfer to Bank of Abyssinia by Account Number
    //  71843475 - Yisgedu Sisay Zewde on …" / "… transfer to Bank of Abyssinia account on …"
    private val madeRe         = Regex("""made\s+([\d,]+\.?\d*)\s*Br\.?\s+transfer\s+to\s+(.+?)(?:\s+by\s+Account\s+Number\s+\d+\s*-\s*(.+?))?\s+(?:account\s+)?on\s+\d""", RegexOption.IGNORE_CASE)
    private val taxRe          = Regex("""Tax\s+([\d,]+\.?\d*)\s*Br""", RegexOption.IGNORE_CASE)
    private val chargeRe       = Regex("""Charge\s+([\d,]+\.?\d*)\s*Br""", RegexOption.IGNORE_CASE)
    // Recipient is "AccountNumber-Name" — anchor with " on " so we don't
    // accidentally pick up a multi-word account name with trailing tokens.
    private val recipientRe    = Regex("""to\s+\d+-([^.]*?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
    private val balanceRe      = Regex("""CBE\s*Birr\s+account\s+balance\s+is\s+([\d,]+\.?\d*)\s*Br""", RegexOption.IGNORE_CASE)
    // Two refs in one line: "Txn ID DEL41DNZRPK,FT26141QJJK9" — grab the first.
    private val refRe          = Regex("""Txn\s+ID\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBEBirr", ignoreCase = true) &&
        (body.contains("transferred", ignoreCase = true) || madeRe.containsMatchIn(body)) &&
        body.contains("Br.", ignoreCase = true) &&
        body.contains(" to ", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val made        = madeRe.find(body)
            val amount      = ParserUtils.parseAmount((made?.groupValues?.get(1)) ?: amountRe.find(body)?.groupValues?.get(1)) ?: return null
            val counterparty = if (made != null) {
                made.groupValues[3].trim().ifBlank { made.groupValues[2].trim() }
            } else recipientRe.find(body)?.groupValues?.get(1)?.trim()

            TransactionEntity(
                sender        = sms.sender,
                bankName      = "CBE Birr",
                type          = TransactionType.TRANSFER_OUT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = counterparty,
                accountNumber = null,
                refNumber     = refRe.find(body)?.groupValues?.get(1),
                dateTime      = sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(chargeRe.find(body)?.groupValues?.get(1)),
                vat           = ParserUtils.parseAmount(taxRe.find(body)?.groupValues?.get(1)),
                disasterFund  = null,
                totalCharged  = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
