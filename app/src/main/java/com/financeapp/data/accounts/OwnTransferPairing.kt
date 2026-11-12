package com.financeapp.data.accounts

import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.Spending
import com.financeapp.data.model.TransactionEntity
import com.financeapp.parsing.TransactionType
import kotlin.math.abs

/**
 * Finds money the user moved between their own accounts (CBE → CBE Birr,
 * CBE 1 → CBE 2, Telebirr → Awash, …) when no SMS says so.
 *
 * Every such move leaves two texts on the phone within moments of each other:
 * money out of one account and the same amount into another. Counted as they
 * stand, the move shows up as spending AND income. This pairs the two legs so
 * both can be filed under [Spending.OWN_ACCOUNTS] (RULES.md rule 5).
 *
 * A pair needs, in order of trust:
 *  1. one money-out row and one money-in row for the same amount, within
 *     [WINDOW_MS] of each other, on two different accounts of the user's; or
 *  2. as a fallback, the same non-blank reference number on both rows
 *     (and the same amount), when the SMS don't say whether the two sit on
 *     different accounts — two CBE texts without account numbers, say.
 *
 * A pair is taken only when it is unambiguous: if a row has more than one
 * possible partner (two 500s in the same few minutes) it is left alone, and
 * the user can file it by hand. Wrongly hiding a real payment is worse than
 * leaving a transfer in. A row the user already categorised is never touched.
 *
 * Pure: no database, no clock. [TransactionRepository] applies the result.
 */
object OwnTransferPairing {

    /** How far apart in time the two legs may be. */
    const val WINDOW_MS = 5 * 60 * 1000L

    data class Pair(val first: TransactionEntity, val second: TransactionEntity) {
        /** Rows that still have to be tagged (a leg already typed as an own-account move is skipped). */
        fun toTag(): List<TransactionEntity> = listOf(first, second).filter { it.isCandidate() }
    }

    private enum class Direction { OUT, IN }

    /** The pairs found among [rows]. Each row appears in at most one pair. */
    fun findPairs(rows: List<TransactionEntity>, windowMs: Long = WINDOW_MS): List<Pair> {
        val usable = rows.filter { !it.hidden && it.reviewStatus != ReviewStatus.NOT_A_TXN }
        val outs = usable.indices.filter { usable[it].direction() == Direction.OUT }
        val ins = usable.indices.filter { usable[it].direction() == Direction.IN }

        // Every pair of legs that could belong together, as an edge between
        // two positions in [usable] (positions, not rows: two identical rows
        // are still two rows).
        class Edge(val out: Int, val inn: Int, val byRef: Boolean)
        val edges = mutableListOf<Edge>()
        for (o in outs) for (i in ins) {
            val a = usable[o]
            val b = usable[i]
            if (abs(a.amount - b.amount) >= 0.005) continue
            if (abs(a.dateTime - b.dateTime) > windowMs) continue
            // At least one leg must still be open to change.
            if (!a.isCandidate() && !b.isCandidate()) continue
            val sameRef = sameRef(a, b)
            if (sameRef || differentAccounts(a, b)) edges += Edge(o, i, sameRef)
        }

        val taken = mutableSetOf<Int>()
        val pairs = mutableListOf<Pair>()
        fun accept(e: Edge) {
            if (e.out in taken || e.inn in taken) return
            taken += e.out; taken += e.inn
            pairs += Pair(usable[e.out], usable[e.inn])
        }

        // Pass 1 — the fallback evidence: a shared reference. Taken when the
        // reference points at exactly one partner on each side, which also
        // settles two same-amount moves in one minute.
        val byRef = edges.filter { it.byRef }
        for (e in byRef) {
            if (byRef.count { it.out == e.out } == 1 && byRef.count { it.inn == e.inn } == 1) accept(e)
        }

        // Pass 2 — amount, time and two different accounts, only when
        // neither leg has a rival partner among what is left.
        val rest = edges.filter { it.out !in taken && it.inn !in taken }
        for (e in rest) {
            if (rest.count { it.out == e.out } == 1 && rest.count { it.inn == e.inn } == 1) accept(e)
        }
        return pairs
    }

    // ── Rows ────────────────────────────────────────────────────────────────

    /** Still open to being filed as an own-account move: parsed, no category of its own. */
    private fun TransactionEntity.isCandidate(): Boolean =
        category == null && (type == TransactionType.CREDIT.name || type in Spending.EXPENSE_TYPES)

    /**
     * Which way the money moved. A transfer a parser already typed
     * INTERNAL_TRANSFER ("to CBE 1000…" / "from CBE 1000…") counts as the
     * leg it names, so the other bank's matching text gets filed too.
     */
    private fun TransactionEntity.direction(): Direction? = when {
        amount <= 0.0 -> null
        category != null && type != TransactionType.INTERNAL_TRANSFER.name -> null
        type == TransactionType.CREDIT.name -> Direction.IN
        type in Spending.EXPENSE_TYPES -> Direction.OUT
        type == TransactionType.INTERNAL_TRANSFER.name -> when {
            counterparty?.trimStart()?.startsWith("to ", ignoreCase = true) == true -> Direction.OUT
            counterparty?.trimStart()?.startsWith("from ", ignoreCase = true) == true -> Direction.IN
            else -> null
        }
        else -> null
    }

    private fun sameRef(a: TransactionEntity, b: TransactionEntity): Boolean {
        val x = a.refNumber?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("manual-") } ?: return false
        return x.equals(b.refNumber?.trim(), ignoreCase = true)
    }

    /**
     * True only when the two rows are certainly not the same account: another
     * bank, or the same bank with two different known account endings. Two
     * rows of one bank with no account number to tell them apart are not
     * "different" — that is what a shared reference is for.
     */
    private fun differentAccounts(a: TransactionEntity, b: TransactionEntity): Boolean {
        val bankA = bankOf(a)
        val bankB = bankOf(b)
        if (!bankA.equals(bankB, ignoreCase = true)) return true
        val sufA = AccountGrouping.accountSuffix(a.accountNumber) ?: return false
        val sufB = AccountGrouping.accountSuffix(b.accountNumber) ?: return false
        return !AccountGrouping.sameSuffix(sufA, sufB)
    }

    private fun bankOf(t: TransactionEntity): String =
        AccountGrouping.shortBankLabel(t.bankName.ifBlank { t.sender })
}
