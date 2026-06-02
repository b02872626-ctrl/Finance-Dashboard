package com.financeapp.parsing.parsers

import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.BankParser
import com.financeapp.parsing.ParserUtils
import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.TransactionType

/**
 * Parses Berhan Bank incoming (credit) SMS — TWO sub-variants seen in
 * the real-world corpus:
 *
 *  A. Simple in-network credit (self-reference, no origin info):
 *     "Dear Natnael, A/C No. 103002XXXXX67 has been credited ETB 16,000
 *      on 23-Mar-2026. Reference is 17742610528660003539. Balance is
 *      ETB 16,029.7. To Natnael Getinet . Berhan Stress Free Banking"
 *     (The "To Natnael Getinet" just re-states the account holder — not
 *      a counterparty.)
 *
 *  B. Cross-bank IPS incoming (the user moved money in from another bank):
 *     "Dear Natnael, ... has been credited ETB 5,000 on 01-May-2026.
 *      Reference is 17776... Balance is ETB 5,034.95. Ips_incoming Txn
 *      From {cbetetaa__1000482226926} . Berhan Stress Free Banking"
 *     The `{<SWIFT_BIC>__<acct>}` payload identifies the originating
 *     bank — `cbetetaa` = CBE Ethiopia (BIC code CBETETAA). When this
 *     pattern is present we treat the row as INTERNAL_TRANSFER so it
 *     doesn't double-count in expense totals against the corresponding
 *     CBE debit row.
 *
 * Differences from Berhan's debit format:
 *  - Customer name uses comma not semicolon: `Dear Natnael,`
 *  - Amount HAS space after ETB: `ETB 16,000`
 *  - Date format `dd-Mon-yyyy` (month abbreviation, not numeric)
 *  - Reference label is `Reference is` (full phrase)
 *  - Balance label is `Balance is` (full phrase)
 */
class BerhanBankCreditParser : BankParser {

    private val amountRe   = Regex("""credited\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    private val accountRe  = Regex("""A/C\s+No\.\s*(\S+)""", RegexOption.IGNORE_CASE)
    // Berhan credit format: "on 23-Mar-2026"
    private val dateRe     = Regex("""on\s+(\d{2})-([A-Za-z]{3})-(\d{4})""")
    private val refRe      = Regex("""Reference\s+is\s+(\S+?)\.""", RegexOption.IGNORE_CASE)
    private val balanceRe  = Regex("""Balance\s+is\s+ETB\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    // IPS incoming: "Ips_incoming Txn From {cbetetaa__1000482226926}"
    private val ipsFromRe  = Regex("""Ips_incoming\s+Txn\s+From\s+\{([^}]+)\}""", RegexOption.IGNORE_CASE)

    // SWIFT BIC prefix → human-readable bank name. Only Ethiopian banks
    // the user is realistically going to receive IPS from.
    private val bicToBank: Map<String, String> = mapOf(
        "cbetetaa" to "CBE",
        "abyseth"  to "BoA",
        "awinetaa" to "Awash",
        "dashetaa" to "Dashen",
        "nibietaa" to "Nib",
        "wegaetaa" to "Wegagen",
        "hbreetaa" to "Hibret",
        "coopetaa" to "Coop Oromia",
        "zemenethaa" to "Zemen",
        "berietaa" to "Berhan"
    )

    override fun canParse(sender: String, body: String): Boolean =
        sender.contains("Berhan", ignoreCase = true) &&
        body.contains("has been credited", ignoreCase = true)

    override fun parse(sms: SmsMessage): TransactionEntity? {
        return try {
            val body = sms.body
            val amount = ParserUtils.parseAmount(amountRe.find(body)?.groupValues?.get(1)) ?: return null

            val ts = dateRe.find(body)?.let { m ->
                val day = m.groupValues[1]; val mon = m.groupValues[2]; val yr = m.groupValues[3]
                runCatching {
                    java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.US)
                        .parse("$day-$mon-$yr")?.time
                }.getOrNull()
            } ?: sms.timestamp

            // Detect cross-bank IPS incoming → tag as INTERNAL_TRANSFER
            val ipsPayload = ipsFromRe.find(body)?.groupValues?.get(1)?.trim()
            val isInternalTransfer = ipsPayload != null
            val txType =
                if (isInternalTransfer) TransactionType.INTERNAL_TRANSFER.name
                else TransactionType.CREDIT.name
            val counterparty = if (ipsPayload != null) {
                buildIpsCounterparty(ipsPayload)
            } else null  // Simple credit has no extractable counterparty; engine fallback fills "from Berhan Bank ..."

            TransactionEntity(
                sender        = sms.sender,
                bankName      = "Berhan Bank",
                type          = txType,
                amount        = amount,
                balance       = ParserUtils.parseAmount(balanceRe.find(body)?.groupValues?.get(1)),
                counterparty  = counterparty,
                accountNumber = accountRe.find(body)?.groupValues?.get(1),
                refNumber     = refRe.find(body)?.groupValues?.get(1)?.trim(),
                dateTime      = ts,
                serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
                currency      = "ETB",
                rawBody       = body
            )
        } catch (e: Exception) { null }
    }

    /**
     * "cbetetaa__1000482226926" → "from CBE 1000482226926".
     * Falls back to the raw BIC if unknown.
     */
    private fun buildIpsCounterparty(payload: String): String {
        val parts = payload.split("__", limit = 2)
        val bic = parts[0].lowercase()
        val acct = parts.getOrNull(1).orEmpty()
        val bank = bicToBank[bic] ?: bic.uppercase()
        return if (acct.isNotEmpty()) "from $bank $acct" else "from $bank"
    }
}
