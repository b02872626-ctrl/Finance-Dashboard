package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * CBE Fast Loan disbursement — money in, like a Telebirr Mela loan:
 *  "Congratulations, Dear X. Your loan of ETB 400.00 for 30 days has been
 *   disbursed successfully. Please repay ETB 460.00 by 03 Oct, 2026. CBE Fast Loan."
 */
class CbeFastLoanParser : BankParser {

    private val amountRe = Regex("""loan\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val termRe   = Regex("""for\s+(\d+\s+days)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBE", ignoreCase = true) &&
            body.contains("disbursed", ignoreCase = true) &&
            body.contains("loan of ETB", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            val term = termRe.find(body)?.groupValues?.get(1)
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Commercial Bank of Ethiopia",
                type          = TransactionType.CREDIT.name,
                amount        = amount,
                balance       = null,
                counterparty  = if (term != null) "CBE Fast Loan ($term)" else "CBE Fast Loan",
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
