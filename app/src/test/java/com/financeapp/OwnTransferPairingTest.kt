package com.financeapp

import com.financeapp.data.accounts.OwnTransferPairing
import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.Spending
import com.financeapp.data.model.TransactionEntity
import com.financeapp.data.model.isOwnAccountTransfer
import com.financeapp.parsing.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Money moved between the user's own accounts, found by pairing the two texts. Made-up numbers throughout. */
class OwnTransferPairingTest {

    private val t0 = 1_800_000_000_000L
    private var nextId = 0L

    private fun row(
        type: TransactionType,
        amount: Double,
        bank: String = "Commercial Bank of Ethiopia",
        sender: String = "CBE",
        account: String? = null,
        ref: String? = null,
        at: Long = t0,
        counterparty: String? = null,
        category: String? = null,
        hidden: Boolean = false,
        status: String = ReviewStatus.PENDING,
    ) = TransactionEntity(
        id = ++nextId, sender = sender, bankName = bank, type = type.name, amount = amount, balance = null,
        counterparty = counterparty, accountNumber = account, refNumber = ref, dateTime = at,
        serviceCharge = null, vat = null, disasterFund = null, totalCharged = null,
        category = category, reviewStatus = status, hidden = hidden, rawBody = "",
    )

    private fun cbeOut(amount: Double, account: String? = "1****1111", at: Long = t0, ref: String? = null) =
        row(TransactionType.DEBIT, amount, account = account, at = at, ref = ref)
    private fun telebirrIn(amount: Double, at: Long = t0 + 20_000, ref: String? = null) =
        row(TransactionType.CREDIT, amount, bank = "Telebirr", sender = "127", at = at, ref = ref)

    private fun ids(pairs: List<OwnTransferPairing.Pair>) = pairs.map { it.first.id to it.second.id }

    @Test fun `a debit at one bank and the same credit at another seconds later are one own-account move`() {
        val out = cbeOut(500.0)
        val inn = telebirrIn(500.0)
        assertEquals(listOf(out.id to inn.id), ids(OwnTransferPairing.findPairs(listOf(inn, out))))
    }

    @Test fun `two accounts at the same bank are told apart by their last four digits`() {
        val out = cbeOut(1200.0, account = "1****1111")
        val inn = row(TransactionType.CREDIT, 1200.0, account = "1****2222", at = t0 + 5_000)
        assertEquals(1, OwnTransferPairing.findPairs(listOf(out, inn)).size)
    }

    @Test fun `the same account paying and being paid the same amount is not a pair`() {
        val out = cbeOut(300.0, account = "1****1111")
        val inn = row(TransactionType.CREDIT, 300.0, account = "1****1111", at = t0 + 5_000)
        assertTrue(OwnTransferPairing.findPairs(listOf(out, inn)).isEmpty())
    }

    @Test fun `one bank with no account numbers cannot be told apart without a reference`() {
        val out = cbeOut(300.0, account = null)
        val inn = row(TransactionType.CREDIT, 300.0, account = null, at = t0 + 5_000)
        assertTrue(OwnTransferPairing.findPairs(listOf(out, inn)).isEmpty())
    }

    @Test fun `a shared reference pairs two legs the accounts cannot separate`() {
        val out = cbeOut(300.0, account = null, ref = "FT25ABC123")
        val inn = row(TransactionType.CREDIT, 300.0, account = null, at = t0 + 5_000, ref = "ft25abc123")
        assertEquals(1, OwnTransferPairing.findPairs(listOf(out, inn)).size)
    }

    @Test fun `a shared reference settles two moves of the same amount in the same minute`() {
        val outA = cbeOut(100.0, ref = "REF-A")
        val outB = cbeOut(100.0, ref = "REF-B", at = t0 + 2_000)
        val inB = telebirrIn(100.0, at = t0 + 10_000, ref = "REF-B")
        val inA = telebirrIn(100.0, at = t0 + 12_000, ref = "REF-A")
        val got = ids(OwnTransferPairing.findPairs(listOf(outA, outB, inB, inA))).toSet()
        assertEquals(setOf(outA.id to inA.id, outB.id to inB.id), got)
    }

