package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Telebirr Mela short-term loan disbursement SMS.
 * Example: "Dear Nahusenay,
 *           Your credit request with CKQ2FYZCJC contract number is successful.
 *           The credit amount is ETB 500.00 and facilitation fee ETB 32.50
 *           with due date 25/01/2026 and the daily fee will be from 0.50% to 0,80%
 *           depending on your credit limit. Your current available credit limit ETB1,700.00."
 */
class TelebirrMelaCreditParser : BankParser {

    private val amountRe   = Regex("""credit\s+amount\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val contractRe = Regex("""credit\s+request\s+with\s+(?:the\s+)?(\S+)\s+contract\s+number""", RegexOption.IGNORE_CASE)
    private val feeRe      = Regex("""facilitation\s+fee\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val dueDateRe  = Regex("""due\s+date\s+(\d{2}/\d{2}/\d{4})""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" &&
        body.contains("credit request", ignoreCase = true) &&
        body.contains("contract number is successful", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            val contract = contractRe.find(body)?.groupValues?.get(1)?.trim()
            val due = dueDateRe.find(body)?.groupValues?.get(1)
            val cp = buildString {
                append("Mela loan disbursement")
                if (due != null) append(" (due $due)")
            }
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Telebirr",
                type          = TransactionType.CREDIT.name,
                amount        = amount,
                balance       = null,
                counterparty  = cp,
                accountNumber = null,
                refNumber     = contract,
                dateTime      = sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(feeRe.find(body)?.groupValues?.get(1)),
                vat           = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
