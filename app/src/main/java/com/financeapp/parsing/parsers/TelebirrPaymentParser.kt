package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Telebirr payment (goods/services purchase) SMS.
 * Example: "Dear yohannes You have paid ETB 307.50 for goods purchased from
 *           500423 - CHAPA FINANCIAL TECHNOLOGY SHARE COMPANY on 01/04/2026 15:33:26.
 *           Your transaction number is  DD18GI0O82. Your current balance is ETB 786.98."
 */
class TelebirrPaymentParser : BankParser {

    private val amountRe    = Regex("""paid\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    // Merchant: "for goods/fuel purchased from <CODE - NAME>" or "for X payment to NAME"
    private val fromMerchantRe = Regex("""from\s+(.+?)\s+(?:on\s+\d{2}/\d{2}/\d{4}|for\s+plate\s+number)""", RegexOption.IGNORE_CASE)
    private val toMerchantRe   = Regex("""payment\s+to\s+(.+?)\s+with\s+Payment\s+Reference""", RegexOption.IGNORE_CASE)
    // Telecom package: "paid ETB 63.00 for package Daily unlimited Internet purchase made for 947343024"
    private val packageRe   = Regex("""for\s+package\s+(.+?)\s+(?:purchase\s+made\s+for|for\s+\d)""", RegexOption.IGNORE_CASE)
    // "for Ethiopian Disaster Risk Management fund (1% of your loan amount)"
    private val purposeRe   = Regex("""for\s+(Ethiopian\s+Disaster\s+Risk\s+Management\s+fund|Third\s+Party\s+Insurance[^.]*?)\b""", RegexOption.IGNORE_CASE)
    // Date and time are joined without "at": "01/04/2026 15:33:26"
    private val dateRe      = Regex("""on\s+(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    private val txnRe       = Regex("""transaction\s+number\s+is\s+(\S+)""", RegexOption.IGNORE_CASE)
    private val balanceRe   = Regex("""(?:current\s+)?(?:telebirr\s+)?balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val scRe        = Regex("""service\s+fee\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val vatRe       = Regex("""15%\s+VAT[^E]*?ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" &&
        body.contains("You have paid ETB", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null
            val dateMatch = dateRe.find(body)
            val txn = txnRe.find(body)?.groupValues?.get(1)?.trim()

            // Counterparty priority: explicit merchant > package name > generic purpose
            val counterparty = fromMerchantRe.find(body)?.groupValues?.get(1)?.trim()
                ?: toMerchantRe.find(body)?.groupValues?.get(1)?.trim()
                ?: packageRe.find(body)?.groupValues?.get(1)?.trim()?.let { "Telecom package: $it" }
                ?: purposeRe.find(body)?.groupValues?.get(1)?.trim()

            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Telebirr",
                type          = TransactionType.PAYMENT.name,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = counterparty,
                accountNumber = null,
                refNumber     = txn?.trimEnd('.', ',', ';', ':'),
                dateTime      = ParserUtils.parseDateTime(dateMatch?.groupValues?.get(1), dateMatch?.groupValues?.get(2))
                                ?: sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(scRe.find(body)?.groupValues?.get(1)),
                vat           = ParserUtils.parseAmount(vatRe.find(body)?.groupValues?.get(1)),
                disasterFund  = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
