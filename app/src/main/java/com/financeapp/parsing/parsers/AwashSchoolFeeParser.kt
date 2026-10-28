package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Awash Bank (AwashPay) school fees:
 *  "School fees payment of 2,394.00 ETB to SUPER HOLY SAVIOR SCHOOL for
 *   3300114480 - Haben Gebreamlak X on 2025-06-09 22:00:56. Ref 250609220048066."
 *  Newer: "… of 4,200.00 ETB charge- 0.00 VAT 0 EDRRF 0 ETB to …"
 * The "Awash" eSchool sender repeats the same payments ("You have paid
 * 2,925 BIRR School Fee …") and is ignored in SmsParserEngine.
 */
class AwashSchoolFeeParser : BankParser {

    private val amountRe = Regex("""School\s+fees\s+payment\s+of\s+([\d,]+\.?\d*)\s*ETB""", RegexOption.IGNORE_CASE)
    private val schoolRe = Regex("""ETB\s+to\s+(.+?)\s+for\s+""", RegexOption.IGNORE_CASE)
    private val chargeRe = Regex("""charge-\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val dateRe   = Regex("""on\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})""")
    private val refRe    = Regex("""Ref\s+(\d+)""")

    override fun canParse(sender: String, body: String): Boolean =
        sender.contains("Awash", ignoreCase = true) && amountRe.containsMatchIn(body)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            TransactionEntity(
                sender = sms.sender, bankName = "Awash Bank", type = TransactionType.PAYMENT.name,
                amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null,
                balance = null,
                counterparty = schoolRe.find(body)?.groupValues?.get(1)?.trim(),
                accountNumber = null,
                refNumber = refRe.find(body)?.groupValues?.get(1),
                dateTime = ParserUtils.parseIsoDateTime(dateRe.find(body)?.groupValues?.get(1)) ?: sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(chargeRe.find(body)?.groupValues?.get(1)),
                vat = null, disasterFund = null, totalCharged = null,
                currency = "ETB", rawBody = body,
            )
        } catch (e: Exception) { null }
    }
}
