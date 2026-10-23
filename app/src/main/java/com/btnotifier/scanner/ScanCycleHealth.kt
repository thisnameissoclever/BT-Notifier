package com.btnotifier.scanner

enum class ScanCycleOutcome { COMPLETED, BLOCKED, NO_TARGETS }

data class ScanCycleResult<T>(
    val outcome: ScanCycleOutcome,
    val observations: List<T>,
    val error: String?
)

/** Updated under the scanner lock while a lease exists, before its consumer releases it. */
class ScanCycleHealth(private val hasTargets: Boolean) {
    private var observedRunning = false
    private var interrupted = false
    private var latestError: String? = null
    private var failure: String? = null

    @Synchronized
    fun observe(status: ScannerStatus) {
        if (!hasTargets) return
        if (status.error != null) latestError = status.error
        if (status.bleScanning || status.classicScanning) observedRunning = true
        else {
            interrupted = true
            if (failure == null) failure = status.error ?: "Bluetooth transports stopped before scan completion"
        }
    }

    @Synchronized
    fun <T> finish(observations: Collection<T>): ScanCycleResult<T> {
        val outcome = when {
            !hasTargets -> ScanCycleOutcome.NO_TARGETS
            !observedRunning || interrupted -> ScanCycleOutcome.BLOCKED
            else -> ScanCycleOutcome.COMPLETED
        }
        return ScanCycleResult(outcome, observations.toList(),
            if (outcome == ScanCycleOutcome.BLOCKED) failure ?: "Bluetooth scan did not start" else latestError)
    }
}
