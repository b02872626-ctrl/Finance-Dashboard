package com.financeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tiny inline pill that labels a transaction with its user-confirmed
 * category. Designed to sit on the subtitle line of a transaction row,
 * next to the bank name, without competing for space:
 *
 *   Beselam Melisew                 -100.00
 *   CBE  •  [Coffee and refreshments]
 *
 * Caller is responsible for the separator dot. The pill itself is just
 * the rounded label.
 *
 * @param category   user-set category string (e.g. "Coffee and refreshments")
 * @param maxLines   safety cap for long category names — defaults to 1
 *                   (the row is already wide-constrained; longer names
 *                   ellipsize gracefully).
 */
@Composable
fun CategoryPill(
    category: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    val pillBg   = Color(0xFF2E2821) // sits between CardBg and InnerDark
    val pillText = Color(0xFFDFD6CD) // muted Headline — readable but recedes
    Text(
        text = category,
        color = pillText,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(pillBg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
