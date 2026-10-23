package com.btnotifier.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.asFlow
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.DetectionSource
import com.btnotifier.data.PreferencesManager
import com.btnotifier.data.history.ObservationRepository
import com.btnotifier.device.DeviceDisplayName
import com.btnotifier.network.WifiNetworkGate
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.scanner.ScanLease
import com.btnotifier.scanner.ScanCycleOutcome
import com.btnotifier.scanner.ScannedDevice
import com.btnotifier.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class BluetoothScanService : Service() {
    companion object {
        const val ACTION_START = "com.btnotifier.START_SCAN"
        const val ACTION_STOP = "com.btnotifier.STOP_SCAN"
        const val ACTION_SCAN_NOW = "com.btnotifier.SCAN_NOW"
    }

    private data class Config(
        val enabled: Boolean,
        val interval: Int,
        val duration: Int,
        val wifi: Boolean,
        val ssid: String,
        val addresses: Set<String>
    )

    // Main serializes commands, lease revocation, completions and notification delivery.
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val scanRequests = Channel<Unit>(Channel.CONFLATED)
    private val cycles = CyclePolicy()
    private val alerts = AlertPolicy()
    private val lastAlerts = mutableMapOf<String, Long>()
    private lateinit var scanner: BluetoothScanner
    private lateinit var prefs: PreferencesManager
    private lateinit var db: AppDatabase
    private lateinit var wifiGate: WifiNetworkGate
    private var activeLease: ScanLease? = null
    private var started = false
    private var cycleRunning = false
    private var visibleStart = false
    private var foregroundTypes = 0

    override fun onCreate() {
        super.onCreate()
        scanner = BluetoothScanner.getInstance(this)
        prefs = PreferencesManager(this)
        db = AppDatabase.getInstance(this)
        wifiGate = WifiNetworkGate(this)
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMonitoring()
            return START_NOT_STICKY
        }
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        if (intent?.getBooleanExtra(MonitoringServiceStarter.EXTRA_USER_INITIATED, false) == true) visibleStart = true
        if (started) {
            if (intent?.action == ACTION_SCAN_NOW && !cycleRunning) scanRequests.trySend(Unit)
            return START_STICKY
        }
        // A new process never inherits previous while-in-use location eligibility.
        visibleStart = intent?.getBooleanExtra(MonitoringServiceStarter.EXTRA_USER_INITIATED, false) == true
        if (!enterForeground(baseTypes(), "Starting monitoring")) {
            scope.launch {
                MonitoringServiceStarter.requestResume(applicationContext, "Android could not start monitoring. Open the app to resume.")
                stopMonitoring("Open the app to resume monitoring")
            }
            return START_NOT_STICKY
        }
        started = true
        MonitoringStatus.update(MonitoringSnapshot(true, "Starting"))
        scope.launch {
            try {
                val timing = combine(prefs.serviceEnabled, prefs.scanIntervalMinutes, prefs.scanDurationSeconds) { enabled, interval, duration ->
                    Triple(enabled, interval.coerceAtLeast(1), duration.coerceAtLeast(1))
                }
                val gate = combine(prefs.wifiScanGateEnabled, prefs.selectedWifiSsid) { enabled, ssid -> enabled to ssid }
                combine(timing, gate, db.watchedDeviceDao().getAllLive().asFlow()) { time, wifi, watched ->
                    Config(time.first, time.second, time.third, wifi.first, wifi.second, watched.map { it.address.uppercase() }.toSet())
                }.distinctUntilChanged().collectLatest { config ->
                    if (!config.enabled) { stopMonitoring(); return@collectLatest }
                    if (config.wifi && !visibleStart) {
                        val reason = "Open the app to resume Wi-Fi-gated monitoring after restart."
                        MonitoringServiceStarter.requestResume(applicationContext, reason)
                        stopMonitoring(reason)
                        return@collectLatest
                    }
                    if (config.wifi && !wifiGate.hasLocationForegroundPrerequisites()) {
                        val reason = "Wi-Fi monitoring needs Location and Wi-Fi permissions with Location enabled. Open the app to resume."
                        MonitoringServiceStarter.requestResume(applicationContext, reason)
                        stopMonitoring(reason)
                        return@collectLatest
                    }
                    if (!enterForeground(if (config.wifi) wifiTypes() else baseTypes(), "Monitoring ready")) {
                        val reason = "Open the app to enable Wi-Fi monitoring."
                        MonitoringServiceStarter.requestResume(applicationContext, reason)
                        stopMonitoring(reason)
                        return@collectLatest
                    }
                    prefs.setMonitoringResumeRequired(false, "")
                    NotificationHelper.cancelResumeNotification(applicationContext)
                    runSchedule(config)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                Log.e("BTScanService", "monitoring configuration or schedule failed", error)
                val reason = "Monitoring stopped: ${error.message ?: error.javaClass.simpleName}"
                MonitoringServiceStarter.requestResume(applicationContext, reason)
                stopMonitoring(reason)
            }
        }
        return START_STICKY
    }

    private suspend fun runSchedule(config: Config) {
        try {
            while (currentCoroutineContext().isActive && started) {
                // Requests received during a cycle coalesce into the cycle already in progress.
                while (scanRequests.tryReceive().isSuccess) { /* discard duplicate wakeups */ }
                val began = System.currentTimeMillis()
                performCycle(config)
                currentCoroutineContext().ensureActive()
                val next = cycles.nextScanAt(began, config.interval * 60_000L, System.currentTimeMillis())
                val prior = MonitoringStatus.state.value
                MonitoringStatus.update(prior.copy(nextScanAt = next))
                withTimeoutOrNull((next - System.currentTimeMillis()).coerceAtLeast(1L)) { scanRequests.receive() }
            }
        } finally {
            revokeLease()
        }
    }

    private suspend fun performCycle(config: Config) {
        val token = cycles.begin()
        cycleRunning = true
        try {
            if (config.addresses.isEmpty()) {
                publish("No watched devices")
                return
            }
            if (!gateAllows(config)) return
            currentCoroutineContext().ensureActive()
            if (!started || !cycles.isCurrent(token)) return
            val lease = scanner.acquire("background", config.addresses)
            if (lease == null) {
                publish("Blocked", scanner.status.value.error ?: "Bluetooth unavailable")
                return
            }
            activeLease = lease
            publish(if (scanner.status.value.isScanning) "Scanning" else "Blocked", scanner.status.value.error)
            if (scanner.status.value.isScanning) delay(config.duration * 1000L)
            currentCoroutineContext().ensureActive()
            if (!cycles.isCurrent(token) || !started || !gateAllows(config)) return
            // Snapshot health before release, whose normal transport shutdown is not a scan failure.
            val completion = lease.health.finish(lease.devices.value.values)
            val results = completion.observations
            scanner.release(lease)
            activeLease = null
            // Persist actual observations before any delivery preference or cooldown decision.
            ObservationRepository.get(applicationContext).record(results, DetectionSource.BACKGROUND)
            currentCoroutineContext().ensureActive()
            if (!cycles.isCurrent(token) || !started) return
            if (completion.outcome != ScanCycleOutcome.COMPLETED) {
                publish(if (completion.outcome == ScanCycleOutcome.NO_TARGETS) "No watched devices" else "Blocked", completion.error)
                return
            }
            prefs.recordBackgroundScan(System.currentTimeMillis(), results.size)
            evaluateAlerts(results, token)
            currentCoroutineContext().ensureActive()
            if (started && cycles.isCurrent(token)) publish("Waiting", completion.error)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            Log.e("BTScanService", "background scan cycle failed", error)
            if (started && cycles.isCurrent(token)) publish("Blocked", "Scan failed: ${error.message ?: error.javaClass.simpleName}")
        } finally {
            revokeLease()
            cycleRunning = false
        }
    }

    private suspend fun gateAllows(config: Config): Boolean {
        if (!config.wifi) return true
        val decision = withContext(Dispatchers.IO) { wifiGate.evaluate(true, config.ssid) }
        currentCoroutineContext().ensureActive()
        if (decision.canScan) return true
        val message = when (decision.blockedReason) {
            WifiNetworkGate.BlockedReason.NO_SELECTED_NETWORK -> "Select Wi-Fi in Settings"
            WifiNetworkGate.BlockedReason.MISSING_WIFI_PERMISSION -> "Waiting for Wi-Fi permissions"
            WifiNetworkGate.BlockedReason.LOCATION_DISABLED -> "Waiting for Location to check Wi-Fi"
            WifiNetworkGate.BlockedReason.WIFI_DISABLED -> "Wi-Fi is off - waiting"
            else -> "Waiting for selected Wi-Fi"
        }
        if (started) publish("Blocked", message)
        return false
    }

    private suspend fun evaluateAlerts(results: List<ScannedDevice>, token: Long) {
        val notifyEnabled = prefs.notifyOnFind.first()
        val watched = db.watchedDeviceDao().getAll()
        val byAddress = results.associateBy { it.address }
        for (device in watched) {
            currentCoroutineContext().ensureActive()
            if (!started || !cycles.isCurrent(token)) return
            val found = byAddress[device.address.uppercase()] ?: continue
            val now = System.currentTimeMillis()
            if (!alerts.shouldAlert(notifyEnabled, device.alertEnabled, lastAlerts[device.address], now)) continue
            val metadata = db.deviceMetadataDao().getByAddress(device.address)
            currentCoroutineContext().ensureActive()
            if (!started || !cycles.isCurrent(token)) return
            val name = DeviceDisplayName.resolve(metadata?.customName, found.name,
                metadata?.lastSeenName ?: device.name, device.address)
            try {
                NotificationHelper.sendDeviceFoundAlert(applicationContext, name, device.address)
                lastAlerts[device.address] = now
            } catch (error: SecurityException) {
                Log.e("BTScanService", "post watched-device alert denied", error)
            }
        }
    }

    private fun revokeLease() {
        cycles.cancel()
        activeLease?.let { scanner.release(it) }
        activeLease = null
    }

    private fun publish(phase: String, error: String? = null) {
        if (!started) return
        MonitoringStatus.update(MonitoringSnapshot(true, phase, error = error))
        val message = error ?: when (phase) { "Waiting" -> "Waiting for next scan"; else -> phase }
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(
                NotificationHelper.NOTIFICATION_ID_SERVICE, NotificationHelper.buildServiceNotification(this, message))
        } catch (failure: SecurityException) { Log.e("BTScanService", "update monitoring notification denied", failure) }
    }

    private fun enterForeground(types: Int, text: String): Boolean {
        if (foregroundTypes == types && started) return true
        val notification: Notification = NotificationHelper.buildServiceNotification(this, text)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification, types)
            } else startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
            foregroundTypes = types
            true
        } catch (error: SecurityException) {
            Log.e("BTScanService", "start foreground service types=$types permission denied", error)
            false
        } catch (error: IllegalStateException) {
            Log.e("BTScanService", "start foreground service types=$types restricted by Android", error)
            false
        }
    }

    private fun baseTypes(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
    private fun wifiTypes(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) baseTypes() or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0

    private fun stopMonitoring(error: String? = null) {
        started = false
        scope.cancel()
        revokeLease()
        MonitoringStatus.update(MonitoringSnapshot(false, if (error == null) "Stopped" else "Needs resume", error = error))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else { @Suppress("DEPRECATION") stopForeground(true) }
        foregroundTypes = 0
        stopSelf()
    }

    override fun onDestroy() {
        started = false
        scope.cancel()
        revokeLease()
        if (MonitoringStatus.state.value.running) MonitoringStatus.update(MonitoringSnapshot())
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