    @Test fun `two possible partners and no reference leaves both alone`() {
        val out = cbeOut(100.0)
        val in1 = telebirrIn(100.0, at = t0 + 10_000)
        val in2 = telebirrIn(100.0, at = t0 + 20_000)
        assertTrue(OwnTransferPairing.findPairs(listOf(out, in1, in2)).isEmpty())
    }

    @Test fun `legs too far apart are not paired`() {
        val out = cbeOut(500.0)
        val late = telebirrIn(500.0, at = t0 + OwnTransferPairing.WINDOW_MS + 1)
        assertTrue(OwnTransferPairing.findPairs(listOf(out, late)).isEmpty())
        val ontime = telebirrIn(500.0, at = t0 + OwnTransferPairing.WINDOW_MS)
        assertEquals(1, OwnTransferPairing.findPairs(listOf(out, ontime)).size)
    }

    @Test fun `different amounts are not paired`() {
        assertTrue(OwnTransferPairing.findPairs(listOf(cbeOut(500.0), telebirrIn(500.5))).isEmpty())
    }

    @Test fun `a row the user already categorised is left alone`() {
        val out = cbeOut(500.0).copy(category = "Rent")
        assertTrue(OwnTransferPairing.findPairs(listOf(out, telebirrIn(500.0))).isEmpty())
    }

    @Test fun `hidden rows and rows marked not a transaction are ignored`() {
        val hiddenOut = row(TransactionType.DEBIT, 500.0, hidden = true)
        val notTxn = row(TransactionType.DEBIT, 500.0, status = ReviewStatus.NOT_A_TXN)
        assertTrue(OwnTransferPairing.findPairs(listOf(hiddenOut, telebirrIn(500.0))).isEmpty())
        assertTrue(OwnTransferPairing.findPairs(listOf(notTxn, telebirrIn(500.0))).isEmpty())
    }

    @Test fun `a transfer and a payment of the same amount are two outflows, not a pair`() {
        val a = cbeOut(500.0)
        val b = row(TransactionType.PAYMENT, 500.0, bank = "Telebirr", sender = "127", at = t0 + 5_000)
        assertTrue(OwnTransferPairing.findPairs(listOf(a, b)).isEmpty())
    }

    @Test fun `a transfer already typed as own-account anchors the other bank's leg`() {
        // Telebirr text: "transferred ETB 800 to Commercial Bank of Ethiopia account number 1000…"
        val anchor = row(
            TransactionType.INTERNAL_TRANSFER, 800.0, bank = "Telebirr", sender = "127",
            counterparty = "to CBE 1000000000001",
        )
        val creditAtCbe = row(TransactionType.CREDIT, 800.0, account = "1****1111", at = t0 + 8_000)
        val pairs = OwnTransferPairing.findPairs(listOf(anchor, creditAtCbe))
        assertEquals(1, pairs.size)
        // Only the leg still counted as income has to be filed; the anchor already is own-account.
        assertEquals(listOf(creditAtCbe.id), pairs[0].toTag().map { it.id })
    }

    @Test fun `an own-account anchor pairs only with the opposite direction`() {
        val anchorOut = row(
            TransactionType.INTERNAL_TRANSFER, 800.0, bank = "Telebirr", sender = "127",
            counterparty = "to CBE 1000000000001",
        )
        val anotherPayment = row(TransactionType.DEBIT, 800.0, account = "1****1111", at = t0 + 8_000)
        assertTrue(OwnTransferPairing.findPairs(listOf(anchorOut, anotherPayment)).isEmpty())
    }

    @Test fun `a row is used in one pair only`() {
        val out = cbeOut(500.0)
        val a = telebirrIn(500.0, at = t0 + 5_000)
        val b = row(TransactionType.CREDIT, 500.0, bank = "Awash Bank", sender = "AwashBank", at = t0 + 6_000)
        // One out, two possible ins: ambiguous, so nothing is taken.
        assertTrue(OwnTransferPairing.findPairs(listOf(out, a, b)).isEmpty())
    }

    @Test fun `filing a pair makes both legs neither spending nor income`() {
        val out = cbeOut(500.0)
        val inn = telebirrIn(500.0)
        val tagged = OwnTransferPairing.findPairs(listOf(out, inn)).single().toTag()
            .map { it.copy(category = Spending.OWN_ACCOUNTS) }
        assertEquals(2, tagged.size)
        tagged.forEach { assertTrue(it.isOwnAccountTransfer()) }
    }
}
