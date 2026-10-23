package com.btnotifier.device

import com.btnotifier.data.DeviceMetadata
import com.btnotifier.data.WatchedDevice

object AliasObservationPolicy {
    data class Observation(val name: String?, val observedAt: Long?)

    fun resolve(existing: DeviceMetadata?, watched: WatchedDevice?, capturedActualName: String?): Observation {
        val metadataName = DeviceDisplayName.safeActualNameForAliasEdit(existing?.customName, existing?.lastSeenName, null)
        val watchedName = DeviceDisplayName.safeActualNameForAliasEdit(existing?.customName, null, watched?.name)
        val metadataAt = existing?.lastObservedAt
        val watchedAt = watched?.lastSeenAt
        val newestAt = listOfNotNull(metadataAt, watchedAt).maxOrNull()
        val storedName = when {
            watchedAt != null && (metadataAt == null || watchedAt > metadataAt) -> watchedName ?: metadataName
            else -> metadataName ?: watchedName
        }
        val name = storedName ?: if (newestAt == null) {
            DeviceDisplayName.safeActualNameForAliasEdit(existing?.customName, null, capturedActualName)
        } else null
        return Observation(name, newestAt)
    }
}
