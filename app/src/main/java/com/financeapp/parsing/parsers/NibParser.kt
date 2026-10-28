package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Nib International Bank:
 *  "Dear Customer your Account ****5637 has been Credited with ETB  50.00
 *   Ref: TT24002GB45Z. Your Current Balance is ETB 50.00. Thank you for
 *   Banking with NIB!"   (the Debited wording is handled the same way)
 */
class NibParser : BankParser {

    private val moveRe    = Regex("""Account\s+(\S+)\s+has\s+been\s+(Credited|Debited)\s+with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val refRe     = Regex("""Ref:\s*([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""Current\s+Balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        (sender.equals("NIB", ignoreCase = true) || sender.equals("NIBBank", ignoreCase = true)) && moveRe.containsMatchIn(body)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val m = moveRe.find(body) ?: return null
            TransactionEntity(
                sender = sms.sender, bankName = "Nib International Bank",
                type = if (m.groupValues[2].equals("Credited", true)) TransactionType.CREDIT.name else TransactionType.DEBIT.name,
                amount = ParserUtils.parseAmount(m.groupValues[3]) ?: return null,
                balance = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty = null, accountNumber = m.groupValues[1],
                refNumber = refRe.find(body)?.groupValues?.get(1),
                dateTime = sms.timestamp, serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency = "ETB", rawBody = body,
            )
        } catch (e: Exception) { null }
    }
}
