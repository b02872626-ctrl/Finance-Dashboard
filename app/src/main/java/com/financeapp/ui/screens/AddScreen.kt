package com.financeapp.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.financeapp.parsing.TransactionType
import com.financeapp.ui.kash.KashColors
import com.financeapp.ui.kash.KashShapes
import com.financeapp.ui.kash.KashSize
import com.financeapp.ui.kash.KashSystemBars
import com.financeapp.ui.kash.KashType
import com.financeapp.ui.kash.components.KashButton
import com.financeapp.ui.kash.components.KashChip
import com.financeapp.ui.kash.components.KashScreen
import com.financeapp.ui.kash.components.KashTextField
import com.financeapp.ui.kash.components.KashTopBar
import com.financeapp.ui.kash.kashClickable
import com.financeapp.ui.kash.num
import com.financeapp.util.KashDates
import com.financeapp.util.LocalCalendarSystem
import com.financeapp.viewmodel.AddTransactionViewModel
import java.util.Calendar

/**
 * Add transaction (AppAdd, AppAddError). Category chips and banks come from
 * the real lists; saving uses AddTransactionViewModel unchanged.
 */
@Composable
fun AddScreen(
    vm: AddTransactionViewModel,
    banks: List<String>,
    customCategories: List<String>,
    recentCategories: List<String>,
    onAddCategory: (String) -> Unit,
    onSaved: () -> Unit,
    onClose: () -> Unit,
) {
    KashSystemBars(darkIcons = false)
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val cal = LocalCalendarSystem.current
    var triedSave by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var newCategoryOpen by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (state.bankName.isBlank()) vm.setBank("Cash")
        runCatching { amountFocus.requestFocus() }
    }
    LaunchedEffect(state.savedId) {
        if (state.savedId != null) {
            Toast.makeText(context, "Transaction added", Toast.LENGTH_SHORT).show()
            onSaved()
        }
    }

    val isIncome = state.type == TransactionType.CREDIT.name
    val nameError = if (triedSave && state.counterparty.isBlank()) {
        if (isIncome) "Add who paid you so you can find it later." else "Add who you paid so you can find it later."
    } else null

    KashScreen(
        heroGap = 18.dp,
        heroBottom = 30.dp,
        hero = {
            KashTopBar("Add transaction", onBack = onClose)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    TransactionType.DEBIT.name to "Expense",
                    TransactionType.CREDIT.name to "Income",
                    TransactionType.TRANSFER_OUT.name to "Transfer",
                ).forEach { (type, label) -> KashChip(label, state.type == type, { vm.setType(type) }, onSlate = true) }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) {
                    if (state.amountText.isEmpty()) Text("0", style = KashType.FigureXl, color = KashColors.OnSlateMuted)
                    BasicTextField(
                        value = state.amountText,
                        onValueChange = { t -> vm.setAmount(t.filter { it.isDigit() || it == '.' }.take(12)) },
                        singleLine = true,
                        textStyle = KashType.FigureXl.copy(color = KashColors.OnSlate),
                        cursorBrush = SolidColor(KashColors.Sand),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(amountFocus)
                            .semantics { contentDescription = "Amount in ETB" },
                    )
                }
                Text("ETB", style = KashType.FigureUnit, color = KashColors.OnSlateMuted, modifier = Modifier.padding(bottom = 6.dp))
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().imePadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                KashTextField(
                    value = state.counterparty,
                    onValueChange = vm::setCounterparty,
                    label = when (state.type) {
                        TransactionType.CREDIT.name -> "Who paid you"
                        TransactionType.TRANSFER_OUT.name -> "Sent to"
                        else -> "Who got paid"
                    },
                    placeholder = if (isIncome) "e.g. Afro Trading PLC" else "e.g. Shoa Supermarket",
                    error = nameError,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (isIncome) "Paid into" else "Paid from", style = KashType.Label, color = KashColors.InkMuted)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("Cash") + banks).distinct().forEach { b ->
                            KashChip(b, state.bankName.equals(b, true), { vm.setBank(b) })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldButton("When", KashDates.whenShort(state.dateTimeMs, cal), Modifier.weight(1f)) {
                        pickDateTime(context, state.dateTimeMs, vm::setDateTime)
                    }
                    FieldButton("Category", state.category ?: "Optional", Modifier.weight(1f), muted = state.category == null) {
                        pickerOpen = true
                    }
                }
                KashTextField(state.note, vm::setNote, "Note", placeholder = "Optional", imeAction = androidx.compose.ui.text.input.ImeAction.Done)
                state.errorMessage?.let { Text(it, style = KashType.Caption, color = KashColors.MoneyOut) }
            }
            KashButton(
                if (state.saving) "Saving…" else "Save transaction",
                {
                    triedSave = true
                    if (state.canSave) vm.save()
                },
                Modifier.fillMaxWidth().padding(pad),
                busy = state.saving,
                enabled = state.amount?.let { it > 0 } == true,
            )
        }
    }

    if (pickerOpen) {
        CategoryPicker(
            tx = null,
            initial = state.category,
            recent = recentCategories,
            custom = customCategories,
            applyToSimilar = false,
            onToggleApplyToSimilar = null,
            onClose = { pickerOpen = false },
            onNewCategory = { newCategoryOpen = true },
            onUse = { vm.setCategory(it); pickerOpen = false },
            title = "Category",
        )
    }
    if (newCategoryOpen) {
        NewCategoryDialog(
            onCancel = { newCategoryOpen = false },
            onCreate = { name -> newCategoryOpen = false; pickerOpen = false; onAddCategory(name); vm.setCategory(name) },
        )
    }
}

/** A field-shaped button with a label above (When, Category). */
@Composable
private fun FieldButton(label: String, value: String, modifier: Modifier, muted: Boolean = false, onClick: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = KashType.Label, color = KashColors.InkMuted)
        Box(
            Modifier
                .kashClickable(onClickLabel = "Change $label", onClick = onClick)
                .fillMaxWidth()
                .height(KashSize.FieldH)
                .clip(KashShapes.Field)
                .background(KashColors.SurfaceRaised)
                .border(1.dp, KashColors.Line, KashShapes.Field)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                value, style = KashType.BodyL.num(), color = if (muted) KashColors.InkMuted else KashColors.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** System date then time pickers, starting from [current]. */
private fun pickDateTime(context: android.content.Context, current: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = current }
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, min ->
            onPicked(Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}
