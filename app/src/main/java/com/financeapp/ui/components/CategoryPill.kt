package com.financeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.ui.theme.categoryColor
import com.financeapp.ui.theme.onCategoryColor

/**
 * Tiny inline pill that labels a transaction with its user-confirmed
 * category. Color is derived from the category name via [categoryColor]
 * so scannable groups share a hue (Food/Coffee → orange family, Salary
 * → mint, Transfer → grey, etc.).
 *
 * Background uses the category color at low alpha (subtle tint that
 * doesn't compete with the row content), text uses the full-saturation
 * version of the same color so the label still pops. This keeps the
 * pill readable on the dark transaction row.
 *
 * @param category   user-set category string (e.g. "Coffee and refreshments")
 * @param maxLines   safety cap for long category names — defaults to 1
 */
@Composable
fun CategoryPill(
    category: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    val accent = categoryColor(category)
    Text(
        text = category,
        color = accent,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/**
 * Larger variant for the transaction-detail hero card. Solid fill (not
 * tinted), white-or-black text per [onCategoryColor]. Reads as the row's
 * dominant identity, not an accent.
 */
@Composable
fun CategoryPillBold(
    category: String,
    modifier: Modifier = Modifier
) {
    val accent = categoryColor(category)
    Text(
        text = category,
        color = onCategoryColor(accent),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}
