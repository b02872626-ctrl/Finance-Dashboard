package com.financeapp.remote

import com.financeapp.data.model.ReviewStatus
import com.financeapp.data.model.Spending
import com.financeapp.data.model.TransactionCategoryCatalog
import com.financeapp.data.model.TransactionEntity

/**
 * Keeps the user's categories safe across a reinstall (or a second phone).
 *
 * After a reinstall the app reads the SMS history again, so every row comes
 * back as a fresh, untouched one (PENDING, no category) waiting to upload.
 * Uploading those with `category = null, review_status = PENDING` used to
 * overwrite the categories already in the cloud, and the pull that runs
 * after the push then found nothing to restore.
 *
 * The rules here:
 *  1. An untouched row never sends its review state, so the cloud keeps
 *     whatever category it already has ([sendsReviewState]).
 *  2. The pull reaches back as far as the oldest row just uploaded, so a
 *     whole re-read history is restored, not just the last 90 days
 *     ([pullSinceMs]).
 *  3. Category names that came back from the cloud but aren't built in are
 *     added to the user's own list again, so they show in the picker
 *     ([customCategoriesToRestore]).
 *  4. The reach back is remembered until a pull succeeds ([owedAfterUpload]).
 *  5. After a fresh read (a reinstall), moves between the user's own
 *     accounts are filed only once the restore is done: filed first, they
 *     went up as "Own accounts" over the category the user had picked.
 */
object CloudCategoryRestore {

    /** The normal pull window: recent edits made on the web. */
    const val RECENT_WINDOW_MS = 90L * 24 * 3600 * 1000L

    /**
     * True when the row carries a decision the user (or a user rule) made,
     * so its category and review status should be uploaded. A row the
     * parser just produced (PENDING, no category) has nothing to say and
     * must not blank out the cloud's copy.
     */
    fun sendsReviewState(tx: TransactionEntity): Boolean =
        !(tx.reviewStatus == ReviewStatus.PENDING && tx.category.isNullOrBlank())

    /**
     * How far back the pull should read: the last 90 days, or further when
     * rows older than that were just uploaded (a re-read history after a
     * reinstall).
     */
    fun pullSinceMs(nowMs: Long, justUploaded: List<TransactionEntity>, owedSinceMs: Long? = null): Long {
        val recent = nowMs - RECENT_WINDOW_MS
        val oldest = justUploaded.minOfOrNull { it.dateTime }
        return listOfNotNull(recent, oldest, owedSinceMs).min()
    }

    /**
     * The restore still owed after this pass: how far back a pull must
     * reach. Kept until a pull succeeds, so a pull that fails (a timeout on
     * a long history) is tried again on the next pass instead of leaving
     * every older row without its category. Null when nothing is owed.
     */
    fun owedAfterUpload(nowMs: Long, justUploaded: List<TransactionEntity>, owedSinceMs: Long?): Long? {
        val oldest = justUploaded.minOfOrNull { it.dateTime }?.takeIf { it < nowMs - RECENT_WINDOW_MS }
        return listOfNotNull(oldest, owedSinceMs).minOrNull()
    }

    /**
     * Category names from the cloud that aren't built in and aren't in the
     * user's own list yet.
     */
    fun customCategoriesToRestore(cloudCategories: Collection<String?>, existingCustom: List<String>): List<String> {
        val known = (TransactionCategoryCatalog.defaultCategories + existingCustom).map { it.lowercase() }.toSet()
        return cloudCategories
            .mapNotNull { it?.let(TransactionCategoryCatalog::normalize)?.takeIf(String::isNotBlank) }
            .filter { it.lowercase() !in known && it.lowercase() !in Spending.LEGACY_OWN_ACCOUNT_NAMES }
            .distinctBy { it.lowercase() }
    }
}
