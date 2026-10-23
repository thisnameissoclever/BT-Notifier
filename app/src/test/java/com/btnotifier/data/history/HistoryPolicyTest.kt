package com.btnotifier.data.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPolicyTest {
    private fun sample(address: String, at: Long, name: String = "Speaker") =
        HistorySample(address, name, -65, true, at, "NEARBY")

    @Test fun overlappingConsumersKeepNewestObservationPerAddressAndMinute() {
        val result = HistoryPolicy.coalesce(listOf(
            sample("AA", 60_001, "old"), sample("AA", 60_099, "new"),
            sample("BB", 60_001), sample("AA", 120_000)
        ))
        assertEquals(3, result.size)
        assertEquals("new", result.single { it.address == "AA" && it.observedAt < 120_000 }.observedName)
    }

    @Test fun delayedObservationCannotReplaceNewerBucketValueOrMetadata() {
        assertFalse(HistoryPolicy.shouldReplace(90_000, 89_000))
        assertFalse(HistoryPolicy.shouldReplace(90_000, 90_000))
        assertTrue(HistoryPolicy.shouldReplace(90_000, 90_001))
        assertTrue(HistoryPolicy.shouldReplace(null, 1))
    }

    @Test fun retentionHasThirtyDayInclusiveBoundary() {
        val now = 40L * 24 * 60 * 60 * 1000
        val cutoff = now - 30L * 24 * 60 * 60 * 1000
        assertEquals(cutoff, HistoryPolicy.retentionCutoff(now))
        assertFalse(HistoryPolicy.shouldRetain(cutoff - 1, now))
        assertTrue(HistoryPolicy.shouldRetain(cutoff, now))
        assertTrue(HistoryPolicy.shouldRetain(now, now))
        assertEquals(10_000, HistoryPolicy.MAX_ROWS)
    }

    @Test fun minuteBoundaryCreatesDistinctBuckets() {
        assertEquals(0L, HistoryPolicy.minuteBucket(59_999))
        assertEquals(1L, HistoryPolicy.minuteBucket(60_000))
    }

    @Test fun emptyObservationBatchAndAnonymousNamesRemainValid() {
        assertTrue(HistoryPolicy.coalesce(emptyList()).isEmpty())
        assertEquals("", HistoryPolicy.coalesce(listOf(sample("AA", 1, ""))).single().observedName)
    }
}
