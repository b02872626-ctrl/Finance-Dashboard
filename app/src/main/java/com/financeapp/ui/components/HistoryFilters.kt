package com.financeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.R
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.CoralPrimary

// =============================================================================
// Shared history controls — used by the History tab and the per-account
// screen so search + period filters look and behave the same in both.
// =============================================================================

private val InnerDark  = Color(0xFF15110F)
private val FooterGrey = Color(0xFF7C7872)

/** Quick period filters for transaction history (NAH-7). */
enum class HistoryPeriod(val label: String, val days: Int?) {
    WEEK("1W", 7),
    MONTH("1M", 30),
    YEAR("1Y", 365),
    ALL("All time", null);

    /** Inclusive lower bound in epoch millis; 0 for [ALL]. */
    fun fromMs(now: Long = System.currentTimeMillis()): Long =
        days?.let { now - it * 86_400_000L } ?: 0L
}

@Composable
fun HistorySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(InnerDark)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_search_dark),
            contentDescription = "Search",
            tint = CoralPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = AppBlue, fontSize = 13.sp),
            cursorBrush = SolidColor(CoralPrimary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(text = placeholder, color = FooterGrey, fontSize = 13.sp)
                }
                inner()
            }
        )
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = FooterGrey,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onQueryChange("") }
                    )
            )
        }
    }
}

/**
 * 1W / 1M / 1Y / All chips. [selected] may be null when a custom date
 * range (calendar picker) is active — then no chip is highlighted.
 */
@Composable
fun PeriodChips(
    selected: HistoryPeriod?,
    onSelect: (HistoryPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryPeriod.values().forEach { period ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) CoralPrimary.copy(alpha = 0.15f) else Color.Transparent)
                    .border(1.dp, if (isSelected) CoralPrimary else Color(0x33FFFFFF), RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(period) }
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = period.label,
                    color = if (isSelected) CoralPrimary else AppBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
