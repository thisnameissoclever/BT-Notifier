package com.btnotifier.service

/** Invalidates callbacks even if their coroutine has not reached its next suspension. */
class CyclePolicy {
    private var generation = 0L
    private var active = false
    fun begin(): Long = (++generation).also { active = true }
    fun cancel() { generation++; active = false }
    fun isCurrent(token: Long): Boolean = active && generation == token
    fun nextScanAt(startedAt: Long, intervalMs: Long, now: Long): Long =
        maxOf(startedAt + intervalMs, now)
}

class AlertPolicy {
    fun shouldAlert(notifyEnabled: Boolean, deviceEnabled: Boolean, lastAlertAt: Long?, now: Long): Boolean =
        notifyEnabled && deviceEnabled && (lastAlertAt == null || now - lastAlertAt >= 5 * 60 * 1000L)
}
