package com.financeapp.ui

import com.financeapp.ui.screens.SwipeOutcome
import com.financeapp.ui.screens.swipeOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/** NAH-36: what letting go of the review card does. Card 1000 px wide, line at 350. */
class ReviewSwipeTest {
    private val w = 1000f

    @Test fun pastTheLineRightConfirmsTheCategory() {
        assertEquals(SwipeOutcome.Confirm, swipeOutcome(400f, 0f, w, hasCategory = true))
    }

    @Test fun rightWithNoCategoryOpensThePicker() {
        assertEquals(SwipeOutcome.Pick, swipeOutcome(400f, 0f, w, hasCategory = false))
    }

    @Test fun pastTheLineLeftSkips() {
        assertEquals(SwipeOutcome.Skip, swipeOutcome(-400f, 0f, w, hasCategory = true))
    }

    @Test fun shortOfTheLineSpringsBack() {
        assertEquals(SwipeOutcome.Back, swipeOutcome(300f, 0f, w, hasCategory = true))
        assertEquals(SwipeOutcome.Back, swipeOutcome(-300f, 0f, w, hasCategory = true))
    }

    @Test fun aQuickFlickCountsBeforeTheLine() {
        // 100 px out at 2500 px/s: carries to 600, past the line.
        assertEquals(SwipeOutcome.Confirm, swipeOutcome(100f, 2500f, w, hasCategory = true))
        assertEquals(SwipeOutcome.Skip, swipeOutcome(-100f, -2500f, w, hasCategory = true))
        // A slow drag of the same distance doesn't.
        assertEquals(SwipeOutcome.Back, swipeOutcome(100f, 1500f, w, hasCategory = true))
    }

    @Test fun aFlickBackCancelsADrag() {
        // Dragged past the line, then flicked back toward the middle.
        assertEquals(SwipeOutcome.Back, swipeOutcome(400f, -2000f, w, hasCategory = true))
    }
}
