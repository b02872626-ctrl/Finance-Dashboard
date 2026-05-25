package com.financeapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.financeapp.data.model.TransactionEntity
import com.financeapp.ui.components.CategorySelector
import com.financeapp.ui.components.TransactionItem
import com.financeapp.ui.components.formatAmount
import com.financeapp.ui.theme.AppBackground
import com.financeapp.ui.theme.AppBlue
import com.financeapp.ui.theme.AppSurface
import com.financeapp.ui.theme.BorderLight
import com.financeapp.ui.theme.CharcoalText
import com.financeapp.ui.theme.TextSecondary
import com.financeapp.viewmodel.BulkCategorySuggestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizeTransactionsScreen(
    userName: String,
    transactions: List<TransactionEntity>,
    categories: List<String>,
    bulkSuggestion: BulkCategorySuggestion?,
    onBack: () -> Unit,
    onTxClick: (Long) -> Unit,
    onCategorySelected: (Long, String) -> Unit,
    onAddCategory: (String) -> Unit,
    onApplyBulkSuggestion: () -> Unit,
    onDismissBulkSuggestion: () -> Unit
) {
    if (bulkSuggestion != null) {
        AlertDialog(
            onDismissRequest = onDismissBulkSuggestion,
            title = { Text("Categorize repeated customer?") },
            text = {
                Text(
                    "You've sent ${formatAmount(bulkSuggestion.totalAmount)} to ${bulkSuggestion.counterparty} in ${bulkSuggestion.transactionCount} transactions. Should I categorize all of them as ${bulkSuggestion.category}?"
                )
            },
            confirmButton = {
                TextButton(onClick = onApplyBulkSuggestion) {
                    Text("Categorize all")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissBulkSuggestion) {
                    Text("Only this one")
                }
            }
        )
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Categorize Today", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground,
                    titleContentColor = CharcoalText,
                    navigationIconContentColor = CharcoalText
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground)
                .padding(padding),
            contentPadding = PaddingValues(start = 11.dp, end = 11.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(17.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface),
                    border = BorderStroke(1.dp, BorderLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Hey ${userName.ifBlank { "there" }}, let's sort today's spending.",
                            style = MaterialTheme.typography.titleLarge,
                            color = CharcoalText
                        )
                        Text(
                            text = when (transactions.size) {
                                0 -> "Everything for today is already categorized."
                                1 -> "You have 1 transaction waiting for a category."
                                else -> "You have ${transactions.size} transactions waiting for a category."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Text(
                            text = "You're all caught up for today.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                items(transactions, key = { it.id }) { tx ->
                    Card(
                        shape = RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(containerColor = AppSurface),
                        border = BorderStroke(1.dp, BorderLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TransactionItem(
                                tx = tx,
                                onClick = { onTxClick(tx.id) },
                                showDivider = false
                            )
                            CategorySelector(
                                selectedCategory = tx.category,
                                categories = categories,
                                onCategorySelected = { onCategorySelected(tx.id, it) },
                                onAddCategory = onAddCategory
                            )
                            TextButton(
                                onClick = { onTxClick(tx.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open details", color = AppBlue)
                            }
                        }
                    }
                }
            }
        }
    }
}
