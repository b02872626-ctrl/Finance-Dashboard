package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Telebirr outgoing transfer SMS (sender: 127).
 * Telebirr uses "transferred" (double-r) vs CBE's "transfered".
 */
class TelebirrTransferParser : BankParser {

    private val amountRe  = Regex("""transferred\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    // P2P form: "transferred ETB 30.00 to MUKEREM NUREDIN (2519****6534) on 19/10/2025"
    private val toPeerRe  = Regex("""transferred\s+ETB\s*[\d,.]+\s+to\s+(.+?)\s+on\s+\d{2}/\d{2}/\d{4}""", RegexOption.IGNORE_CASE)
    // Bank form: "transferred ETB 2,000.00 successfully from your telebirr account ... to Commercial Bank of Ethiopia account number 100... on 19/10/2025"
    private val toBankRe  = Regex("""to\s+(.+?account\s+number\s+\S+)\s+on\s+\d{2}/\d{2}/\d{4}""", RegexOption.IGNORE_CASE)
    private val dateRe    = Regex("""on\s+(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    private val accountRe = Regex("""from\s+(?:your\s+)?(?:telebirr\s+)?account\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val txnRe     = Regex("""(?:telebirr\s+)?transaction\s+number\s+is\s+(\S+)""", RegexOption.IGNORE_CASE)
    // Real Telebirr text: "The service fee is  ETB 0.87 and  15% VAT on the service fee is ETB 0.13"
    private val scRe      = Regex("""(?:S\.?charge|service\s+fee)\s+(?:is\s+)?(?:of\s+)?ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    // Two phrasings: CBE-style "VAT(15%) of ETB X" and Telebirr "15% VAT on the service fee is ETB X".
    // The negated class [^.] keeps us inside the same sentence and is case-fold-safe (period has no case).
    private val vatRe     = Regex("""(?:VAT\(15%\)\s+of\s+ETB|15%\s+VAT[^.]*?ETB)\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val dfRe      = Regex("""Disaster\s+Fund\s+\(5%\)\s+of\s+ETB([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val totalRe   = Regex("""total\s+of\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""(?:Current\s+)?(?:E-Money\s+Account\s+)?[Bb]alance\s+is\s+ETB\s*([\d,]+\.?\d*)""")

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" &&
        body.contains("transferred ETB", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            val dateMatch = dateRe.find(body)
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Telebirr",
                type          = TransactionType.TRANSFER_OUT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = (toPeerRe.find(body)?.groupValues?.get(1)
                                    ?: toBankRe.find(body)?.groupValues?.get(1))?.trim(),
                accountNumber = accountRe.find(body)?.groupValues?.get(1),
                refNumber     = txnRe.find(body)?.groupValues?.get(1)?.trim(),
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
