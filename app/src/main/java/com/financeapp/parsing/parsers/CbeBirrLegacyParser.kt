package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Older CBE Birr wordings (2024–2026):
 *  - "you have sent 200.00Br. to NARDOS WENDIMU on 26/06/25 14:26,Txn ID CFQ9PVCXT9."
 *  - "you received 600.00Br. from 251947343024 - nahusenay gebreamlak teka on …"
 *  - "you have received 2.00Br. salary payment from 036000 - Dil Gebeya Branch on …"
 *  - "you paid 692.04Br. to 232320 - Chapafinancialtechnology s.c on …"
 *  - "reversal of transaction CL84105E978 has been successfully completed on … and
 *     200.00Br. is credited to your account."
 */
class CbeBirrLegacyParser : BankParser {

    private val sentRe     = Regex("""you\s+have\s+sent\s+([\d,]+\.?\d*)\s*Br\.?\s+to\s+(.+?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
    private val receivedRe = Regex("""you\s+(?:have\s+)?received\s+([\d,]+\.?\d*)\s*Br\.?\s+(?:salary\s+payment\s+)?from\s+(?:\d+\s*-\s*)?(.+?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
    private val paidRe     = Regex("""you\s+paid\s+([\d,]+\.?\d*)\s*Br\.?\s+to\s+(?:\d+\s*-\s*)?(.+?)\s+on\s+\d""", RegexOption.IGNORE_CASE)
    private val reversalRe = Regex("""reversal\s+of\s+transaction\s+([A-Z0-9]+).*?([\d,]+\.?\d*)\s*Br\.?\s+is\s+credited""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val refRe      = Regex("""(?:Txn\s+ID|Transaction\s+ID\s+is)\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""balance\s+is\s+([\d,]+\.?\d*)\s*Br""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender.equals("CBEBirr", ignoreCase = true) &&
            (sentRe.containsMatchIn(body) || receivedRe.containsMatchIn(body) ||
                paidRe.containsMatchIn(body) || reversalRe.containsMatchIn(body))

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val sent = sentRe.find(body)
            val received = receivedRe.find(body)
            val paid = paidRe.find(body)
            val reversal = reversalRe.find(body)
            val (type, amountRaw, counterparty) = when {
                sent != null     -> Triple(TransactionType.TRANSFER_OUT, sent.groupValues[1], sent.groupValues[2])
                received != null -> Triple(TransactionType.CREDIT, received.groupValues[1], received.groupValues[2])
                paid != null     -> Triple(TransactionType.PAYMENT, paid.groupValues[1], paid.groupValues[2])
                reversal != null -> Triple(TransactionType.CREDIT, reversal.groupValues[2], "Reversal")
                else -> return null
            }
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "CBE Birr",
                type          = type.name,
                amount        = ParserUtils.parseAmount(amountRaw) ?: return null,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = counterparty.trim(),
                accountNumber = null,
                refNumber     = refRe.find(body)?.groupValues?.get(1) ?: reversal?.groupValues?.get(1),
                dateTime      = sms.timestamp,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
