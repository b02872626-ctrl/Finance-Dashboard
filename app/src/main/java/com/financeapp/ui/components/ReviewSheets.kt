package com.financeapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.data.model.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Palette (shared with ReviewQueueScreen) ────────────────────────────────
private val CardBg       = Color(0xFF231E1A)
private val CardSoft     = Color(0xFF302A25)
private val BorderCol    = Color(0xFF353535)
private val TextPrimary  = Color(0xFFF5F2EB)
private val TextMute     = Color(0xFFB7B3AC)
private val Brown        = Color(0xFFDCC8AF)
private val Mint         = Color(0xFF82C8AA)
private val MintBannerBg = Color(0x1A82C8AA)
private val Backdrop     = Color(0xA6000000)
private val Red          = Color(0xFFFF6E6E)
private val Green        = Color(0xFF78D296)
private val RawBg        = Color(0xFF221D18)
private val RawText      = Color(0xFFC8BEAF)
private val MonoFont     = FontFamily.Monospace

// ─── Shared sheet shell ─────────────────────────────────────────────────────
@Composable
private fun SheetShell(
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Backdrop)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onClose
            )
    )
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .align()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(CardBg)
            .border(
                width = 1.dp, color = BorderCol,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = {} // swallow taps inside sheet
            )
    ) {
        // grab handle
        Box(
            Modifier
                .padding(top = 10.dp, bottom = 4.dp)
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF463E34))
        )
        content()
    }
}

// Force the sheet to the bottom regardless of caller layout.
@Composable
private fun Modifier.align(): Modifier =
    this.then(
        Modifier
            .wrapContentHeight(align = Alignment.Bottom)
    )

// ─── Category picker bottom sheet ───────────────────────────────────────────
@Composable
fun CategoryPickerSheet(
    merchant: String,
    currentCategory: String?,
    categories: List<String>,
    applySimilar: Boolean,
    onApplySimilarChange: (Boolean) -> Unit,
    onPick: (String) -> Unit,
    onAddCustom: (String) -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showAddCustomDialog by remember { mutableStateOf(false) }
    val filtered = remember(query, categories) {
        if (query.isBlank()) categories
        else categories.filter { it.contains(query, ignoreCase = true) }
    }
    val firstWord = merchant.split(' ').firstOrNull() ?: merchant
    val recent = listOf("Food", "Transport", "Transfer").filter { it in categories }

    // Custom-category input dialog. Opens when user taps the "Add custom"
    // row OR submits an empty search. Closes on save/cancel/back.
    if (showAddCustomDialog) {
        AddCustomCategoryDialog(
            initial = query.trim(),
            existingCategories = categories,
            onSave = { name ->
                showAddCustomDialog = false
                if (name.isNotBlank()) {
                    onAddCustom(name)
                    onPick(name) // category-create + immediate-select
                }
            },
            onCancel = { showAddCustomDialog = false }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Backdrop)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onClose
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(CardBg)
                .border(
                    width = 1.dp, color = BorderCol,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = {}
                )
        ) {
            // Handle
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF463E34))
            )
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 14.dp, top = 6.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Categorize", color = TextMute, fontSize = 12.5.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(merchant, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                CloseButton(onClose)
            }
            // Search
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSoft)
                    .border(1.dp, BorderCol, RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔍 ", color = Brown, fontSize = 13.sp)
                BasicTextField(
                    value = query, onValueChange = { query = it },
                    textStyle = LocalTextStyle.current.copy(color = TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(TextPrimary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search categories", color = TextMute, fontSize = 14.sp)
                        inner()
                    }
                )
            }
            Spacer(Modifier.height(8.dp))

            // List
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (query.isBlank()) {
                    if (recent.isNotEmpty()) {
                        item {
                            Text("Recent", color = TextMute, fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                recent.forEach { c ->
                                    Text(
                                        c,
                                        color = if (currentCategory == c) Color(0xFF231E1A) else TextPrimary,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(if (currentCategory == c) TextPrimary else CardBg)
                                            .border(1.dp, BorderCol, RoundedCornerShape(50))
                                            .clickable { onPick(c) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }

                    TransactionCategoryCatalog.sections.forEach { (groupLabel, groupCats) ->
                        val visible = groupCats.filter { it in categories }
                        if (visible.isNotEmpty()) {
                            item {
                                Text(
                                    groupLabel,
                                    color = TextMute, fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 8.dp)
                                )
                            }
                            items(visible) { c ->
                                CategoryRow(
                                    label = c, selected = currentCategory == c,
                                    onClick = { onPick(c) }
                                )
                            }
                        }
                    }

                    // Any custom categories outside the sections
                    val sectionedSet = TransactionCategoryCatalog.sections.flatMap { it.second }.toSet()
                    val custom = categories.filter { it !in sectionedSet }
                    if (custom.isNotEmpty()) {
                        item {
                            Text(
                                "Custom",
                                color = TextMute, fontSize = 12.sp,
                                modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 8.dp)
                            )
                        }
                        items(custom) { c ->
                            CategoryRow(label = c, selected = currentCategory == c, onClick = { onPick(c) })
                        }
                    }
                } else {
                    items(filtered) { c ->
                        CategoryRow(label = c, selected = currentCategory == c, onClick = { onPick(c) })
                    }
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    AddCustomRow(query = query, onAdd = {
                        // Always open the dialog — pre-fill from search if
                        // the user typed something, otherwise start blank.
                        showAddCustomDialog = true
                    })
                }
            }

            // Apply-to-similar at the bottom
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, BorderCol, RoundedCornerShape(0.dp))
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Apply to similar transactions", color = TextPrimary, fontSize = 13.5.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("Updates rule for $firstWord", color = TextMute, fontSize = 11.5.sp)
                }
                Toggle(on = applySimilar, onToggle = { onApplySimilarChange(!applySimilar) })
            }
        }
    }
}

