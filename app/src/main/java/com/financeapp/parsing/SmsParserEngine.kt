package com.financeapp.parsing

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.parsers.AwashCreditParser
import com.financeapp.parsing.parsers.AwashDebitParser
import com.financeapp.parsing.parsers.BoaCreditParser
import com.financeapp.parsing.parsers.BoaDebitParser
import com.financeapp.parsing.parsers.CbeCreditParser
import com.financeapp.parsing.parsers.CbeDebitParser
import com.financeapp.parsing.parsers.CbeTransferParser
import com.financeapp.parsing.parsers.DashenCreditParser
import com.financeapp.parsing.parsers.DashenDebitParser
import com.financeapp.parsing.parsers.TelebirrAirtimeParser
import com.financeapp.parsing.parsers.TelebirrCreditParser
import com.financeapp.parsing.parsers.TelebirrMelaCreditParser
import com.financeapp.parsing.parsers.TelebirrMelaRepaymentParser
import com.financeapp.parsing.parsers.TelebirrPaymentParser
import com.financeapp.parsing.parsers.TelebirrTrafficPenaltyParser
import com.financeapp.parsing.parsers.TelebirrTransferParser
import com.financeapp.parsing.parsers.TelebirrWalletCreditParser
import com.financeapp.parsing.parsers.TelebirrWithdrawalParser
import com.financeapp.parsing.parsers.UnknownBankParser

/**
 * Orchestrates SMS parsing by routing each SMS to the first matching parser.
 * Parser priority order matters — more specific parsers go first.
 */
object SmsParserEngine {

    private val financialSenders = setOf("CBE", "127", "BOA", "Awash Bank", "DashenBank")
    private val senderBankNames = mapOf(
        "CBE" to "Commercial Bank of Ethiopia",
        "127" to "Telebirr",
        "BOA" to "Bank of Abyssinia",
        "Awash Bank" to "Awash Bank",
        "DashenBank" to "Dashen Bank"
    )

    private val parsers: List<BankParser> = listOf(
        BoaCreditParser(),
        BoaDebitParser(),
        AwashCreditParser(),
        AwashDebitParser(),
        DashenCreditParser(),
        DashenDebitParser(),
        CbeCreditParser(),
        CbeTransferParser(),
        CbeDebitParser(),
        // Mela parsers must come before generic Telebirr parsers — they match more specific phrasing.
        TelebirrMelaCreditParser(),
        TelebirrMelaRepaymentParser(),
        TelebirrWalletCreditParser(),
        TelebirrCreditParser(),
        TelebirrAirtimeParser(),
        TelebirrTrafficPenaltyParser(),
        TelebirrPaymentParser(),
        TelebirrTransferParser(),
        TelebirrWithdrawalParser(),
        UnknownBankParser()
    )

    fun isFinancialSms(sender: String): Boolean =
        financialSenders.any { it.equals(sender.trim(), ignoreCase = true) }

    fun inferBankName(sms: SmsMessage): String {
        val parsed = parse(sms)
        if (parsed != null && parsed.type != TransactionType.UNKNOWN.name && !parsed.bankName.startsWith("Unknown")) {
            return parsed.bankName
        }

        val normalizedSender = sms.sender.trim()
        return senderBankNames.entries.firstOrNull { (sender, _) ->
            sender.equals(normalizedSender, ignoreCase = true)
        }?.value ?: normalizedSender
    }

    /**
     * Parse the SMS and return a TransactionEntity.
     * Returns null only if the sender is not a tracked financial sender.
     */
    fun parse(sms: SmsMessage): TransactionEntity? {
        val normalizedSender = sms.sender.trim()
        if (!isFinancialSms(normalizedSender)) return null

        if (sms.body.contains("lottery ticket", ignoreCase = true)) {
            return null
        }

        for (parser in parsers) {
            try {
                if (parser.canParse(normalizedSender, sms.body)) {
                    return parser.parse(sms)?.withReceiptLinkFallback()
                }
            } catch (_: Exception) {
                // Ignore parser failure and keep trying others.
            }
        }

        return UnknownBankParser().parse(sms).withReceiptLinkFallback()
    }

    private fun TransactionEntity.withReceiptLinkFallback(): TransactionEntity =
        if (!receiptLink.isNullOrBlank()) this else copy(receiptLink = ParserUtils.extractFirstUrl(rawBody))
}
