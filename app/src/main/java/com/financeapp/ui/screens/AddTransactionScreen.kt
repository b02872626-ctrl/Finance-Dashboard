package com.financeapp.ui.screens

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.components.CategoryPill
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.CoralPrimary
import com.financeapp.ui.theme.PureWhite
import com.financeapp.ui.theme.pressScale
import com.financeapp.ui.theme.rememberHaptic
import com.financeapp.ui.theme.rememberTactileSource
import com.financeapp.viewmodel.AddTransactionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Mirror the local color tokens DashboardScreen uses (they're private there;
// keeping a parallel set here avoids leaking palette into the public theme).
private val CardBg     = Color(0xFF231E1A)
private val InnerDark  = Color(0xFF15110F)
private val Headline   = AppBlue
private val Coral      = CoralPrimary
private val BodyMuted  = Color(0xFFB7B3AC)
private val FooterGrey = Color(0xFF7C7872)

/**
 * Form for inserting a transaction the user typed in by hand — cash
 * spends, missed-SMS transfers, anything the parser can't see.
 *
 * State + persistence live in [AddTransactionViewModel]. The screen
 * watches `state.savedId`; the first non-null value fires [onSaved]
 * which closes the screen. Errors surface as a Toast.
 */
@Composable
fun AddTransactionScreen(
    vm: AddTransactionViewModel,
    onSaved: () -> Unit,
    onClose: () -> Unit
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val haptic = rememberHaptic()

    // Auto-close on successful save
    LaunchedEffect(state.savedId) {
        if (state.savedId != null) {
            Toast.makeText(context, "Transaction added", Toast.LENGTH_SHORT).show()
            onSaved()
        }
    }
    // Surface errors as Toast
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearError()
        }
    }

    Box(Modifier.fillMaxSize().background(AppBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Header(onClose = onClose) }

            // Type selector (Expense / Income / Transfer)
            item {
                TypeSegments(
                    selected = state.type,
                    onPick = {
                        haptic(com.financeapp.ui.theme.Haptic.TAP)
                        vm.setType(it)
                    }
                )
            }

            // Big amount input
            item {
                AmountInput(
                    value = state.amountText,
                    onChange = vm::setAmount,
                    accentColor = colorForType(state.type)
                )
            }

            // Counterparty, bank, date, note
            item {
                LabeledField(
                    label = if (state.type == TransactionType.CREDIT.name) "From"
                            else "To",
                    value = state.counterparty,
                    placeholder = "Who paid / who got paid",
                    onChange = vm::setCounterparty
                )
            }
            item {
                LabeledField(
                    label = "Account / Bank",
                    value = state.bankName,
                    placeholder = "Cash, CBE, Telebirr…",
                    onChange = vm::setBank
                )
            }
            item {
                DatePickerRow(
                    dateTimeMs = state.dateTimeMs,
                    onPick = vm::setDateTime
                )
            }
            item {
                CategoryRow(
                    current = state.category,
                    onPick = vm::setCategory
                )
            }
            item {
                LabeledField(
                    label = "Note",
                    value = state.note,
                    placeholder = "Optional",
                    onChange = vm::setNote
                )
            }

            // Save button — sticky-feeling spacing, not actually sticky to
            // avoid awkward keyboard interactions.
            item { Spacer(Modifier.height(12.dp)) }
            item {
                SaveButton(
                    enabled = state.canSave,
                    saving  = state.saving,
                    onClick = {
                        haptic(com.financeapp.ui.theme.Haptic.CONFIRM)
                        vm.save()
                    }
                )
            }
        }
    }
}

// ───────────────────────────── Sections ─────────────────────────────

@Composable
private fun Header(onClose: () -> Unit) {
    val closeIx = rememberTactileSource()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .pressScale(closeIx)
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg)
                .clickable(
                    interactionSource = closeIx,
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Headline,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            "Add transaction",
            color = Headline, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
        )
        // Phantom 44dp box to keep title visually centered without using
        // an explicit weight layout.
        Spacer(Modifier.size(44.dp))
    }
}

