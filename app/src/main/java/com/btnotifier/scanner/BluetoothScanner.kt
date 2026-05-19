package com.btnotifier.scanner

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScannedDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val isBle: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class BluetoothScanner(private val context: Context) {

    private val tag = "BluetoothScanner"
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private val _devices = MutableStateFlow<Map<String, ScannedDevice>>(emptyMap())
    val devices: StateFlow<Map<String, ScannedDevice>> = _devices

    private var isScanning = false
    private var classicReceiver: BroadcastReceiver? = null

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            // Read name only if BLUETOOTH_CONNECT is granted; still add the device without it.
            val name = if (hasConnectPermission()) {
                try { device.name ?: "" } catch (e: SecurityException) { "" }
            } else ""
            addDevice(
                ScannedDevice(
                    address = device.address,
                    name = name,
                    rssi = result.rssi,
                    isBle = true
                )
            )
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(tag, "BLE scan failed: $errorCode")
        }
    }

    fun startScan() {
        if (isScanning) return
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        isScanning = true
        _devices.value = emptyMap()

        startBleScan(adapter)
        startClassicScan(adapter)
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false

        stopBleScan()
        stopClassicScan()
    }

    fun clearDevices() {
        _devices.value = emptyMap()
    }

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    private fun startBleScan(adapter: BluetoothAdapter) {
        if (!hasScanPermission()) return
        val scanner = adapter.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, bleScanCallback)
        } catch (e: SecurityException) {
            Log.e(tag, "BLE scan permission denied", e)
        }
    }

    private fun stopBleScan() {
        if (!hasScanPermission()) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
        } catch (e: SecurityException) {
            Log.e(tag, "BLE stop scan error", e)
        }
    }

    private fun startClassicScan(adapter: BluetoothAdapter) {
        if (!hasScanPermission()) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        device?.let {
                            val name = try { it.name ?: "" } catch (e: SecurityException) { "" }
                            addDevice(
                                ScannedDevice(
                                    address = it.address,
                                    name = name,
                                    rssi = rssi,
                                    isBle = false
                                )
                            )
                        }
                    }
                }
            }
        }
        classicReceiver = receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(receiver, filter)
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (e: SecurityException) {
            Log.e(tag, "Classic scan permission denied", e)
        }
    }

    private fun stopClassicScan() {
        classicReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
            classicReceiver = null
        }
        if (!hasScanPermission()) return
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: SecurityException) {
            Log.e(tag, "Classic cancel discovery error", e)
        }
    }

    private fun addDevice(device: ScannedDevice) {
        val current = _devices.value.toMutableMap()
        val existing = current[device.address]
        // Keep the better name if we already have one
        val bestName = when {
            device.name.isNotBlank() -> device.name
            existing?.name?.isNotBlank() == true -> existing.name
            else -> ""
        }
        current[device.address] = device.copy(name = bestName)
        _devices.value = current
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
    }
}
