package com.financeapp

import com.financeapp.ui.screens.WrappedShare
import com.financeapp.util.KashDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** The Wrapped share picture: stats never reach the footer; EC weekdays read right. */
class WrappedShareTest {
    @Test fun everyLayoutStaysAboveTheFooter() {
        for (n in 1..WrappedShare.MAX_STATS) for (wide in listOf(false, true)) {
            val cells = WrappedShare.cells(n, wide)
            assertEquals(n, cells.size)
            // A value is drawn 76px under its label, about 64px tall.
            assertTrue("n=$n wide=$wide", cells.all { it.y + 76f <= WrappedShare.FOOTER_TOP - 40f })
            assertTrue(cells.all { it.x + it.width <= 1080f - WrappedShare.MARGIN + 0.5f })
        }
    }

    @Test fun fiveStatsWithAWideFirstUseThreeRows() {
        val cells = WrappedShare.cells(5, firstWide = true)
        assertEquals(WrappedShare.TOP, cells[0].y)
        assertEquals(cells[1].y, cells[2].y)                  // side by side
        assertEquals(cells[3].y, cells[4].y)
        assertTrue(cells[4].y > cells[2].y)
    }

    @Test fun weekdayPlurals() {
        val saturday = Calendar.getInstance().apply { clear(); set(2026, 10, 14, 12, 0) }.timeInMillis
        assertEquals("Saturdays", KashDates.weekdays(saturday, "GREGORIAN"))
        assertEquals("Kidame days", KashDates.weekdays(saturday, "ETHIOPIAN"))
    }
}
