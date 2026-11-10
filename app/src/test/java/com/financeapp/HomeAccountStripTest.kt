package com.financeapp

import com.financeapp.ui.screens.accountChipName
import com.financeapp.viewmodel.AccountSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/** NAH-28: names on Home's account strip. */
class HomeAccountStripTest {
    private fun account(bank: String, label: String = bank, suffix: String? = null) =
        AccountSummary(bankName = bank, accountNumber = "1000001234", balance = 0.0, transactionCount = 1, label = label, accountSuffix = suffix)

    @Test fun `one account at a bank shows the bank's short name`() {
        assertEquals("CBE", accountChipName(account("Commercial Bank of Ethiopia")))
        assertEquals("BoA", accountChipName(account("Bank of Abyssinia")))
        assertEquals("Telebirr", accountChipName(account("Telebirr")))
    }

    @Test fun `several accounts at a bank keep their numbered label`() {
        assertEquals("CBE 2", accountChipName(account("Commercial Bank of Ethiopia", label = "CBE 2", suffix = "1234")))
    }
}
