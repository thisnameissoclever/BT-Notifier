package com.btnotifier.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
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

data class ScannerStatus(
    val isScanning: Boolean = false,
    val bleScanning: Boolean = false,
    val classicScanning: Boolean = false,
    val error: String? = null
)

class ScanLease internal constructor(internal val token: Long, val owner: String, hasTargets: Boolean) {
    internal val acquiredAtNanos = SystemClock.elapsedRealtimeNanos()
    internal val results = MutableStateFlow<Map<String, ScannedDevice>>(emptyMap())
    val devices: StateFlow<Map<String, ScannedDevice>> = results
    internal val health = ScanCycleHealth(hasTargets)
}

class BluetoothScanner private constructor(private val context: Context) {
    companion object {
        @Volatile private var instance: BluetoothScanner? = null
        fun getInstance(context: Context): BluetoothScanner = instance ?: synchronized(this) {
            instance ?: BluetoothScanner(context.applicationContext).also { instance = it }
        }
    }

    private val lock = Any()
    private val registry = ScanLeaseRegistry()
    private val leases = linkedMapOf<Long, ScanLease>()
    private val mutableDevices = MutableStateFlow<Map<String, ScannedDevice>>(emptyMap())
    val devices: StateFlow<Map<String, ScannedDevice>> = mutableDevices
    private val mutableStatus = MutableStateFlow(ScannerStatus())
    val status: StateFlow<ScannerStatus> = mutableStatus
    private val errors = linkedMapOf<String, String>()
    private var bleScanner: BluetoothLeScanner? = null
    private var bleCallback: ScanCallback? = null
    private var classicReceiver: BroadcastReceiver? = null
    private var classicGeneration = 0L
    private var bleRunning = false
    private var classicRunning = false

    private fun adapter(): BluetoothAdapter? = guarded("read Bluetooth adapter") {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    val hasPermissions: Boolean
        get() = hasPermission(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasPermission(Manifest.permission.BLUETOOTH_CONNECT))

    val isBluetoothEnabled: Boolean
        get() = synchronized(lock) { guarded("read Bluetooth enabled") { adapter()?.isEnabled == true } ?: false }

    fun acquire(owner: String, addresses: Set<String>? = null): ScanLease? = synchronized(lock) {
        val normalized = addresses?.map { it.uppercase() }?.toSet()
        if (normalized == null || normalized.isNotEmpty()) {
            if (!hasPermissions || !isBluetoothEnabled) {
                errors["availability"] = if (!hasPermissions) "Bluetooth permissions required" else "Bluetooth is off or unavailable"
                publishStatus()
                return@synchronized null
            }
        }
        errors.remove("availability")
        val lease = ScanLease(registry.acquire(normalized), owner, normalized == null || normalized.isNotEmpty())
        reconcile(explicitAcquire = normalized == null || normalized.isNotEmpty())
        // Startup can publish intermediate transport states. This lease begins after reconciliation.
        leases[lease.token] = lease
        lease.health.observe(mutableStatus.value)
        lease
    }

    fun release(lease: ScanLease) = synchronized(lock) {
        if (leases[lease.token] !== lease) return@synchronized
        leases.remove(lease.token)
        registry.release(lease.token)
        reconcile()
    }

    fun clearDevices() = synchronized(lock) { mutableDevices.value = emptyMap() }

    private fun reconcile(explicitAcquire: Boolean = false) {
        val interested = registry.hasInterest()
        val wanted = registry.effectiveAddresses()
        if (!interested) {
            stopBle()
            stopClassic()
        } else {
            if (registry.shouldStartBle(explicitAcquire)) {
                stopBle()
                startBle(wanted)
            }
            if (classicReceiver == null) startClassic()
        }
        publishStatus()
    }

    private fun startBle(addresses: Set<String>?) {
        val generation = registry.beginBleRegistration(addresses)
        errors.remove("ble")
        val driver = guarded("get BLE scanner") { adapter()?.bluetoothLeScanner }
        if (driver == null) {
            registry.failBleGeneration(generation)
            if (!errors.containsKey("ble")) errors["ble"] = "BLE scanner unavailable"
            return
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult): Unit = synchronized(lock) {
                if (!registry.acceptsBleGeneration(generation)) return@synchronized
                val receivedAt = SystemClock.elapsedRealtimeNanos()
                val observedAt = result.timestampNanos.coerceIn(0L, receivedAt)
                val ageMs = (receivedAt - observedAt) / 1_000_000L
                readDevice(result.device, result.rssi, true)?.let {
                    publishDevice(it.copy(timestamp = System.currentTimeMillis() - ageMs), observedAt)
                }
            }
            override fun onBatchScanResults(results: List<ScanResult>) { results.forEach { onScanResult(0, it) } }
            override fun onScanFailed(errorCode: Int): Unit = synchronized(lock) {
                if (!registry.failBleGeneration(generation)) return@synchronized
                bleRunning = false
                errors["ble"] = "BLE scan failed ($errorCode)"
                Log.w("BluetoothScanner", errors["ble"].orEmpty())
                publishStatus()
            }
        }
        bleScanner = driver
        bleCallback = callback
        val filters = addresses?.map { ScanFilter.Builder().setDeviceAddress(it).build() }
        bleRunning = guarded("start BLE scan") {
            driver.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            true
        } == true
        if (!bleRunning) registry.failBleGeneration(generation)
        if (!bleRunning && !errors.containsKey("ble")) errors["ble"] = "BLE scan could not start"
    }

