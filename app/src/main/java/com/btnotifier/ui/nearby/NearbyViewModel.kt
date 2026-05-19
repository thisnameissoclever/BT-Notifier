package com.btnotifier.ui.nearby

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.WatchedDevice
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.scanner.ScannedDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class NearbyViewModel(application: Application) : AndroidViewModel(application) {

    val scanner = BluetoothScanner(application)
    private val dao = AppDatabase.getInstance(application).watchedDeviceDao()

    private val _isScanning = MutableLiveData(false)
    val isScanning: LiveData<Boolean> = _isScanning

    private val _devices = MutableLiveData<List<ScannedDevice>>(emptyList())
    val devices: LiveData<List<ScannedDevice>> = _devices

    val watchedDevices: LiveData<List<WatchedDevice>> = dao.getAllLive()

    init {
        scanner.devices.onEach { map ->
            _devices.postValue(map.values.sortedByDescending { it.rssi })
        }.launchIn(viewModelScope)
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
            dao.insert(
                WatchedDevice(
                    address = device.address,
                    name = device.name
                )
            )
        }
    }

    fun removeFromWatchList(address: String) {
        viewModelScope.launch {
            val device = dao.getByAddress(address) ?: return@launch
            dao.delete(device)
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanner.stopScan()
    }
}
