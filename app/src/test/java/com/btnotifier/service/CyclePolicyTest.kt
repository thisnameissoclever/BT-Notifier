package com.btnotifier.service

import org.junit.Assert.*
import org.junit.Test

class CyclePolicyTest {
    @Test fun stopInvalidatesPendingEvaluationAndOldManualTimers() {
        val policy = CyclePolicy()
        val old = policy.begin()
        policy.cancel()
        assertFalse(policy.isCurrent(old))
        val replacement = policy.begin()
        assertFalse(policy.isCurrent(old))
        assertTrue(policy.isCurrent(replacement))
    }

    @Test fun startToStartScheduleDoesNotAddScanDuration() {
        assertEquals(61_000L, CyclePolicy().nextScanAt(1_000L, 60_000L, 16_000L))
    }

    @Test fun notificationSwitchesAndCooldownOnlyControlDelivery() {
        val alerts = AlertPolicy()
        assertFalse(alerts.shouldAlert(false, true, null, 400_000L))
        assertFalse(alerts.shouldAlert(true, false, null, 400_000L))
        assertFalse(alerts.shouldAlert(true, true, 390_000L, 400_000L))
        assertTrue(alerts.shouldAlert(true, true, null, 400_000L))
    }
}
