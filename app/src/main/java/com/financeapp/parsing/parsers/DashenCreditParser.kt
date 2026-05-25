package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Dashen Bank Credit SMS.
 * Example: "Dear, YOHANNES Your Account '5090**011' has been credited with ETB 683.00
 *           from other bank on 04/04/2026 at 02:27:51 PM. your current balance is ETB 779.35.
 *           Dashen Bank - Always one step ahead!"
 */
class DashenCreditParser : BankParser {

    private val accountRe  = Regex("""Account\s+'([^']+)'\s+has\s+been\s+credited""", RegexOption.IGNORE_CASE)
    private val amountRe   = Regex("""credited\s+with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val fromRe     = Regex("""from\s+(.+?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""current\s+balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("DashenBank", ignoreCase = true) &&
        body.contains("credited with ETB", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null

            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Dashen Bank",
                type          = TransactionType.CREDIT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = fromRe.find(body)?.groupValues?.get(1)?.trim(),
                accountNumber = accountRe.find(body)?.groupValues?.get(1)?.trim(),
                refNumber     = null,
                dateTime      = sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
