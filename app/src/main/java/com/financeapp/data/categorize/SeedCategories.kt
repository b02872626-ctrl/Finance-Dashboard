package com.financeapp.data.categorize

/**
 * Hand-curated merchant → category seeds. Used as a fallback when the user
 * hasn't categorized a merchant yet. Patterns are matched against the
 * uppercase, noise-stripped MerchantKey output, so they assume no dates,
 * amounts, refs, or phone numbers in the input.
 *
 * Confidence emitted by these matches is 0.80 (high but not user-confirmed).
 *
 * Curated from the 3,268-row Ethiopian SMS corpus. Add patterns as new
 * merchants appear — keep order specific-to-general so the first match wins.
 */
object SeedCategories {

    private data class Seed(val pattern: Regex, val category: String)

    private val seeds: List<Seed> = listOf(
        // ── Coffee chains (must come before generic Food) ─────────────────
        Seed(Regex("""\bKALDI'?S?\b"""),                 "Coffee and refreshments"),
        Seed(Regex("""\b(?:CAFFE|TOMOCA|COFFEE|CAFE)\b"""), "Coffee and refreshments"),

        // ── Food / restaurants ────────────────────────────────────────────
        Seed(Regex("""\b(?:RESTAURANT|RESTO)\b"""),       "Food"),
        Seed(Regex("""\b(?:BURGER|PIZZA|NOODLE|SUSHI|KEBAB|DOUGH|FRIED\s+CHICKEN)\b"""),
                                                          "Food"),
        Seed(Regex("""\b(?:LUCY|SALAM|EFOY|SISHU|DRAGON|HARMONY|MASTER|RAS|YOD\s+ABYSSINIA)\b"""),
                                                          "Food"),
        Seed(Regex("""\b(?:BAKERY|BREAD|CAKE|PASTRY|DOUGHNUT)\b"""), "Food"),

        // ── Transport ─────────────────────────────────────────────────────
        Seed(Regex("""\b(?:RIDE|FERES|ZAYRIDE|ZAY\s+RIDE|EXPRESS\s+RIDE)\b"""), "Transport"),
        Seed(Regex("""\b(?:BAJAJ|TAXI|UBER)\b"""),         "Transport"),
        Seed(Regex("""\b(?:TOTAL|OILIBYA|OILYBA|NOC|NAS|YETEBABERUT|FUEL|PETROL|GAS\s+STATION)\b"""),
                                                          "Transport"),

        // ── Bills / utilities ─────────────────────────────────────────────
        Seed(Regex("""\b(?:ETHIOPIAN\s+ELECTRIC|EEPCO|ELECTRIC|ETC\s+ELECTRIC)\b"""),
                                                          "Bills"),
        Seed(Regex("""\b(?:WATER|AAWSA|WATER\s+SUPPLY)\b"""), "Bills"),
        Seed(Regex("""\b(?:ETHIOTELECOM|ETHIO\s+TELECOM|SAFARICOM)\b"""), "Bills"),
        Seed(Regex("""\b(?:AIRTIME|PACKAGE|DATA\s+PACKAGE|MOBILE\s+DATA|TOP\s*UP|TOPUP)\b"""),
                                                          "Bills"),
        Seed(Regex("""\b(?:DSTV|CANAL|NETFLIX|SPOTIFY|YOUTUBE\s+PREMIUM|HULU)\b"""),
                                                          "Bills"),
        Seed(Regex("""\b(?:INSURANCE|NYALA|AWASH\s+INSURANCE)\b"""), "Bills"),

        // ── Shopping (groceries, retail) ──────────────────────────────────
        Seed(Regex("""\b(?:SHOLA|FRESH\s+CORNER|SAFEWAY|SHOA|FRIENDSHIP|CITY\s+CENTER|GETU|QUEEN|BAMBIS|NOVIS)\b"""),
                                                          "Shopping"),
        Seed(Regex("""\bSUPERMARKET\b"""),                "Shopping"),
        Seed(Regex("""\b(?:MARKET|RETAIL|MALL|STORE)\b"""), "Shopping"),
        Seed(Regex("""\b(?:AMAZON|JUMIA|ALIEXPRESS|TEMU)\b"""), "Shopping"),

        // ── Health ────────────────────────────────────────────────────────
        Seed(Regex("""\b(?:PHARMACY|CLINIC|HOSPITAL|MEDICAL|MEDICINE|LANCET|YERER|KADISCO|MYUNG\s+SUNG)\b"""),
                                                          "Health"),

        // ── Entertainment / drinks ────────────────────────────────────────
        Seed(Regex("""\b(?:CINEMA|MOVIE|THEATER|CLUB|PUB|BAR|LOUNGE|NIGHT)\b"""),
                                                          "Drinks and fun"),

        // ── Rent ──────────────────────────────────────────────────────────
        Seed(Regex("""\b(?:RENT|HOUSE\s+RENT|RENTAL)\b"""), "Rent"),

        // ── Fees (also covered by type fallback) ──────────────────────────
        Seed(Regex("""\b(?:SERVICE\s+CHARGE|VAT|DISASTER\s+FUND|FEES?)\b"""), "Fees"),
    )

    /**
     * @return the first seed category whose pattern appears anywhere in
     *  [merchantKey], or `null` if no seed matched.
     */
    fun match(merchantKey: String): String? {
        for ((re, cat) in seeds) {
            if (re.containsMatchIn(merchantKey)) return cat
        }
        return null
    }
}
