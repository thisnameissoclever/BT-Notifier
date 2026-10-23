package com.btnotifier.ui.nearby

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.btnotifier.data.DetectionSource
import com.btnotifier.data.history.ObservationRepository
import com.btnotifier.scanner.ScanLease
import com.btnotifier.service.CyclePolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.DeviceMetadata
import com.btnotifier.data.WatchedDevice
import com.btnotifier.device.DeviceDisplayName
import com.btnotifier.device.AliasObservationPolicy
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.scanner.ScannedDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class NearbyViewModel(application: Application) : AndroidViewModel(application) {

    val scanner = BluetoothScanner.getInstance(application)
    private var lease: ScanLease? = null
    private var stopJob: Job? = null
    private var observationJob: Job? = null
    private val timers = CyclePolicy()
    private val mutableScanError = MutableLiveData<String?>()
    val scanError: LiveData<String?> = mutableScanError
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

        scanner.status.onEach { status ->
            mutableScanError.value = status.error
            _isScanning.value = lease != null && status.isScanning
        }.launchIn(viewModelScope)
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
        if (lease != null && _isScanning.value == true) return
        stopScan()
        scanner.clearDevices()
        val current = scanner.acquire("nearby") ?: run {
            mutableScanError.value = scanner.status.value.error
            return
        }
        lease = current
        _isScanning.value = scanner.status.value.isScanning
        val token = timers.begin()
        observationJob = viewModelScope.launch {
            current.devices.collect { devices ->
                try {
                    ObservationRepository.get(getApplication()).record(devices.values, DetectionSource.NEARBY)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("NearbyViewModel", "record nearby observations failed", error)
                    mutableScanError.value = "Could not save nearby observations: ${error.message ?: error.javaClass.simpleName}"
                }
            }
        }
        stopJob = viewModelScope.launch {
            delay(15_000)
            if (timers.isCurrent(token) && lease === current) stopScan()
        }
    }

    fun stopScan() {
        timers.cancel()
        stopJob?.cancel()
        stopJob = null
        observationJob?.cancel()
        observationJob = null
        lease?.let { scanner.release(it) }
        lease = null
        _isScanning.value = false
    }
    fun addToWatchList(device: ScannedDevice) {
        viewModelScope.launch {
            db.withTransaction {
                rememberLastSeenName(device.address, device.name, device.timestamp)
                if (dao.getByAddress(device.address) == null) {
                    val metadata = metaDao.getByAddress(device.address)
                    dao.insert(WatchedDevice(address = device.address,
                        name = metadata?.lastSeenName ?: device.name,
                        lastSeenAt = maxOf(device.timestamp, metadata?.lastObservedAt ?: 0L)))
                }
            }
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
            db.withTransaction {
                val existing = metaDao.getByAddress(device.address)
                val newestName = if ((existing?.lastObservedAt ?: 0L) > device.timestamp) existing?.lastSeenName
                    else DeviceDisplayName.normalizeActualName(device.name) ?: existing?.lastSeenName
                metaDao.upsert(
                    (existing ?: DeviceMetadata(device.address)).copy(
                        lastSeenName = newestName,
                        lastObservedAt = maxOf(existing?.lastObservedAt ?: 0L, device.timestamp),
                        isHidden = true
                    )
                )
                dao.getByAddress(device.address)?.let { dao.delete(it) }
            }
        }
    }

    fun unhideDevice(address: String) {
        viewModelScope.launch { metaDao.setHidden(address, false) }
    }

    fun renameDevice(address: String, newName: String, actualName: String? = null) {
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

    private suspend fun rememberLastSeenName(address: String, actualName: String?, observedAt: Long) {
        val normalizedActualName = DeviceDisplayName.normalizeActualName(actualName) ?: return
        val existing = metaDao.getByAddress(address)
        if ((existing?.lastObservedAt ?: 0L) > observedAt) return
        if (existing != null) {
            metaDao.upsert(existing.copy(lastSeenName = normalizedActualName, lastObservedAt = observedAt))
        } else {
            metaDao.upsert(DeviceMetadata(address, lastSeenName = normalizedActualName, lastObservedAt = observedAt))
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }
}
