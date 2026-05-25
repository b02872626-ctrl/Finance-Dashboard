package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses CBE outgoing transfer SMS.
 * Example: "Dear Yohannes, You have transfered ETB 600.00 to Fetlework Ansa on 03/04/2026
 *           at 22:46:55 from your account 1*****8166. Your account has been debited with
 *           a S.charge of ETB 0.50 and VAT(15%) of ETB0.08 and Disaster Fund (5%) of ETB0.03,
 *           with a total of ETB 600.61. Your Current Balance is ETB 78,471.86."
 * Note: CBE SMS uses single-r "transfered" (intentional typo in their system).
 */
class CbeTransferParser : BankParser {

    // Matches both "transfered" (CBE typo) and "transferred"
    private val amountRe   = Regex("""transfer(?:r)?ed\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val toRe       = Regex("""transfer(?:r)?ed\s+ETB\s*[\d,.]+\s+to\s+(.+?)\s+on\s+\d{2}/\d{2}/\d{4}""", RegexOption.IGNORE_CASE)
    private val dateRe     = Regex("""on\s+(\d{2}/\d{2}/\d{4})\s+at\s+(\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    private val accountRe  = Regex("""from\s+your\s+account\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val scRe       = Regex("""S\.charge\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    // Handles both "VAT(15%) of ETB0.08" and "15% VAT of ETB0.30"
    private val vatRe      = Regex("""(?:VAT\(15%\)\s+of\s+ETB|15%\s+VAT\s+of\s+ETB)\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val dfRe       = Regex("""Disaster\s+Fund\s+\(5%\)\s+of\s+ETB([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val totalRe    = Regex("""total\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""(?:Current\s+)?Balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val refRe      = Regex("""[?&]id=([A-Z0-9]+)""")       // extract from URL if present

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBE", ignoreCase = true) &&
        body.contains("transfer", ignoreCase = true) &&
        body.contains("from your account", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            val dateMatch = dateRe.find(body)
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Commercial Bank of Ethiopia",
                type          = TransactionType.TRANSFER_OUT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = toRe.find(body)?.groupValues?.get(1)?.trim(),
                accountNumber = accountRe.find(body)?.groupValues?.get(1),
                refNumber     = refRe.find(body)?.groupValues?.get(1),
                dateTime      = ParserUtils.parseDateTime(dateMatch?.groupValues?.get(1), dateMatch?.groupValues?.get(2))
                                ?: sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(scRe.find(body)?.groupValues?.get(1)),
                vat           = ParserUtils.parseAmount(vatRe.find(body)?.groupValues?.get(1)),
                disasterFund  = ParserUtils.parseAmount(dfRe.find(body)?.groupValues?.get(1)),
                totalCharged  = ParserUtils.parseAmount(totalRe.find(body)?.groupValues?.get(1)),
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
