package com.btnotifier.ui.nearby

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.DeviceMetadata
import com.btnotifier.data.WatchedDevice
import com.btnotifier.device.DeviceDisplayName
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.scanner.ScannedDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class NearbyViewModel(application: Application) : AndroidViewModel(application) {

    val scanner = BluetoothScanner(application)
    private val db = AppDatabase.getInstance(application)
    private val dao = db.watchedDeviceDao()
    private val metaDao = db.deviceMetadataDao()

    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _devices = MutableLiveData<List<DeviceItem>>(emptyList())
    val devices: LiveData<List<DeviceItem>> = _devices

    val watchedDevices: LiveData<List<WatchedDevice>> = dao.getAllLive()
    val hiddenDevices: LiveData<List<DeviceMetadata>> = metaDao.getHiddenLive()

    // Address to metadata, kept in sync with DB.
    private var metaMap: Map<String, DeviceMetadata> = emptyMap()

    data class DeviceItem(
        val device: ScannedDevice,
        val isWatched: Boolean,
        val customName: String?,
        val lastSeenName: String?
    ) {
        val displayName: String
            get() = DeviceDisplayName.resolve(
                alias = customName,
                primaryName = device.name,
                secondaryName = lastSeenName,
                fallback = "Unknown Device"
            )
    }

    init {
        // Use asFlow + launchIn so observers are cancelled automatically in onCleared()
        metaDao.getAllLive().asFlow()
            .onEach { list ->
                metaMap = list.associateBy { it.address }
                rebuildDeviceList()
            }
            .launchIn(viewModelScope)

        scanner.devices.onEach { _ -> rebuildDeviceList() }.launchIn(viewModelScope)

        watchedDevices.asFlow()
            .onEach { rebuildDeviceList() }
            .launchIn(viewModelScope)
    }

    private fun rebuildDeviceList() {
        val watchedAddresses = watchedDevices.value?.map { it.address }?.toSet() ?: emptySet()
        val items = scanner.devices.value.values
            .filter { metaMap[it.address]?.isHidden != true }
            .sortedByDescending { it.rssi }
            .map { device ->
                val meta = metaMap[device.address]
                DeviceItem(
                    device = device,
                    isWatched = device.address in watchedAddresses,
                    customName = meta?.customName,
                    lastSeenName = meta?.lastSeenName
                )
            }
        _devices.postValue(items)
    }

    fun startScan() {
        if (_isScanning.value == true) return
        scanner.clearDevices()
        _isScanning.value = true
        scanner.startScan()
        viewModelScope.launch {
            delay(15_000)
            stopScan()
        }
    }

    fun stopScan() {
        scanner.stopScan()
        _isScanning.value = false
    }

    fun addToWatchList(device: ScannedDevice) {
        viewModelScope.launch {
            rememberLastSeenName(device.address, device.name)
            dao.insert(WatchedDevice(address = device.address, name = device.name))
        }
    }

    fun removeFromWatchList(address: String) {
        viewModelScope.launch {
            val device = dao.getByAddress(address) ?: return@launch
            dao.delete(device)
        }
    }

    fun hideDevice(device: ScannedDevice) {
        viewModelScope.launch {
            val existing = metaDao.getByAddress(device.address)
            metaDao.upsert(
                (existing ?: DeviceMetadata(device.address)).copy(
                    lastSeenName = DeviceDisplayName.normalizeActualName(device.name) ?: existing?.lastSeenName,
                    isHidden = true
                )
            )
            dao.getByAddress(device.address)?.let { dao.delete(it) }
        }
    }

    fun unhideDevice(address: String) {
        viewModelScope.launch { metaDao.setHidden(address, false) }
    }

    fun renameDevice(address: String, newName: String, actualName: String? = null) {
        viewModelScope.launch {
            val name = DeviceDisplayName.normalizeAlias(newName)
            val normalizedActualName = DeviceDisplayName.normalizeActualName(actualName)
            val existing = metaDao.getByAddress(address)
            val watched = dao.getByAddress(address)
            val updated = (existing ?: DeviceMetadata(address)).copy(
                customName = name,
                lastSeenName = DeviceDisplayName.retainedLastSeenName(
                    existingAlias = existing?.customName,
                    existingLastSeenName = existing?.lastSeenName,
                    actualName = normalizedActualName
                )
            )
            metaDao.upsert(updated)
            when {
                normalizedActualName != null -> dao.updateName(address, normalizedActualName)
                DeviceDisplayName.storedNameLooksLikeAlias(existing?.customName, watched?.name) ->
                    dao.updateName(address, "")
            }
        }
    }

    private suspend fun rememberLastSeenName(address: String, actualName: String?) {
        val normalizedActualName = DeviceDisplayName.normalizeActualName(actualName) ?: return
        val existing = metaDao.getByAddress(address)
        if (existing != null) {
            metaDao.setLastSeenName(address, normalizedActualName)
        } else {
            metaDao.upsert(DeviceMetadata(address, lastSeenName = normalizedActualName))
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanner.stopScan()
    }
}
