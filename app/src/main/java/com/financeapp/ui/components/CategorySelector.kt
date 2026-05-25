package com.financeapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.ui.theme.AccentBorder
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.AppSurfaceSecondary
import com.financeapp.ui.theme.BorderLight
import com.financeapp.ui.theme.CharcoalText
import com.financeapp.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(
    selectedCategory: String?,
    categories: List<String>,
    onCategorySelected: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Category"
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var customCategory by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedCategory.orEmpty(),
                onValueChange = {},
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                label = { Text(label) },
                placeholder = { Text("Select category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBorder,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = AppSurfaceSecondary,
                    unfocusedContainerColor = AppSurfaceSecondary,
                    focusedTextColor = CharcoalText,
                    unfocusedTextColor = CharcoalText,
                    cursorColor = AppBlue,
                    focusedLabelColor = AppBlue,
                    unfocusedLabelColor = TextMuted
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            expanded = false
                            onCategorySelected(category)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Add category") },
                    leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = null) },
                    onClick = {
                        expanded = false
                        showAddDialog = true
                    }
                )
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add category") },
                text = {
                    OutlinedTextField(
                        value = customCategory,
                        onValueChange = { customCategory = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Gym payment") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val normalized = TransactionCategoryCatalog.normalize(customCategory)
                            if (normalized.isNotBlank()) {
                                onAddCategory(normalized)
                                onCategorySelected(normalized)
                            }
                            customCategory = ""
                            showAddDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            customCategory = ""
                            showAddDialog = false
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
