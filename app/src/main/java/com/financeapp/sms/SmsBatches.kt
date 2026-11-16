package com.financeapp.sms

/** How the SMS read is split up. Plain Kotlin, so it is unit-tested. */
internal object SmsBatches {
    /**
     * The messages grouped by bank, smallest bank first (ties keep the order
     * the banks first appear). Each bank keeps its messages in their order.
     */
    fun <T> byBank(messages: List<T>, bankOf: (T) -> String): List<Pair<String, List<T>>> =
        messages.groupBy(bankOf).toList().sortedBy { it.second.size }
}
