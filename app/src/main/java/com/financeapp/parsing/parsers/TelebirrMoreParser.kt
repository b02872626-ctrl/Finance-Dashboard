package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Telebirr wordings the older parsers miss:
 *  - Cash out at an agent: "Your telebirr account 2519… has been debited with
 *    ETB 600.00 on 09/07/2025 09:05:42 at telebirr Agent 271404."
 *  - ATM withdrawal reversed: "Your transaction for ATM withdraw request … and
 *    with amount ETB 200.00 is reversed to your account"
 *  - Bank to telebirr, in Amharic: "… 2,000.00 ብር ከ Commercial Bank of Ethiopia
 *    ወደ ቴሌብር አካውንትዎ … ተላልፏል"
 *  - Mela loan paid back in part: "Your outstanding credit amount has been
 *    partially paid successfully. The paid amount is ETB 3,000.00"
 */
class TelebirrMoreParser : BankParser {

    private val agentRe     = Regex("""debited\s+with\s+ETB\s*([\d,]+\.?\d*)\s+on\s+(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2}:\d{2})\s+at\s+telebirr\s+Agent\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val atmRevRe    = Regex("""ATM\s+withdraw\s+request.*?amount\s+ETB\s*([\d,]+\.?\d*)\s+is\s+reversed""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val amBankRe    = Regex("""([\d,]+\.?\d*)\s*ብር\s+ከ\s+(.+?)\s+ወደ\s+ቴሌብር""")
    private val partialRe   = Regex("""partially\s+paid.*?paid\s+amount\s+is\s+ETB\s*([\d,]+\.?\d*)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val isoDateRe   = Regex("""(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})""")
    private val txnRe       = Regex("""(?:transaction\s+number\s+is|ግብይት\s+ቁጥር)\s+([A-Z0-9]+)""", RegexOption.IGNORE_CASE)
    private val feeRe       = Regex("""service\s+fee\s+is\s*ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val balanceRe   = Regex("""(?:balance\s+is\s+ETB|ቀሪ\s+ሂሳብ)\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)

    override fun canParse(sender: String, body: String): Boolean =
        sender == "127" && listOf(agentRe, atmRevRe, amBankRe, partialRe).any { it.containsMatchIn(body) }

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val agent = agentRe.find(body)
            val atm = atmRevRe.find(body)
            val bank = amBankRe.find(body)
            val partial = partialRe.find(body)
            val (type, amountRaw, counterparty) = when {
                agent != null   -> Triple(TransactionType.DEBIT, agent.groupValues[1], "Cash withdrawal (agent ${agent.groupValues[4]})")
                atm != null     -> Triple(TransactionType.CREDIT, atm.groupValues[1], "ATM withdrawal reversed")
                bank != null    -> Triple(TransactionType.CREDIT, bank.groupValues[1], bank.groupValues[2].trim())
                partial != null -> Triple(TransactionType.DEBIT, partial.groupValues[1], "Telebirr Mela repayment")
                else -> return null
            }
            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Telebirr",
                type          = type.name,
                amount        = ParserUtils.parseAmount(amountRaw) ?: return null,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = counterparty,
                accountNumber = null,
                refNumber     = txnRe.find(body)?.groupValues?.get(1),
                dateTime      = ParserUtils.parseDateTime(agent?.groupValues?.get(2), agent?.groupValues?.get(3))
                    ?: ParserUtils.parseIsoDateTime(isoDateRe.find(body)?.groupValues?.get(1))
                    ?: sms.timestamp,
                serviceCharge = ParserUtils.parseAmount(feeRe.find(body)?.groupValues?.get(1)),
                vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }
}
