package com.financeapp.data.categorize

/**
 * Normalizes a raw merchant / counterparty string from an SMS into a stable
 * lookup key for the Daily Review categorizer + "apply to similar" backfill.
 *
 * Goals:
 *  - Deterministic: the same input always returns the same key.
 *  - Noise-stripping: drops embedded refs/dates/amounts/phone numbers so
 *    `"KALDIS COFFEE 12/04/26"` and `"KALDIS COFFEE"` collapse to the same key.
 *  - Conservative: does NOT shorten distinct business names. "Kaldis Bole"
 *    and "Kaldis Megenagna" stay separate — if the user wants them in the
 *    same category they'll naturally end up there via two confirmations.
 *
 * Output is uppercased, single-space normalized, and trimmed. Returns null
 * for empty/garbage input so callers can treat "no key" as a real signal
 * (e.g. BoA balance updates with no counterparty).
 */
object MerchantKey {

    // Order matters — phone numbers must be stripped before dates/amounts,
    // otherwise an Ethiopian mobile like 0912345678 would partially survive.
    private val phonePatterns = listOf(
        // International ET mobile: 2519XXXXXXXX or +2519XXXXXXXX
        Regex("""\+?2519\d{8}\b"""),
        // Local ET mobile: 09XXXXXXXX
        Regex("""\b09\d{8}\b"""),
        // Telebirr's bracketed phone-tail: (2519****1234) or (****1234)
        Regex("""\((?:\d|\*){3,}\)"""),
    )

    private val refPatterns = listOf(
        // Telebirr refs: TB7724X9, TB1190AA — TB + 6+ alnum
        Regex("""\bTB[A-Z0-9]{5,}\b""", RegexOption.IGNORE_CASE),
        // Bill-pay / generic: BP4421, REF: ABC123, TXN-7720
        Regex("""\b(?:REF|TXN|BP|FT)[\s:#-]*[A-Z0-9]{3,}\b""", RegexOption.IGNORE_CASE),
        // POS terminal codes that look like 6XX2218 (masked card)
        // — keep these in (they carry merchant identity for unbranded POS).
    )

    private val datePatterns = listOf(
        Regex("""\b\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}\b"""),       // 06/04/26
        Regex("""\b\d{4}[/\-.]\d{1,2}[/\-.]\d{1,2}\b"""),         // 2026-04-06
        Regex("""\b\d{1,2}:\d{2}(:\d{2})?\b"""),                  // 08:14 / 08:14:22
    )

    private val amountPatterns = listOf(
        // 1,000.00 or 845.00 or 75
        Regex("""\b\d{1,3}(,\d{3})+(\.\d{1,2})?\b"""),
        Regex("""\b\d+\.\d{2}\b"""),
        // ETB / BR / BIRR currency tokens
        Regex("""\bETB\b""", RegexOption.IGNORE_CASE),
        Regex("""\bBIRR\b""", RegexOption.IGNORE_CASE),
    )

    // Generic noise words that appear next to merchants but don't identify
    // them. NOTE: "POS" stays IN the key — it changes the merchant identity
    // (vs "PAYMENT" or "TRANSFER"), and the original counterparty already
    // dropped action words by the time it reaches us in most cases.
    private val noisePatterns = listOf(
        Regex("""\b(?:ON|AT|TO|FROM|BAL|BALANCE|AVBL|VAT|FEE)\b""", RegexOption.IGNORE_CASE),
    )

    // Branch / store suffixes that the user thinks of as "the same place".
    // Only stripped from the END of the string so "BRANCH OF X" stays intact.
    private val trailingSuffixes = listOf(
        Regex("""\s+BRANCH\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+BR\.?$""", RegexOption.IGNORE_CASE),
        Regex("""\s+SUPER\s*MARKET$""", RegexOption.IGNORE_CASE),
        Regex("""\s+SUPERMARKET$""", RegexOption.IGNORE_CASE),
    )

    /**
     * @return normalized key (uppercase, single-spaced) or `null` if the
     *   input collapses to fewer than 2 alnum characters.
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s: String = raw

        // Strip noise in a deterministic order.
        for (p in phonePatterns)  s = p.replace(s, " ")
        for (p in refPatterns)    s = p.replace(s, " ")
        for (p in datePatterns)   s = p.replace(s, " ")
        for (p in amountPatterns) s = p.replace(s, " ")
        for (p in noisePatterns)  s = p.replace(s, " ")

        // Collapse punctuation to spaces (keep alnum, &, ', - which appear in
        // legit brand names like "Mom & Dad" / "Kaldi's" / "Co-op").
        s = s.replace(Regex("""[^A-Za-z0-9&'\- ]+"""), " ")

        // Uppercase + whitespace collapse.
        s = s.uppercase().replace(Regex("""\s+"""), " ").trim()

        // Strip trailing branch/store suffixes.
        for (p in trailingSuffixes) {
            val previous = s
            s = p.replace(s, "")
            if (s != previous) s = s.trim()
        }

        // Final sanity: at least 2 alphanumeric characters survived.
        val alnum = s.count { it.isLetterOrDigit() }
        return if (alnum >= 2) s else null
    }
}
