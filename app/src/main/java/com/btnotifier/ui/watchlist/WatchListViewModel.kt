package com.btnotifier.ui.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.DeviceMetadata
import com.btnotifier.data.WatchedDevice
import com.btnotifier.device.DeviceDisplayName
import com.btnotifier.device.AliasObservationPolicy
import kotlinx.coroutines.launch
import java.util.Locale

class WatchListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val dao = db.watchedDeviceDao()
    private val metaDao = db.deviceMetadataDao()

    private var watchedRows: List<WatchedDevice> = emptyList()
    private var metadataByAddress: Map<String, DeviceMetadata> = emptyMap()

    val watchedDevices = MediatorLiveData<List<WatchedDeviceItem>>()

    data class WatchedDeviceItem(
        val device: WatchedDevice,
        val customName: String?,
        val lastSeenName: String?
    ) {
        val displayName: String
            get() = DeviceDisplayName.resolve(
                alias = customName,
                primaryName = lastSeenName,
                secondaryName = device.name,
                fallback = device.address
            )
    }

    init {
        watchedDevices.addSource(dao.getAllLive()) { rows ->
            watchedRows = rows
            publishWatchedDevices()
        }
        watchedDevices.addSource(metaDao.getAllLive()) { rows ->
            metadataByAddress = rows.associateBy { it.address }
            publishWatchedDevices()
        }
    }

    fun removeDevice(device: WatchedDevice) {
        viewModelScope.launch { dao.delete(device) }
    }

    fun setAlertEnabled(device: WatchedDevice, enabled: Boolean) {
        viewModelScope.launch { dao.setAlertEnabled(device.address, enabled) }
    }

    fun renameDevice(address: String, newName: String, actualName: String?) {
        viewModelScope.launch {
            db.withTransaction {
                val existing = metaDao.getByAddress(address)
                val watched = dao.getByAddress(address)
                val observation = AliasObservationPolicy.resolve(existing, watched, actualName)
                metaDao.upsert(
                    (existing ?: DeviceMetadata(address)).copy(
                        customName = DeviceDisplayName.normalizeAlias(newName),
                        lastSeenName = observation.name,
                        lastObservedAt = observation.observedAt
                    )
                )
                observation.observedAt?.let { dao.updateLastSeen(address, it) }
                when {
                    observation.name != null -> dao.updateName(address, observation.name)
                    DeviceDisplayName.storedNameLooksLikeAlias(existing?.customName, watched?.name) ->
                        dao.updateName(address, "")
                }
            }
        }
    }

    private fun publishWatchedDevices() {
        val items = watchedRows.map { device ->
            val metadata = metadataByAddress[device.address]
            WatchedDeviceItem(
                device = device,
                customName = metadata?.customName,
                lastSeenName = metadata?.lastSeenName
            )
        }.sortedBy { it.displayName.lowercase(Locale.ROOT) }
        watchedDevices.value = items
    }
}
