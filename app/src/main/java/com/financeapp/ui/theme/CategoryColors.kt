package com.financeapp.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/**
 * One color per category, picked so categories are scannable at a glance —
 * "see the orange pill, it's Food" before you read the label.
 *
 * Two-tier resolution:
 *   1. Curated palette: known categories get hand-picked hues so semantically
 *      similar ones share a color family (Food/Coffee = warm orange; Salary/
 *      Income = mint green; Transfer/Internal = neutral grey).
 *   2. Hash fallback: custom user-added categories get a stable hue derived
 *      from their normalized name. Same name → same color forever.
 *
 * Returned colors are tuned for dark backgrounds. Pair with [onCategoryColor]
 * when text needs to sit ON the color (auto-picks black/white for legibility).
 */
fun categoryColor(rawName: String?): Color {
    if (rawName.isNullOrBlank()) return DEFAULT_NEUTRAL
    val key = rawName.trim().lowercase()
    CURATED[key]?.let { return it }
    // Some near-misses we want to alias (so "Coffee" and "Coffee and refreshments"
    // pick the same hue even if one isn't in CURATED directly).
    for ((alias, color) in CURATED) {
        if (key.startsWith(alias) || alias.startsWith(key)) return color
    }
    // Hash fallback — stable across launches.
    val hue = ((key.hashCode().absoluteValue) % 360).toFloat()
    return Color.hsv(hue, saturation = 0.58f, value = 0.82f)
}

/**
 * Text color that reads well on top of [bg]. Returns near-black on light
 * backgrounds, white on dark. Threshold tuned for the palette below.
 */
fun onCategoryColor(bg: Color): Color {
    // Perceived luminance (Rec. 709 weights). Low compute, no allocations.
    val lum = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (lum > 0.55f) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
}

private val DEFAULT_NEUTRAL = Color(0xFF95918A)

private val CURATED: Map<String, Color> = mapOf(
    // ── Spending ─────────────────────────────────────────────────────────
    "food"                     to Color(0xFFFFA959), // warm orange
    "coffee"                   to Color(0xFFD4823F), // amber
    "coffee and refreshments"  to Color(0xFFD4823F),
    "groceries"                to Color(0xFFFFB04A),
    "shopping"                 to Color(0xFFE57BAA), // pink
    "entertainment"            to Color(0xFFB47BE5), // purple
    "transport"                to Color(0xFF6BA8E5), // sky blue
    "fuel"                     to Color(0xFF7B8FE5),
    "health"                   to Color(0xFF5FCFA4), // jade
    "medical"                  to Color(0xFF5FCFA4),
    "bills"                    to Color(0xFFE5C56B), // yellow
    "internet"                 to Color(0xFF5FB8C9), // teal
    "telecom"                  to Color(0xFF5FB8C9),
    "rent"                     to Color(0xFF8B95E3), // periwinkle
    "education"                to Color(0xFFE3A06B),
    "gifts"                    to Color(0xFFE57FB4),
    "donation"                 to Color(0xFF9CC68F),
    // ── Income / inflow ──────────────────────────────────────────────────
    "salary"                   to Color(0xFF82C8AA), // mint green
    "income"                   to Color(0xFF82C8AA),
    "refund"                   to Color(0xFF6FBF9A),
    // ── Transfers / neutral ──────────────────────────────────────────────
    "transfer"                 to Color(0xFF95918A),
    "internal transfer"        to Color(0xFF95918A),
    "cashout"                  to Color(0xFF8C8077),
    "atm"                      to Color(0xFF8C8077),
    "withdrawal"               to Color(0xFF8C8077),
    // ── Debt / personal money flows ──────────────────────────────────────
    "loan"                     to Color(0xFFE57373), // soft red
    "telebirr loan"            to Color(0xFFE57373),
    "debt"                     to Color(0xFFE57373),
    // ── Catch-alls ───────────────────────────────────────────────────────
    "fees"                     to Color(0xFFB89968),
    "other"                    to Color(0xFF95918A),
    "uncategorized"            to Color(0xFF6E6A65)
)
