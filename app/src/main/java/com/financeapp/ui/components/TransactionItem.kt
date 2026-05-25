package com.financeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.theme.AccentFill
import com.financeapp.ui.theme.AmberBg
import com.financeapp.ui.theme.AmberTransfer
import com.financeapp.ui.theme.BorderLight
import com.financeapp.ui.theme.CharcoalText
import com.financeapp.ui.theme.GreenBg
import com.financeapp.ui.theme.GreenIncome
import com.financeapp.ui.theme.MonoFontFamily
import com.financeapp.ui.theme.MutedGray
import com.financeapp.ui.theme.PurpleBg
import com.financeapp.ui.theme.PurplePayment
import com.financeapp.ui.theme.RedBg
import com.financeapp.ui.theme.RedExpense
import com.financeapp.ui.theme.TextMuted
import com.financeapp.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TxTypeStyle(val color: Color, val bg: Color, val label: String, val sign: String, val icon: ImageVector)

fun typeStyle(type: String): TxTypeStyle = when (type) {
    TransactionType.CREDIT.name -> TxTypeStyle(GreenIncome, GreenBg, "Credit", "+", Icons.Default.ArrowDownward)
    TransactionType.DEBIT.name -> TxTypeStyle(RedExpense, RedBg, "Debit", "-", Icons.Default.ArrowUpward)
    TransactionType.TRANSFER_OUT.name -> TxTypeStyle(AmberTransfer, AmberBg, "Transfer", "-", Icons.Default.SwapHoriz)
    TransactionType.PAYMENT.name -> TxTypeStyle(PurplePayment, PurpleBg, "Payment", "-", Icons.Default.ArrowUpward)
    else -> TxTypeStyle(MutedGray, AccentFill, "Unknown", "", Icons.Default.HelpOutline)
}

@Composable
fun TransactionItem(
    tx: TransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTime: Boolean = true,
    showDivider: Boolean = true
) {
    val style = typeStyle(tx.type)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(style.bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = style.label,
                    tint = style.color,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.size(10.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = tx.counterparty ?: tx.bankName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    color = CharcoalText
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(style.bg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = style.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = style.color
                        )
                    }
                    if (showTime) {
                        Text(
                            text = formatTime(tx.dateTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontFamily = MonoFontFamily
                        )
                    }
                }
                tx.category?.takeIf { it.isNotBlank() }?.let { category ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(AccentFill)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = CharcoalText
                        )
                    }
                }
            }

            Spacer(Modifier.size(8.dp))

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${style.sign}${formatAmount(tx.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = style.color,
                    fontFamily = MonoFontFamily
                )
                if (tx.balance != null) {
                    Text(
                        text = "Bal ${formatAmount(tx.balance)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(color = BorderLight)
        }
    }
}

fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
