package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Telebirr Mela short-term loan repayment SMS.
 * Two variants:
 *  - "your outstanding Credit amount has been paid successfully. The paid amount is ETB 538.90,
 *     your monthly outstanding amount is ETB 0.00 and your total outstanding amount is ETB 0.00."
 *  - "You have repaid 3,659.67 ETB, for your Mela 50 days contract.
 *     Your current unpaid credit amount is 0.00 ETB."
 */
class TelebirrMelaRepaymentParser : BankParser {

    private val paidAmountRe  = Regex("""paid\s+amount\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val repaidAmountRe = Regex("""repaid\s+([\d,]+\.?\d*)\s+ETB""", RegexOption.IGNORE_CASE)
    private val unpaidRe      = Regex("""unpaid\s+credit\s+amount\s+is\s+([\d,]+\.?\d*)\s+ETB""", RegexOption.IGNORE_CASE)
    private val outstandingRe = Regex("""total\s+outstanding\s+amount\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val contractRe    = Regex("""Mela\s+(\d+\s+days\s+contract)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" && (
            body.contains("outstanding Credit amount has been paid successfully", ignoreCase = true) ||
            (body.contains("repaid", ignoreCase = true) && body.contains("Mela", ignoreCase = true))
        )

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(paidAmountRe.find(body)?.groupValues?.get(1))
                ?: ParserUtils.parseAmount(repaidAmountRe.find(body)?.groupValues?.get(1))
                ?: return null
            val contract = contractRe.find(body)?.groupValues?.get(1)?.trim()
            val cp = if (contract != null) "Mela loan repayment ($contract)" else "Mela loan repayment"
            val balance = ParserUtils.parseAmount(outstandingRe.find(body)?.groupValues?.get(1))
                ?: ParserUtils.parseAmount(unpaidRe.find(body)?.groupValues?.get(1))
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Telebirr",
                type          = TransactionType.DEBIT.name,
                amount        = amount,
                balance       = balance,
                counterparty  = cp,
                accountNumber = null,
                refNumber     = null,
                dateTime      = sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
