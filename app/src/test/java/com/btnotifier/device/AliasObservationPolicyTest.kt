package com.btnotifier.device

import com.btnotifier.data.DeviceMetadata
import com.btnotifier.data.WatchedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliasObservationPolicyTest {
    @Test fun currentWatchedObservationBeatsStaleDialogWhenMetadataIsMissing() {
        val result = AliasObservationPolicy.resolve(null, WatchedDevice("AA", "Current B", lastSeenAt = 200), "Captured A")
        assertEquals("Current B", result.name)
        assertEquals(200L, result.observedAt)
    }

    @Test fun newerWatchedObservationBeatsOlderMetadata() {
        val result = AliasObservationPolicy.resolve(
            DeviceMetadata("AA", lastSeenName = "Old A", lastObservedAt = 100),
            WatchedDevice("AA", "Current B", lastSeenAt = 200), "Captured A"
        )
        assertEquals("Current B", result.name)
        assertEquals(200L, result.observedAt)
    }

    @Test fun newerMetadataBeatsOlderWatchedAndCapturedNames() {
        val result = AliasObservationPolicy.resolve(
            DeviceMetadata("AA", lastSeenName = "Newest C", lastObservedAt = 300),
            WatchedDevice("AA", "Old B", lastSeenAt = 200), "Captured A"
        )
        assertEquals("Newest C", result.name)
        assertEquals(300L, result.observedAt)
    }

    @Test fun storedNamesWithoutTimestampsStillBeatUntimestampedDialogSnapshots() {
        val result = AliasObservationPolicy.resolve(DeviceMetadata("AA", lastSeenName = "Stored actual"), null, "Captured old")
        assertEquals("Stored actual", result.name)
        assertNull(result.observedAt)
    }

    @Test fun unwatchedRenameFallsBackToCapturedActualNameWhenNothingIsStored() {
        val result = AliasObservationPolicy.resolve(null, null, " Nearby actual ")
        assertEquals("Nearby actual", result.name)
        assertNull(result.observedAt)
    }

    @Test fun legacyAliasValuesAreNotSavedAsActualNames() {
        val result = AliasObservationPolicy.resolve(
            DeviceMetadata("AA", customName = "Old alias", lastSeenName = "Old alias"),
            WatchedDevice("AA", "Old alias", lastSeenAt = null), "Old alias"
        )
        assertNull(result.name)
        assertNull(result.observedAt)
    }

    @Test fun newerUnnamedObservationKeepsLastKnownActualNameAndNewestTimestamp() {
        val result = AliasObservationPolicy.resolve(
            DeviceMetadata("AA", lastSeenName = "Known name", lastObservedAt = 100),
            WatchedDevice("AA", "", lastSeenAt = 200), "Captured old"
        )
        assertEquals("Known name", result.name)
        assertEquals(200L, result.observedAt)
    }
}
