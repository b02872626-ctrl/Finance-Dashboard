package com.financeapp

import com.financeapp.parsing.SmsMessage
import com.financeapp.parsing.SmsParserEngine
import com.financeapp.parsing.TransactionType
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Developer tool, not a regular test: replays an export of i_raw_data
 * (admin → "Download all unparsed SMS", or the table's CSV export) through
 * today's parser and writes what it still can't read.
 *
 *   KASH_RAW_CSV=/path/to/i_raw_data_rows.csv ./gradlew testDebugUnitTest \
 *     --tests '*ParserCoverageReport*'
 *
 * Output: build/parser-coverage.txt (per-sender counts + every unread SMS).
 * Skipped when KASH_RAW_CSV isn't set.
 */
class ParserCoverageReport {

    @Test
    fun report() {
        val path = System.getenv("KASH_RAW_CSV")
        assumeTrue("set KASH_RAW_CSV to run", !path.isNullOrBlank())
        val rows = readCsv(File(path!!))
        val header = rows.first()
        val iSender = header.indexOf("sender_name")
        val iBody = header.indexOf("raw_message")

        data class Tally(var parsed: Int = 0, var ignored: Int = 0, var unread: Int = 0, var notTracked: Int = 0)
        val tally = sortedMapOf<String, Tally>()
        val unread = mutableListOf<Pair<String, String>>()
        rows.drop(1).forEach { r ->
            val sender = r.getOrNull(iSender)?.trim().orEmpty()
            val body = r.getOrNull(iBody).orEmpty()
            val t = tally.getOrPut(sender) { Tally() }
            when {
                !SmsParserEngine.isFinancialSms(sender) -> t.notTracked++
                else -> {
                    val tx = SmsParserEngine.parse(SmsMessage(sender, body, 0L))
                    when {
                        tx == null -> t.ignored++
                        tx.type == TransactionType.UNKNOWN.name || tx.bankName.startsWith("Unknown") -> { t.unread++; unread += sender to body }
                        else -> t.parsed++
                    }
                }
            }
        }
        val out = buildString {
            appendLine("sender            parsed  ignored  unread  not-tracked")
            tally.forEach { (s, t) -> appendLine("%-16s %7d %8d %7d %12d".format(s, t.parsed, t.ignored, t.unread, t.notTracked)) }
            appendLine()
            unread.groupBy({ it.first }, { it.second }).forEach { (s, bodies) ->
                appendLine("===== $s (${bodies.size} unread)")
                bodies.forEach { appendLine("- " + it.replace('\n', ' ')) }
            }
        }
        File("build/parser-coverage.txt").writeText(out)
        println(out.lineSequence().take(tally.size + 2).joinToString("\n"))
    }

    /** RFC 4180 CSV: quoted fields, doubled quotes, newlines inside quotes. */
    private fun readCsv(file: File): List<List<String>> {
        val text = file.readText().removePrefix("﻿")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += field.toString(); field.clear(); rows += row; row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}