@Composable
private fun TypeSegments(selected: String, onPick: (String) -> Unit) {
    val options = listOf(
        TransactionType.DEBIT.name        to "Expense",
        TransactionType.CREDIT.name       to "Income",
        TransactionType.TRANSFER_OUT.name  to "Transfer"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val active = selected == value
            val ix = rememberTactileSource()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pressScale(ix)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) colorForType(value) else Color.Transparent)
                    .clickable(
                        interactionSource = ix,
                        indication = null,
                        onClick = { onPick(value) }
                    )
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (active) PureWhite else BodyMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AmountInput(value: String, onChange: (String) -> Unit, accentColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardBg)
            .padding(horizontal = 22.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Amount", color = BodyMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "ETB ",
                color = accentColor,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            BasicTextField(
                value = value,
                onValueChange = { raw ->
                    // Allow only digits + one decimal separator
                    val cleaned = raw.filter { it.isDigit() || it == '.' }
                    onChange(cleaned)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(
                    color = accentColor,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(accentColor),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "0.00",
                                color = BodyMuted.copy(alpha = 0.4f),
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(label, color = BodyMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Headline,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Coral),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            color = FooterGrey,
                            fontSize = 16.sp
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DatePickerRow(dateTimeMs: Long, onPick: (Long) -> Unit) {
    val context = LocalContext.current
    val ix = rememberTactileSource()
    val fmt = remember { SimpleDateFormat("EEE, MMM d • HH:mm", Locale.US) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(ix)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .clickable(
                interactionSource = ix,
                indication = null,
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = dateTimeMs }
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            cal.set(Calendar.YEAR, y)
                            cal.set(Calendar.MONTH, m)
                            cal.set(Calendar.DAY_OF_MONTH, d)
                            onPick(cal.timeInMillis)
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Date", color = BodyMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                fmt.format(Date(dateTimeMs)),
                color = Headline, fontSize = 15.sp, fontWeight = FontWeight.Medium
            )
        }
        Text("📅", fontSize = 20.sp)
    }
}

@Composable
private fun CategoryRow(current: String?, onPick: (String?) -> Unit) {
    // Lightweight: tap a chip from the standard catalog. For v1 we use
    // a horizontal-wrap of the most-common categories rather than the
    // full picker sheet — that sheet is heavy and overkill for inline.
    val popular = listOf(
        "Food", "Coffee and refreshments", "Internet", "Transport",
        "Rent", "Salary", "Loan", "Internal transfer", "Other"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text("Category (optional)", color = BodyMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        // Simple wrap-row using FlowRow would be ideal; for now a 2-row
        // grid of pills works at most screen sizes.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            popular.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { cat ->
                        val active = current.equals(cat, ignoreCase = true)
                        val ix = rememberTactileSource()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pressScale(ix)
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (active) Coral else InnerDark)
                                .border(
                                    width = 1.dp,
                                    color = if (active) Coral else Color(0x33FFFFFF),
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .clickable(
                                    interactionSource = ix,
                                    indication = null,
                                    onClick = { onPick(if (active) null else cat) }
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                cat,
                                color = if (active) PureWhite else Headline,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                    // Pad row to 3 cells when the last row is short
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // Show selection echo as a pill for parity with the row UI
        current?.let {
            Spacer(Modifier.height(10.dp))
            CategoryPill(category = it)
        }
    }
}

@Composable
private fun SaveButton(enabled: Boolean, saving: Boolean, onClick: () -> Unit) {
    val ix = rememberTactileSource()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(ix)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Coral else Coral.copy(alpha = 0.35f))
            .clickable(
                enabled = enabled,
                interactionSource = ix,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (saving) "Saving…" else "Save transaction",
            color = PureWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ───────────────────────────── Helpers ─────────────────────────────

private fun colorForType(type: String): Color = when (type) {
    TransactionType.CREDIT.name -> Color(0xFF82C8AA) // mint income
    else                        -> Coral
}