@Composable
private fun CategoryRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) TextPrimary else CardBg)
            .border(1.dp, if (selected) TextPrimary else BorderCol, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) CardBg else CardSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label.first().uppercase(),
                color = if (selected) TextPrimary else Brown,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
        }
        Text(
            label,
            color = if (selected) Color(0xFF231E1A) else TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AddCustomRow(query: String, onAdd: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BorderCol, RoundedCornerShape(16.dp))
            .clickable(onClick = onAdd)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardSoft),
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Brown, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            if (query.isBlank()) "Add custom category" else "Add \"$query\"",
            color = TextPrimary, fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            "income · expense · neutral",
            color = TextMute, fontSize = 11.sp
        )
    }
}

@Composable
private fun Toggle(on: Boolean, onToggle: () -> Unit) {
    val bg = if (on) Mint else Color(0xFF40382E)
    Box(
        Modifier
            .width(36.dp)
            .height(22.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onToggle),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .padding(horizontal = 2.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color(0xFFDCD4C8))
        )
    }
}

// ─── Correction sheet (long-press) ──────────────────────────────────────────
@Composable
fun CorrectionSheet(
    tx: TransactionEntity,
    onPickCat: () -> Unit,
    onNotATransaction: () -> Unit,
    onClose: () -> Unit
) {
    // Parsers store amounts as positive magnitudes; direction lives in `type`.
    // Using `amount > 0` would label every txn as INCOME — see audit BLOCKER.
    val isIncome = tx.type == com.financeapp.parsing.TransactionType.CREDIT.name
    val timeLabel = remember(tx.dateTime) {
        SimpleDateFormat("HH:mm · MMM d", Locale.US).format(Date(tx.dateTime))
    }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Backdrop)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = onClose
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(CardBg)
                .border(
                    width = 1.dp, color = BorderCol,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = {}
                )
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF463E34))
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 14.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit transaction", color = TextMute, fontSize = 12.5.sp)
                CloseButton(onClose)
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Text("Amount", color = TextMute, fontSize = 11.5.sp, modifier = Modifier.padding(start = 6.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp)) {
                    Text(
                        (if (isIncome) "+" else "−") +
                            String.format(Locale.US, "%,.2f", kotlin.math.abs(tx.amount)),
                        color = if (isIncome) Green else Red,
                        fontSize = 32.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text("ETB", color = TextMute, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(14.dp))

                FieldRow(label = "Merchant", value = tx.counterparty ?: "—")
                FieldRow(label = "Source",   value = tx.bankName.ifBlank { tx.sender })
                FieldRow(label = "Time",     value = timeLabel)
                FieldRow(
                    label = "Category",
                    value = tx.category ?: tx.predictedCategory ?: "Uncategorized",
                    action = "Change ›",
                    onClick = onPickCat
                )

                Spacer(Modifier.height(14.dp))
                Text("Raw SMS", color = TextMute, fontSize = 11.5.sp, modifier = Modifier.padding(start = 6.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    tx.rawBody,
                    color = RawText, fontSize = 11.5.sp,
                    fontFamily = MonoFont, lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(RawBg)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            // Actions
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBg)
                        .border(1.dp, BorderCol, RoundedCornerShape(14.dp))
                        .clickable(onClick = onNotATransaction)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Not a transaction", color = TextPrimary, fontSize = 14.sp)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TextPrimary)
                        .clickable(onClick = onPickCat)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Change category", color = Color(0xFF231E1A), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String, action: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(width = 0.5.dp, color = Color(0x14FFFFFF), shape = RoundedCornerShape(0.dp))
            .padding(horizontal = 6.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMute, fontSize = 12.5.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, color = TextPrimary, fontSize = 14.sp)
            if (action != null) {
                Text(action, color = Brown, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(50))
            .background(CardSoft)
            .border(1.dp, BorderCol, RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("✕", color = Brown, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ─── Add custom category dialog ─────────────────────────────────────────────
/**
 * Text-input AlertDialog for creating a new category from the picker. The
 * keyboard auto-focuses and IME's Done key submits, so the user can type +
 * Enter without leaving the keyboard.
 */
@Composable
private fun AddCustomCategoryDialog(
    initial: String,
    existingCategories: List<String>,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var input by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val trimmed = input.trim()
    val isDuplicate = trimmed.isNotEmpty() &&
        existingCategories.any { it.equals(trimmed, ignoreCase = true) }
    val canSave = trimmed.isNotEmpty() && !isDuplicate

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = CardBg,
        title = {
            Text(
                "New category",
                color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    "Pick a short, descriptive name. Used to group your transactions.",
                    color = TextMute, fontSize = 13.sp, lineHeight = 18.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardSoft)
                        .border(
                            width = 1.dp,
                            color = if (isDuplicate) Red else BorderCol,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(TextPrimary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { if (canSave) onSave(trimmed) }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text("e.g. Subscriptions", color = TextMute, fontSize = 15.sp)
                            }
                            inner()
                        }
                    )
                }
                if (isDuplicate) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "A category with that name already exists.",
                        color = Red, fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canSave) onSave(trimmed) },
                enabled = canSave,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (canSave) Mint else TextMute
                )
            ) {
                Text("Create", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                colors = ButtonDefaults.textButtonColors(contentColor = TextMute)
            ) {
                Text("Cancel", fontSize = 14.sp)
            }
        }
    )
}
