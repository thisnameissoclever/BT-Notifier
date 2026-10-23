package com.btnotifier.ui.finder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinderSignalModelTest {
    @Test fun invalidAndSentinelReadingsNeverCreateSignal() {
        val model = FinderSignalModel()
        listOf(Short.MIN_VALUE.toInt(), -128, 0, 127, 20).forEach {
            assertFalse(model.add(it, 1_000L))
        }
        assertTrue(model.snapshot(1_000L).samples.isEmpty())
        assertNull(model.snapshot(1_000L).smoothedRssi)
        assertEquals(SignalProximity.NO_RECENT_SIGNAL, model.snapshot(1_000L).proximity)
    }

    @Test fun smoothingDampsJumpsAndReportsStrongerThenWeaker() {
        val model = FinderSignalModel()
        model.add(-80, 1_000L)
        model.add(-40, 2_000L)
        assertEquals(-70.0, model.snapshot(2_000L).smoothedRssi!!, 0.001)
        assertEquals(SignalDirection.STRONGER, model.snapshot(2_000L).direction)
        model.add(-100, 3_000L)
        assertEquals(-77.5, model.snapshot(3_000L).smoothedRssi!!, 0.001)
        assertEquals(SignalDirection.WEAKER, model.snapshot(3_000L).direction)
    }

    @Test fun trendRetainsOnlyNewestBoundedSamples() {
        val model = FinderSignalModel(maxSamples = 3)
        (1L..8L).forEach { model.add(-90 + it.toInt(), it * 1_000L) }
        assertEquals(listOf(6_000L, 7_000L, 8_000L), model.snapshot(8_000L).samples.map { it.at })
    }

    @Test fun staleReadingRemovesCurrentStrengthAndDirectionAtExpiry() {
        val model = FinderSignalModel(staleAfterMs = 10_000L)
        model.add(-50, 1_000L)
        assertEquals(SignalProximity.STRONG, model.snapshot(10_999L).proximity)
        val stale = model.snapshot(11_000L)
        assertNull(stale.smoothedRssi)
        assertEquals(SignalDirection.UNKNOWN, stale.direction)
        assertEquals(SignalProximity.NO_RECENT_SIGNAL, stale.proximity)
        assertEquals(1, stale.samples.size)
    }

    @Test fun oldOrDuplicateObservationsCannotRefreshOrBiasSignal() {
        val model = FinderSignalModel()
        model.add(-85, 2_000L)
        assertFalse(model.add(-40, 1_000L))
        assertFalse(model.add(-40, 2_000L))
        assertEquals(-85.0, model.snapshot(2_000L).smoothedRssi!!, 0.001)
        assertEquals(SignalProximity.WEAK, model.snapshot(2_000L).proximity)
    }

    @Test fun firstSampleAfterGapStartsFreshAndCannotClaimMovement() {
        val model = FinderSignalModel(staleAfterMs = 10_000L)
        model.add(-90, 1_000L)
        model.add(-60, 11_000L)
        val current = model.snapshot(11_000L)
        assertEquals(-60.0, current.smoothedRssi!!, 0.001)
        assertEquals(SignalProximity.MODERATE, current.proximity)
        assertEquals(SignalDirection.UNKNOWN, current.direction)
    }
}
