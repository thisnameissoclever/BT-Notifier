package com.btnotifier.ui.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.WatchedDevice
import kotlinx.coroutines.launch

class WatchListViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).watchedDeviceDao()
    val watchedDevices = dao.getAllLive()

    fun removeDevice(device: WatchedDevice) {
        viewModelScope.launch { dao.delete(device) }
    }

    fun setAlertEnabled(device: WatchedDevice, enabled: Boolean) {
        viewModelScope.launch { dao.setAlertEnabled(device.address, enabled) }
    }
}
