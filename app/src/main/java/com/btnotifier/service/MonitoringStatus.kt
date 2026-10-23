package com.btnotifier.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-local state. A saved preference never proves that the service is alive. */
data class MonitoringSnapshot(
    val running: Boolean = false,
    val phase: String = "Stopped",
    val nextScanAt: Long? = null,
    val error: String? = null
)

object MonitoringStatus {
    private val mutableState = MutableStateFlow(MonitoringSnapshot())
    val state: StateFlow<MonitoringSnapshot> = mutableState
    internal fun update(snapshot: MonitoringSnapshot) { mutableState.value = snapshot }
}
