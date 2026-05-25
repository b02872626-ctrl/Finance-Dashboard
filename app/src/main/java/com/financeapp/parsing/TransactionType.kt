package com.financeapp.parsing

enum class TransactionType(val label: String) {
    CREDIT("Credit"),
    DEBIT("Debit"),
    TRANSFER_OUT("Transfer Out"),
    PAYMENT("Payment"),
    UNKNOWN("Unknown")
}
