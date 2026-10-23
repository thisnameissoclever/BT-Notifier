package com.btnotifier.scanner

import org.junit.Assert.*
import org.junit.Test

class ScanCycleHealthTest {
    @Test fun leaseWithNoWorkingTransportCannotCompleteSuccessfully() {
        val health = ScanCycleHealth(hasTargets = true)
        health.observe(ScannerStatus(error = "BLE unavailable; classic rejected"))
        val result = health.finish(emptyList<String>())
        assertEquals(ScanCycleOutcome.BLOCKED, result.outcome)
        assertEquals("BLE unavailable; classic rejected", result.error)
    }

    @Test fun lostTransportsKeepActualSightingsWithoutSuccessfulCompletion() {
        val health = ScanCycleHealth(hasTargets = true)
        health.observe(ScannerStatus(true, true, true))
        health.observe(ScannerStatus(error = "Bluetooth permission lost"))
        // A foreground acquisition can recover the shared radio without repairing this cycle.
        health.observe(ScannerStatus(true, true, false))
        val result = health.finish(listOf("actual sighting"))
        assertEquals(ScanCycleOutcome.BLOCKED, result.outcome)
        assertEquals(listOf("actual sighting"), result.observations)
        assertEquals("Bluetooth permission lost", result.error)
    }

    @Test fun partialTransportStillCompletesAndPreservesItsObservations() {
        val health = ScanCycleHealth(hasTargets = true)
        health.observe(ScannerStatus(true, false, true, "BLE failed"))
        val result = health.finish(listOf("classic sighting"))
        assertEquals(ScanCycleOutcome.COMPLETED, result.outcome)
        assertEquals(listOf("classic sighting"), result.observations)
        assertEquals("BLE failed", result.error)
    }

    @Test fun emptyTargetLeaseNeverClaimsPhysicalCompletionFromAnotherConsumer() {
        val health = ScanCycleHealth(hasTargets = false)
        health.observe(ScannerStatus(true, true, true))
        assertEquals(ScanCycleOutcome.NO_TARGETS, health.finish(emptyList<String>()).outcome)
    }
}
