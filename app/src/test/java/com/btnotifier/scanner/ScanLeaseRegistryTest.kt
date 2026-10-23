package com.btnotifier.scanner

import org.junit.Assert.*
import org.junit.Test

class ScanLeaseRegistryTest {
    @Test fun targetedLeasesUnionAndRestoreAfterForegroundStops() {
        val registry = ScanLeaseRegistry()
        registry.acquire(setOf("A"))
        registry.acquire(setOf("B"))
        assertEquals(setOf("A", "B"), registry.effectiveAddresses())
        val nearby = registry.acquire(null)
        assertNull(registry.effectiveAddresses())
        registry.release(nearby)
        assertEquals(setOf("A", "B"), registry.effectiveAddresses())
    }

    @Test fun oldTokenReleaseDoesNotReleaseReplacementAndResultsStayEligible() {
        val registry = ScanLeaseRegistry()
        val old = registry.acquire(setOf("A"))
        registry.release(old)
        val replacement = registry.acquire(setOf("A"))
        registry.release(old)
        assertTrue(registry.accepts(replacement, "A"))
        assertFalse(registry.accepts(old, "A"))
        assertFalse(registry.accepts(replacement, "B"))
    }

    @Test fun emptyTargetsNeverRequestRadio() {
        val registry = ScanLeaseRegistry()
        val empty = registry.acquire(emptySet())
        assertFalse(registry.hasInterest())
        assertEquals(emptySet<String>(), registry.effectiveAddresses())
        assertFalse(registry.accepts(empty, "A"))
    }

    @Test fun obsoletePhysicalCallbacksAreRejected() {
        val registry = ScanLeaseRegistry()
        val token = registry.acquire(setOf("A"))
        val old = registry.restartBleGeneration()
        val current = registry.restartBleGeneration()
        assertFalse(registry.acceptsBleGeneration(old))
        assertTrue(registry.acceptsBleGeneration(current))
        registry.release(token)
        assertFalse(registry.acceptsBleGeneration(current))
    }
}
