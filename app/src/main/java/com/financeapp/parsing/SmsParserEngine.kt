package com.financeapp.parsing

import com.financeapp.data.categorize.MerchantKey
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.parsers.AwashCreditParser
import com.financeapp.parsing.parsers.BerhanBankCreditParser
import com.financeapp.parsing.parsers.BerhanBankDebitParser
import com.financeapp.parsing.parsers.AwashDebitParser
import com.financeapp.parsing.parsers.BoaCreditParser
import com.financeapp.parsing.parsers.BoaDebitParser
import com.financeapp.parsing.parsers.CbeBirrCreditParser
import com.financeapp.parsing.parsers.CbeBirrTransferParser
import com.financeapp.parsing.parsers.CbeBirrWithdrawalParser
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

    /**
     * Full SMS-sender allowlist across Ethiopian banks + digital wallets.
     *
     * Capture vs parse — IMPORTANT: being in this list means the SMS gets
     * ingested into `raw_sms`, but it ONLY becomes a transaction if a
     * dedicated parser claims it via canParse(). Banks marked "capture-only"
     * below currently have NO parser — their messages land in raw_sms (so
     * we don't lose data) but are filtered out at parse time as UNKNOWN.
     *
     * When a parser is added for a capture-only bank, beta4's
     * auto-rescan-on-upgrade hook will retroactively turn the captured
     * raw_sms rows into transactions.
     *
     * Sender variants — Samsung's thread title can differ from the actual
     * SMS sender field. We allowlist every plausible spelling.
     */
    private val financialSenders = setOf(
        // ── Already have parsers ─────────────────────────────────────────
        "CBE", "CBEBirr", "127",
        "BOA", "Abyssinia",                              // BoA: existing + alt
        "Awash Bank", "AwashBank", "AWASH",              // Awash: existing + alts
        "DashenBank", "Dashen",                          // Dashen: existing + alt
        // ── Capture-only (no parser yet) ─────────────────────────────────
        "Amole",                                          // Dashen's digital wallet
        "HibretBank", "HIBRET",                          // Hibret Bank
        "NIBBank", "NIB",                                // Nib International
        "Coopbank", "COOP",                              // Coop Bank of Oromia
        "ZemenBank", "ZEMEN",                            // Zemen Bank
        "Wegagen",                                        // Wegagen Bank
        "BunnaBank", "BUNNA",                            // Bunna Bank
        "OromiaBank", "OB",                              // Oromia Bank
        "AmharaBank", "AMHARA",                          // Amhara Bank
        "EnatBank",                                       // Enat Bank
        "GlobalBank",                                     // Global Bank (formerly Global Lion)
        "Berhan Bank", "Berhan", "BerhanBank", "BERHAN", "BerhanBnk", "BERHANBANK",
        "AbayBank",                                       // Abay Bank
        "AdIB",                                           // Addis International Bank
        "DGB",                                            // Debub Global Bank
        "Tsedey",                                         // Tsedey Bank
        "Siinqee",                                        // Siinqee Bank
        "Shabelle",                                       // Shabelle Bank
        "HijraBank",                                      // Hijra Bank (interest-free)
        "ZadBank"                                         // Zad Bank (interest-free)
    )
    private val senderBankNames = mapOf(
        // Already-parsed
        "CBE" to "Commercial Bank of Ethiopia",
        "CBEBirr" to "CBE Birr",
        "Abyssinia" to "Bank of Abyssinia",
        "AwashBank" to "Awash Bank",
        "AWASH" to "Awash Bank",
        "Dashen" to "Dashen Bank",
        // Capture-only — added so the History list shows a useful name
        // even before the parsers exist.
        "Amole" to "Amole (Dashen)",
        "HibretBank" to "Hibret Bank",
        "HIBRET" to "Hibret Bank",
        "NIBBank" to "Nib International Bank",
        "NIB" to "Nib International Bank",
        "Coopbank" to "Cooperative Bank of Oromia",
        "COOP" to "Cooperative Bank of Oromia",
        "ZemenBank" to "Zemen Bank",
        "ZEMEN" to "Zemen Bank",
        "Wegagen" to "Wegagen Bank",
        "BunnaBank" to "Bunna Bank",
        "BUNNA" to "Bunna Bank",
        "OromiaBank" to "Oromia Bank",
        "OB" to "Oromia Bank",
        "AmharaBank" to "Amhara Bank",
        "AMHARA" to "Amhara Bank",
        "EnatBank" to "Enat Bank",
        "GlobalBank" to "Global Bank Ethiopia",
        "Berhan Bank" to "Berhan Bank",
        "Berhan" to "Berhan Bank",
        "BerhanBank" to "Berhan Bank",
        "BERHAN" to "Berhan Bank",
        "BerhanBnk" to "Berhan Bank",
        "BERHANBANK" to "Berhan Bank",
        "AbayBank" to "Abay Bank",
        "AdIB" to "Addis International Bank",
        "DGB" to "Debub Global Bank",
        "Tsedey" to "Tsedey Bank",
        "Siinqee" to "Siinqee Bank",
        "Shabelle" to "Shabelle Bank",
        "HijraBank" to "Hijra Bank",
        "ZadBank" to "Zad Bank",
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
        BerhanBankCreditParser(),
        BerhanBankDebitParser(),
        // CBEBirr parsers — match on sender == "CBEBirr" so they're safe
        // to keep next to the CBE parsers (which match on "CBE"). Order
        // them by specificity: withdrawal + transfer before credit.
        CbeBirrWithdrawalParser(),
        CbeBirrTransferParser(),
        CbeBirrCreditParser(),
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
     * Patterns that match SMS we never want to ingest — OTPs, marketing,
     * holiday greetings, voucher requests (just a code, the real txn comes
     * later), insufficient-balance failures, Amharic promo blasts.
     *
     * Curated from the 8,575-row real-user corpus where ~10.6% of incoming
     * SMS were non-transactional but still made it to UnknownBankParser.
     *
     * Order matters only for short-circuit performance; correctness is the
     * same regardless of order.
     */
    private val ignorePatterns: List<Regex> = listOf(
        // OTP / verification codes
        Regex("""\bOTP\b""", RegexOption.IGNORE_CASE),
        Regex("""\bverification code\b""", RegexOption.IGNORE_CASE),
        Regex("""\bone[- ]time password\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsecurity code\b""", RegexOption.IGNORE_CASE),
        Regex("""\bactivation code\b""", RegexOption.IGNORE_CASE),
        // SMS Retriever auto-fill format: starts with "<#>" then a code
        Regex("""^<#>\s*\d+""", RegexOption.MULTILINE),
        // PIN-only / "Your otp is :"
        Regex("""\bYour otp is\b""", RegexOption.IGNORE_CASE),
        Regex("""\bPIN[\s:]+\d{3,}\b""", RegexOption.IGNORE_CASE),
        // "Please keep this code private" warnings
        Regex("""please keep this code private""", RegexOption.IGNORE_CASE),
        // "do not share" / "please don't share"
        Regex("""\b(do not share|don't share|please don't share)\b""", RegexOption.IGNORE_CASE),
        // Telebirr "ATM withdrawal secret code is X for an amount of ETB Y" —
        // the actual withdrawal SMS comes from CBE later, this is just the code
        Regex("""ATM withdrawal secret code""", RegexOption.IGNORE_CASE),
        // CBEBirr "as per your request for CBE Birr ATM cash out voucher" —
        // again, just the voucher; the real withdrawal SMS comes separately
        Regex("""CBE Birr ATM cash out voucher""", RegexOption.IGNORE_CASE),
        // Failed transaction notifications
        Regex("""insufficient balance""", RegexOption.IGNORE_CASE),
        Regex("""\bSorry,\s*You have insufficient""", RegexOption.IGNORE_CASE),
        // Holiday-greeting URLs (combanketh.et/holidays etc.)
        Regex("""combanketh\.et[^\s]*/holidays""", RegexOption.IGNORE_CASE),
        // Lottery / promo (existing rule, kept)
        Regex("""\blottery ticket\b""", RegexOption.IGNORE_CASE),
        // Common Amharic marketing keywords ("greetings", "expo", "BYD car")
        Regex("""እንኳን"""),         // "congratulations" / holiday greeting opener
        Regex("""ዓለም ዋንጫ"""),       // "World Cup"
        Regex("""ኢድ ሙባረክ"""),      // "Eid Mubarak"
        Regex("""መልካም በዓል"""),    // "Happy holiday"
        Regex("""BYD"""),            // BYD car promotions
        Regex("""ፋይዳ"""),           // NBE Fayda national-ID notices (Amharic)
        // English NBE Fayda notices — every bank sends these, no transaction
        Regex("""\bFayda\s+(ID|Alias\s+Number|FAN)\b""", RegexOption.IGNORE_CASE),
        Regex("""link\s+(?:your\s+)?Fayda""", RegexOption.IGNORE_CASE),
    )

    /**
     * Parse the SMS and return a TransactionEntity.
     * Returns null only if the sender is not a tracked financial sender,
     * OR the body matches one of the IGNORE patterns (non-transactional).
     */
    fun parse(sms: SmsMessage): TransactionEntity? {
        val normalizedSender = sms.sender.trim()
        if (!isFinancialSms(normalizedSender)) return null

        // Fast-fail on known non-transaction patterns. Walks the list once;
        // any match short-circuits.
        for (pattern in ignorePatterns) {
            if (pattern.containsMatchIn(sms.body)) return null
        }

        for (parser in parsers) {
            try {
                if (parser.canParse(normalizedSender, sms.body)) {
                    return parser.parse(sms)?.withReceiptLinkFallback()?.withMerchantKey()
                }
            } catch (_: Exception) {
                // Ignore parser failure and keep trying others.
            }
        }

        return UnknownBankParser().parse(sms).withReceiptLinkFallback().withMerchantKey()
    }

    private fun TransactionEntity.withReceiptLinkFallback(): TransactionEntity =
        if (!receiptLink.isNullOrBlank()) this else copy(receiptLink = ParserUtils.extractFirstUrl(rawBody))

    /**
     * Set merchantKey from the counterparty so Daily Review's per-merchant
     * rule lookup + "apply to similar" works without each parser having to
     * compute it.
     *
     * Also fills in a counterparty fallback when the parser couldn't extract
     * a name (e.g. an SMS that only references the account/phone/bank).
     * Renders as "to <bank> <acct>" / "from <bank>" so the UI shows something
     * meaningful instead of "Unknown merchant".
     */
    private fun TransactionEntity.withMerchantKey(): TransactionEntity {
        val filled = if (counterparty.isNullOrBlank()) {
            copy(counterparty = buildCounterpartyFallback())
        } else this
        return if (filled.merchantKey != null) filled
               else filled.copy(merchantKey = MerchantKey.normalize(filled.counterparty))
    }

    /**
     * Direction-aware fallback string. Inbound (CREDIT) → "from …",
     * outbound (everything else) → "to …". Uses bank name + masked
     * account number when available.
     */
    private fun TransactionEntity.buildCounterpartyFallback(): String {
        val isInbound = type == TransactionType.CREDIT.name
        val direction = if (isInbound) "from" else "to"
        val acct = accountNumber?.takeIf { it.isNotBlank() }
        val bank = bankName.ifBlank { sender }
        return when {
            acct != null -> "$direction $bank $acct"
            else         -> "$direction $bank"
        }
    }
}