    // guarded catches permission revocation during cleanup and reports operation plus cause.
    @SuppressLint("MissingPermission")
    private fun stopBle() {
        registry.restartBleGeneration()
        val callback = bleCallback
        bleCallback = null
        bleRunning = false
        if (callback != null) guarded("stop BLE scan") { bleScanner?.stopScan(callback) }
        bleScanner = null
    }

    private fun startClassic() {
        val generation = ++classicGeneration
        errors.remove("classic")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent): Unit = synchronized(lock) {
                if (generation != classicGeneration || !registry.hasInterest()) return@synchronized
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        device?.let { readDevice(it, rssi, false) }?.let { publishDevice(it) }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        classicRunning = false
                        startClassicDiscovery()
                        publishStatus()
                    }
                }
            }
        }
        classicReceiver = receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply { addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) }
        val registered = guarded("register classic receiver") {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            true
        } == true
        if (!registered) { classicReceiver = null; return }
        startClassicDiscovery()
    }

    // guarded catches permission revocation between lease acquisition and adapter access.
    @SuppressLint("MissingPermission")
    private fun startClassicDiscovery() {
        if (!registry.hasInterest()) return
        classicRunning = guarded("start classic discovery") {
            val driver = adapter() ?: return@guarded false
            driver.isDiscovering || driver.startDiscovery()
        } == true
        if (!classicRunning && !errors.containsKey("classic")) errors["classic"] = "Classic discovery could not start"
    }

    // Cleanup must still be attempted after revocation; guarded handles SecurityException.
    @SuppressLint("MissingPermission")
    private fun stopClassic() {
        val ownedDiscovery = classicReceiver != null
        classicGeneration++
        classicReceiver?.let { receiver ->
            try { context.unregisterReceiver(receiver) }
            catch (error: IllegalArgumentException) { Log.w("BluetoothScanner", "unregister classic receiver failed", error) }
        }
        classicReceiver = null
        classicRunning = false
        if (ownedDiscovery) guarded("cancel classic discovery") { adapter()?.cancelDiscovery() }
    }

    // Device getters race runtime permission changes; guarded logs and publishes that failure.
    @SuppressLint("MissingPermission")
    private fun readDevice(device: BluetoothDevice, rssi: Int, ble: Boolean): ScannedDevice? =
        guarded("read discovered device") { ScannedDevice(device.address.uppercase(), device.name.orEmpty(), rssi, ble) }

    private fun publishDevice(device: ScannedDevice, observedAtNanos: Long = SystemClock.elapsedRealtimeNanos()) {
        if (!hasPermissions) {
            errors["availability"] = "Bluetooth permissions were revoked"
            stopBle()
            stopClassic()
            publishStatus()
            return
        }
        val previous = mutableDevices.value[device.address]
        if (previous == null || device.timestamp >= previous.timestamp) {
            mutableDevices.value = mutableDevices.value + (device.address to device.copy(name = device.name.ifBlank { previous?.name.orEmpty() }))
        }
        leases.values.forEach { lease ->
            if (observedAtNanos >= lease.acquiredAtNanos && registry.accepts(lease.token, device.address)) {
                val old = lease.results.value[device.address]
                if (old == null || device.timestamp >= old.timestamp) {
                    lease.results.value = lease.results.value + (device.address to device.copy(name = device.name.ifBlank { old?.name.orEmpty() }))
                }
            }
        }
    }

    private fun publishStatus() {
        val snapshot = ScannerStatus(bleRunning || classicRunning, bleRunning, classicRunning,
            errors.values.takeIf { it.isNotEmpty() }?.joinToString("; "))
        mutableStatus.value = snapshot
        leases.values.forEach { it.health.observe(snapshot) }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private inline fun <T> guarded(operation: String, block: () -> T): T? = try {
        block().also { errors.remove(operation) }
    }
    catch (error: SecurityException) {
        Log.e("BluetoothScanner", "$operation denied", error)
        errors[operation] = "$operation: permission denied"
        bleRunning = false
        classicRunning = false
        publishStatus()
        null
    } catch (error: IllegalStateException) {
        Log.e("BluetoothScanner", "$operation failed", error)
        errors[operation] = "$operation: ${error.message ?: error.javaClass.simpleName}"
        publishStatus()
        null
    }
}
