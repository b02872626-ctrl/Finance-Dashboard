package com.financeapp.parsing

enum class TransactionType(val label: String) {
    CREDIT("Credit"),
    DEBIT("Debit"),
    TRANSFER_OUT("Transfer Out"),
    PAYMENT("Payment"),
    /**
     * Money moving between the user's own accounts (e.g. Telebirr ↔ CBE).
     * Detected when the parsed SMS explicitly names a counterpart bank
     * rather than a person. Should be excluded from expense/income totals
     * so it doesn't double-count the same money.
     */
    INTERNAL_TRANSFER("Internal Transfer"),
    UNKNOWN("Unknown")
}
