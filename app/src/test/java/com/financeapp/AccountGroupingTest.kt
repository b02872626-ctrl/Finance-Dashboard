package com.financeapp

import com.financeapp.data.accounts.AccountGrouping
import com.financeapp.data.model.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountGroupingTest {

    private val cbe = "Commercial Bank of Ethiopia"

    private fun tx(bank: String, account: String?, dateTime: Long, balance: Double? = null) =
        TransactionEntity(
            sender = "CBE",
            bankName = bank,
            type = "DEBIT",
            amount = 10.0,
            balance = balance,
            counterparty = null,
            accountNumber = account,
            refNumber = null,
            dateTime = dateTime,
            serviceCharge = null,
            vat = null,
            disasterFund = null,
            totalCharged = null,
            rawBody = ""
        )

    @Test
    fun `suffix ignores mask length`() {
        assertEquals("8166", AccountGrouping.accountSuffix("1*****8166"))
        assertEquals("8166", AccountGrouping.accountSuffix("1********8166."))
        assertNull(AccountGrouping.accountSuffix("1***"))
        assertNull(AccountGrouping.accountSuffix(null))
    }

    @Test
    fun `single account bank stays one group with every row`() {
        val groups = AccountGrouping.group(listOf(
            tx(cbe, "1*****6155", 1),
            tx(cbe, "1********6155", 2),
            tx(cbe, null, 3)
        ))
        assertEquals(1, groups.size)
        assertEquals(cbe, groups[0].label)
        assertEquals(3, groups[0].transactions.size)
        assertNull(groups[0].accountSuffix)
        assertEquals("1********6155", groups[0].accountNumber)
    }

    @Test
    fun `two accounts at one bank split and number by first seen`() {
        val groups = AccountGrouping.group(listOf(
            tx(cbe, "1********8506", 50),
            tx(cbe, "1********6155", 10),
            tx(cbe, "1********6155", 60),
            tx(cbe, null, 70),
            tx("Telebirr", null, 5)
        ))
        val cbeGroups = groups.filter { it.bankName == cbe }
        assertEquals(listOf("CBE 1", "CBE 2"), cbeGroups.map { it.label })
        assertEquals(listOf("6155", "8506"), cbeGroups.map { it.accountSuffix })
        assertEquals(listOf(2, 1), cbeGroups.map { it.transactions.size })
        assertEquals(listOf("$cbe#6155", "$cbe#8506"), cbeGroups.map { it.key })
        assertEquals("Telebirr", groups.single { it.bankName == "Telebirr" }.label)
    }

    // ── Hidden accounts ──────────────────────────────────────────────────

    @Test
    fun `account key matches the group key whatever the mask length`() {
        val rows = listOf(
            tx(cbe, "1********6155", 1), tx(cbe, "1*********6155", 2),
            tx(cbe, "1********8565", 3), tx(cbe, "1*********8565", 4),
        )
        val groups = AccountGrouping.group(rows)
        assertEquals(2, groups.size)
        groups.forEach { g ->
            g.transactions.forEach { t ->
                assertEquals(g.key, AccountGrouping.accountKeyOf(t.bankName, t.sender, t.accountNumber))
            }
        }
    }

    @Test
    fun `account key falls back to sender and needs an account number`() {
        assertEquals("CBE#6155", AccountGrouping.accountKeyOf("", "CBE", "1****6155"))
        assertNull(AccountGrouping.accountKeyOf(cbe, "CBE", null))
    }

    @Test
    fun `account key round trips`() {
        val key = AccountGrouping.accountKey(cbe, "8565")
        assertEquals(cbe to "8565", AccountGrouping.parseAccountKey(key))
        assertNull(AccountGrouping.parseAccountKey("no-hash"))
        assertNull(AccountGrouping.parseAccountKey("bank#"))
    }

    @Test
    fun `hiding two of four accounts renumbers the rest`() {
        val rows = listOf(
            tx(cbe, "1********6155", 1), tx(cbe, "1********8506", 2),
            tx(cbe, "1********8565", 3), tx(cbe, "1********1287", 4),
        )
        val hidden = setOf(AccountGrouping.accountKey(cbe, "8565"), AccountGrouping.accountKey(cbe, "1287"))
        val visible = rows.filter { AccountGrouping.accountKeyOf(it.bankName, it.sender, it.accountNumber) !in hidden }
        assertEquals(listOf("CBE 1" to "6155", "CBE 2" to "8506"),
            AccountGrouping.group(visible).map { it.label to it.accountSuffix })
    }

    // ── Berhan writes one account two ways (Dawi, Nov 2026): "*********5167"
    //    on debits and "105001XXXXX67" on credits. It showed as two accounts
    //    and a stale 16,500 balance was added to the real 42.50.

    private val berhan = "Berhan Bank"

    @Test
    fun `suffix uses only the digits after the mask`() {
        assertEquals("67", AccountGrouping.accountSuffix("105001XXXXX67"))
        assertEquals("5167", AccountGrouping.accountSuffix("*********5167"))
        assertEquals("6926", AccountGrouping.accountSuffix("1000482226926"))
    }

    @Test
    fun `Berhan debit and credit masks are one account with the newest balance`() {
        val rows = listOf(
            tx(berhan, "105001XXXXX67", 1_000L, balance = 16_500.00),
            tx(berhan, "*********5167", 2_000L, balance = 42.50),
        )
        val groups = AccountGrouping.group(rows)
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].transactions.size)
        val latest = groups[0].transactions.filter { it.balance != null }.maxBy { it.dateTime }
        assertEquals(42.50, latest.balance!!, 0.001)
    }

    @Test
    fun `short tail stays apart when two accounts end the same way`() {
        val rows = listOf(
            tx(berhan, "*********5167", 1_000L),
            tx(berhan, "*********1167", 2_000L),
            tx(berhan, "105001XXXXX67", 3_000L),
        )
        assertEquals(3, AccountGrouping.group(rows).size)
    }

    @Test
    fun `hiding the full account also hides its short-tail rows`() {
        val full = AccountGrouping.accountKey(berhan, "5167")
        val short = AccountGrouping.accountKeyOf(berhan, "Berhan Bank", "105001XXXXX67")!!
        assertEquals(true, AccountGrouping.sameAccountKey(full, short))
        assertEquals(false, AccountGrouping.sameAccountKey(full, AccountGrouping.accountKey(berhan, "1167")))
        assertEquals(false, AccountGrouping.sameAccountKey(full, AccountGrouping.accountKey("Commercial Bank of Ethiopia", "5167")))
    }
}
