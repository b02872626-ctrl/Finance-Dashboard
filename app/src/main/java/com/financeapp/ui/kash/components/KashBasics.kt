package com.financeapp.ui.kash.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashTheme
import com.financeapp.ui.kash.KashType
import java.util.Locale
import kotlin.math.abs

/** Money formatting rules from the design system: `1,250.00`, real − and + signs. */
object KashMoney {
    const val MINUS = "−"
    const val PLUS = "+"

    /** `1,250.00` — absolute value, two decimals, thousands separators. */
    fun amount(value: Double): String = String.format(Locale.US, "%,.2f", abs(value))

    /** `1,250` — absolute value, no decimals (chart labels, small totals). */
    fun whole(value: Double): String = String.format(Locale.US, "%,.0f", abs(value))

    fun sign(direction: KashDirection): String = when (direction) {
        KashDirection.In -> PLUS
        KashDirection.Out -> MINUS
        KashDirection.Transfer -> ""
    }

    fun signed(value: Double, direction: KashDirection): String = sign(direction) + amount(value)
}

/** Which way money moved. Own-account transfers are neutral and not counted. */
enum class KashDirection { In, Out, Transfer }

/**
 * The one big figure on a screen: `18,245` in [style] and `.00 ETB` in
 * figure-unit in [unitColor]. [hidden] shows dots instead (balance hidden).
 */
@Composable
fun KashFigure(
    value: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = KashType.FigureXl,
    color: Color = KashColors.OnSlate,
    unitColor: Color = KashColors.OnSlateMuted,
    sign: String = "",
    currency: String = "ETB",
    showDecimals: Boolean = true,
    hidden: Boolean = false,
) {
    val formatted = KashMoney.amount(value)
    val whole = formatted.substringBefore('.')
    val decimals = "." + formatted.substringAfter('.')
    val unitStyle = KashType.FigureUnit.toSpanStyle().copy(color = unitColor)
    Text(
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
        text = buildAnnotatedString {
            if (hidden) {
                withStyle(SpanStyle(color = color, letterSpacing = 0.08.em)) { append("••••••") }
                withStyle(unitStyle) { append(" $currency") }
            } else {
                withStyle(SpanStyle(color = color)) { append(sign + whole) }
                withStyle(unitStyle) {
                    if (showDecimals) append(decimals)
                    if (currency.isNotEmpty()) append(" $currency")
                }
            }
        },
        style = style,
    )
}

/** Placeholder bank mark until the official logos are supplied (BankBadge). */
data class KashBank(val code: String, val color: Color, val fullName: String, val shortName: String)

fun kashBank(bankName: String): KashBank {
    val n = bankName.lowercase(Locale.US)
    return when {
        n.contains("cbe birr") -> KashBank("CB", KashColors.BankCbe, "CBE Birr", "CBE Birr")
        n.contains("commercial bank") || n == "cbe" ->
            KashBank("CBE", KashColors.BankCbe, "Commercial Bank of Ethiopia", "CBE")
        n.contains("telebirr") -> KashBank("tb", KashColors.BankTelebirr, "Telebirr", "Telebirr")
        n.contains("abyssinia") || n == "boa" -> KashBank("BoA", KashColors.BankBoa, "Bank of Abyssinia", "BoA")
        else -> {
            // No placeholder colour in the system for other banks: muted ink, initials.
            val clean = bankName.removePrefix("Unknown (").removeSuffix(")")
            // "Dashen Bank" → DB, "Awash Bank" → AB (the same habit as BoA, CBE).
            val words = clean.split(' ').filter { it.isNotBlank() }
            val code = words.take(2).joinToString("") { it.take(1).uppercase(Locale.US) }.ifEmpty { "?" }
            KashBank(code, KashColors.InkMuted, clean, words.firstOrNull() ?: clean)
        }
    }
}

/** A dashed 1dp outline in [shape] (the "New category" chip). */
fun Modifier.dashedBorder(color: Color, shape: Shape, width: Dp = 1.dp, dash: Dp = 4.dp): Modifier =
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            color = color,
            style = Stroke(
                width = width.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())),
            ),
        )
    }

@Preview(name = "Figures", widthDp = 360)
@Composable
private fun FiguresPreview() = KashTheme {
    Column(
        Modifier.background(KashColors.Slate).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KashFigure(18245.0)
        KashFigure(18245.0, hidden = true)
        KashFigure(100.0, style = KashType.FigureL, sign = KashMoney.MINUS)
        Text(KashMoney.signed(160.0, KashDirection.In), color = KashColors.OnSlate, style = KashType.FigureS)
    }
}
