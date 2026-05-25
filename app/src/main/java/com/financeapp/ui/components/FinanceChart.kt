package com.financeapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.data.db.MonthlyAggregate
import com.financeapp.ui.theme.AccentFill
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.BorderLight
import com.financeapp.ui.theme.CharcoalText
import com.financeapp.ui.theme.GreenIncome
import com.financeapp.ui.theme.RedExpense
import com.financeapp.ui.theme.TextMuted
import com.financeapp.ui.theme.TextSecondary

@Composable
fun MonthlyBarChart(
    data: List<MonthlyAggregate>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { maxOf(it.totalIncome, it.totalExpense) }.coerceAtLeast(1.0)

    var isLoaded by remember { mutableStateOf(false) }
    val animProgress by animateFloatAsState(
        targetValue = if (isLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "chartReveal"
    )
    LaunchedEffect(Unit) { isLoaded = true }

    var touchX by remember { mutableStateOf<Float?>(null) }
    var chartWidth by remember { mutableStateOf(0f) }

    val selectedIndex = touchX?.let { x ->
        if (chartWidth > 0) {
            val groupW = chartWidth / data.size
            (x / groupW).toInt().coerceIn(0, data.size - 1)
        } else null
    }

    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(36.dp), contentAlignment = Alignment.Center) {
            if (selectedIndex != null) {
                val item = data[selectedIndex]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.month,
                        style = MaterialTheme.typography.labelMedium,
                        color = CharcoalText,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatAmount(item.totalIncome),
                        color = GreenIncome,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        formatAmount(item.totalExpense),
                        color = RedExpense,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(AppBlue)
                    Spacer(Modifier.size(6.dp))
                    Text("Income", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.size(17.dp))
                    LegendDot(RedExpense)
                    Spacer(Modifier.size(6.dp))
                    Text("Expense", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .pointerInput(data) {
                    detectTapGestures(
                        onPress = { offset ->
                            touchX = offset.x
                            tryAwaitRelease()
                            touchX = null
                        }
                    )
                }
                .pointerInput(data) {
                    detectDragGestures(
                        onDragStart = { offset -> touchX = offset.x },
                        onDragEnd = { touchX = null },
                        onDragCancel = { touchX = null },
                        onDrag = { change, _ -> touchX = change.position.x }
                    )
                }
        ) {
            chartWidth = size.width
            val groupW = size.width / data.size
            val baseBarWidth = (groupW * 0.26f).coerceAtMost(18f)
            val usableHeight = size.height - 12.dp.toPx()
            val corner = CornerRadius(10.dp.toPx(), 10.dp.toPx())

            repeat(4) { index ->
                val y = usableHeight * (index / 3f)
                drawLine(
                    color = BorderLight,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            data.forEachIndexed { i, item ->
                val isSelected = selectedIndex == i
                val alpha = if (selectedIndex == null || isSelected) 1f else 0.35f
                val cx = i * groupW + groupW / 2f
                val incH = (item.totalIncome / maxValue * usableHeight).toFloat() * animProgress
                val expH = (item.totalExpense / maxValue * usableHeight).toFloat() * animProgress

                drawRoundRect(
                    color = AppBlue.copy(alpha = alpha),
                    topLeft = Offset(cx - baseBarWidth - 2.dp.toPx(), usableHeight - incH),
                    size = Size(baseBarWidth, incH.coerceAtLeast(3.dp.toPx())),
                    cornerRadius = corner
                )

                drawRoundRect(
                    color = RedExpense.copy(alpha = alpha),
                    topLeft = Offset(cx + 2.dp.toPx(), usableHeight - expH),
                    size = Size(baseBarWidth, expH.coerceAtLeast(3.dp.toPx())),
                    cornerRadius = corner
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            data.forEachIndexed { index, item ->
                val isSelected = selectedIndex == index
                Text(
                    text = item.month,
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) CharcoalText else TextMuted
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color) {
    Canvas(Modifier.size(8.dp)) { drawCircle(color) }
}
