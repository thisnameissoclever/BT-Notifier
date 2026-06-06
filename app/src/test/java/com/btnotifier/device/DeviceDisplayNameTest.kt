package com.btnotifier.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDisplayNameTest {

    @Test
    fun displayNameUsesAliasWithoutLeakingActualDeviceName() {
        val displayName = DeviceDisplayName.resolve(
            alias = "Studio Speaker",
            primaryName = "JBL Flip 6",
            secondaryName = "Old watch-list name",
            fallback = "00:11:22:33:44:55"
        )

        assertEquals("Studio Speaker", displayName)
    }

    @Test
    fun displayNameUsesActualDeviceNameWhenAliasIsBlank() {
        val displayName = DeviceDisplayName.resolve(
            alias = "   ",
            primaryName = "JBL Flip 6",
            secondaryName = "Old watch-list name",
            fallback = "00:11:22:33:44:55"
        )

        assertEquals("JBL Flip 6", displayName)
    }

    @Test
    fun displayNameFallsBackToSecondaryNameBeforeFallbackText() {
        val displayName = DeviceDisplayName.resolve(
            alias = null,
            primaryName = "",
            secondaryName = "Known Device",
            fallback = "Unknown Device"
        )

        assertEquals("Known Device", displayName)
    }

    @Test
    fun normalizeAliasTrimsAndClearsBlankAliases() {
        assertEquals("Studio Speaker", DeviceDisplayName.normalizeAlias("  Studio Speaker  "))
        assertNull(DeviceDisplayName.normalizeAlias("   "))
        assertNull(DeviceDisplayName.normalizeAlias(null))
    }

    @Test
    fun safeActualNameForAliasEditIgnoresLegacyWatchedNameWhenItMatchesAlias() {
        val actualName = DeviceDisplayName.safeActualNameForAliasEdit(
            alias = "Studio Speaker",
            lastSeenName = null,
            storedWatchedName = "Studio Speaker"
        )

        assertNull(actualName)
    }

    @Test
    fun safeActualNameForAliasEditIgnoresLegacyLastSeenNameWhenItMatchesAlias() {
        val actualName = DeviceDisplayName.safeActualNameForAliasEdit(
            alias = "Studio Speaker",
            lastSeenName = "Studio Speaker",
            storedWatchedName = null
        )

        assertNull(actualName)
    }

    @Test
    fun safeActualNameForAliasEditUsesStoredWatchedNameWhenItDoesNotMatchAlias() {
        val actualName = DeviceDisplayName.safeActualNameForAliasEdit(
            alias = "Studio Speaker",
            lastSeenName = null,
            storedWatchedName = "JBL Flip 6"
        )

        assertEquals("JBL Flip 6", actualName)
    }

    @Test
    fun retainedLastSeenNameClearsLegacyAliasValue() {
        val retainedName = DeviceDisplayName.retainedLastSeenName(
            existingAlias = "Studio Speaker",
            existingLastSeenName = "Studio Speaker",
            actualName = null
        )

        assertNull(retainedName)
    }

    @Test
    fun storedNameLooksLikeAliasMatchesTrimmedAliasOnly() {
        assertTrue(DeviceDisplayName.storedNameLooksLikeAlias(" Studio Speaker ", "Studio Speaker"))
        assertFalse(DeviceDisplayName.storedNameLooksLikeAlias("Studio Speaker", "JBL Flip 6"))
        assertFalse(DeviceDisplayName.storedNameLooksLikeAlias(null, "Studio Speaker"))
    }
}
