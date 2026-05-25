package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses CBE credit (money received) SMS.
 * Supports both:
 * - "Account ... has been Credited with ETB ..."
 * - "You have received ETB ... from account ... to your account ..."
 */
class CbeCreditParser : BankParser {

    private val accountRegexes = listOf(
        Regex("""Account\s+(\S+)\s+has\s+been\s+(?:Credited|credited)""", RegexOption.IGNORE_CASE),
        Regex("""to\s+your\s+account\s+(\S+)""", RegexOption.IGNORE_CASE)
    )

    private val amountRegexes = listOf(
        Regex("""Credited\s+with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
        Regex("""credited\s+by\s+.+?\s+with\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
        Regex("""received\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    )

    private val counterpartyRegexes = listOf(
        Regex("""from\s+(.+?)\s*,?\s+on\s+\d{2}/\d{2}/\d{4}""", RegexOption.IGNORE_CASE),
        Regex("""from\s+account\s+\S+\s+\(([^)]+)\)""", RegexOption.IGNORE_CASE),
        // "credited by DAWIT WONDWOSSEN/MDM DEVELOPERS with ETB ..."
        Regex("""credited\s+by\s+(.+?)\s+with\s+ETB""", RegexOption.IGNORE_CASE)
    )

    private val dateRe = Regex("""on\s+(\d{2}/\d{2}/\d{4})\s+at\s+(\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)

    private val refRegexes = listOf(
        Regex("""Ref\s+No\s+(\w+)""", RegexOption.IGNORE_CASE),
        Regex("""[?&]id=([A-Z0-9]+)""", RegexOption.IGNORE_CASE),
        Regex("""https?://[^\s?]+/([A-Z0-9-]+)""", RegexOption.IGNORE_CASE)
    )

    private val balanceRe = Regex("""(?:Current\s+)?Balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBE", ignoreCase = true) &&
            (
                body.contains("Credited with ETB", ignoreCase = true) ||
                    body.contains("received ETB", ignoreCase = true) ||
                    body.contains("credited by", ignoreCase = true)
                )

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = amountRegexes.firstNotNullOfOrNull { regex ->
                ParserUtils.parseAmount(regex.find(body)?.groupValues?.getOrNull(1))
            } ?: return null

            val dateMatch = dateRe.find(body)

            TransactionEntity(
                sender = sms.sender,
                bankName = "Commercial Bank of Ethiopia",
                type = TransactionType.CREDIT.name,
                amount = amount,
                balance = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.getOrNull(1)),
                counterparty = firstGroupMatch(body, counterpartyRegexes)?.substringBefore(",")?.trim(),
                accountNumber = firstGroupMatch(body, accountRegexes)?.trimEnd('.', ',', ';', ':'),
                refNumber = firstGroupMatch(body, refRegexes)?.trimEnd('.', ',', ';', ':'),
                dateTime = ParserUtils.parseDateTime(
                    dateMatch?.groupValues?.getOrNull(1),
                    dateMatch?.groupValues?.getOrNull(2)
                ) ?: sms.timestamp,
                serviceCharge = null,
                vat = null,
                disasterFund = null,
                totalCharged = null,
                currency = "ETB",
                rawBody = body
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun firstGroupMatch(body: String, regexes: List<Regex>): String? =
        regexes.firstNotNullOfOrNull { regex -> regex.find(body)?.groupValues?.getOrNull(1) }
}
