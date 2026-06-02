package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Telebirr incoming (received) funds.
 * Example: "Dear yohannes, You have received  ETB 1,000.00 by transaction number DD19GI0GK9
 *           on 2026-04-01 15:33:19 from Bank of Abyssinia to your telebirr Account
 *           251954998471 - yohannes tadesse gbereyohannes. Your current balance is ETB 1,094.48."
 */
class TelebirrCreditParser : BankParser {

    private val amountRe    = Regex("""received\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val txnRe       = Regex("""transaction\s+number\s+(?:is\s+)?(\S+)""", RegexOption.IGNORE_CASE)
    // ISO date (bank-to-wallet: "on 2025-10-19 09:03:06")
    private val isoDateRe   = Regex("""on\s+(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    // dd/MM/yyyy date (P2P: "on 20/10/2025 13:52:01")
    private val slashDateRe = Regex("""on\s+(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})""", RegexOption.IGNORE_CASE)
    // Bank-to-wallet form: "from CBE to your telebirr Account 251947..."
    private val fromBankRe  = Regex("""from\s+(.+?)\s+to\s+(?:your\s+)?telebirr\s+Account""", RegexOption.IGNORE_CASE)
    // Peer form: "from Abel Abebe(2519****8602)  on 20/10/2025" (parenthetical phone optional)
    private val fromPeerRe  = Regex("""from\s+([^()\n]+?)\s*\(?2519[\d*]+\)?\s+on\s+\d{2}/\d{2}/\d{4}""", RegexOption.IGNORE_CASE)
    // Stable counterparty identifier — phone token "2519****8602" anywhere in body.
    private val peerPhoneRe = Regex("""(251[\d*]{8,12})""")
    private val accountRe   = Regex("""Account\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val balanceRe   = Regex("""current\s+(?:E-Money\s+Account\s+)?balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" &&
        body.contains("You have received", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null

            var ts = sms.timestamp
            val iso = isoDateRe.find(body)
            if (iso != null) {
                ts = runCatching {
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .parse("${iso.groupValues[1]} ${iso.groupValues[2]}")?.time
                }.getOrNull() ?: ts
            } else {
                val slash = slashDateRe.find(body)
                if (slash != null) {
                    ts = ParserUtils.parseDateTime(slash.groupValues[1], slash.groupValues[2]) ?: ts
                }
            }

            val bankSource = fromBankRe.find(body)?.groupValues?.get(1)?.trim()
            val peerSource = fromPeerRe.find(body)?.groupValues?.get(1)?.trim()
            val isInternalBankTransfer = bankSource != null && peerSource == null
            val txType =
                if (isInternalBankTransfer) TransactionType.INTERNAL_TRANSFER.name
                else TransactionType.CREDIT.name
            val counterparty = if (isInternalBankTransfer) {
                shortenBankCounterparty("from", bankSource!!)
            } else {
                peerSource ?: bankSource
            }

            // P2P only — lift the (2519****XXXX) phone into counterpartyId so
            // the same person matches across spelling variants in future SMS.
            val counterpartyId = if (isInternalBankTransfer) null
                                 else peerPhoneRe.find(body)?.groupValues?.get(1)

            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Telebirr",
                type          = txType,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = counterparty,
                accountNumber = accountRe.find(body)?.groupValues?.get(1),
                refNumber     = txnRe.find(body)?.groupValues?.get(1)?.trimEnd('.', ',', ';', ':'),
                dateTime      = ts,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body,
                counterpartyId = counterpartyId
            )
        } catch (e: Exception) { null }
    }

    /** Same helper as TelebirrTransferParser — pretty short form for chips. */
    private fun shortenBankCounterparty(direction: String, raw: String): String {
        val cleaned = raw
            .replace(Regex("""\s+account\s+number\s+""", RegexOption.IGNORE_CASE), " ")
            .replace("Commercial Bank of Ethiopia", "CBE", ignoreCase = true)
            .replace("Bank of Abyssinia", "BOA", ignoreCase = true)
            .replace("Awash Bank", "Awash", ignoreCase = true)
            .replace("Dashen Bank", "Dashen", ignoreCase = true)
            .trim()
        return "$direction $cleaned"
    }
}
