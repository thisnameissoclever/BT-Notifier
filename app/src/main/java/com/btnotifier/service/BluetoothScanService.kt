package com.btnotifier.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.DeviceMetadata
import com.btnotifier.data.PreferencesManager
import com.btnotifier.device.DeviceDisplayName
import com.btnotifier.network.WifiNetworkGate
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.utils.NotificationHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class BluetoothScanService : Service() {

    companion object {
        private const val TAG = "BTScanService"
        const val ACTION_START = "com.btnotifier.START_SCAN"
        const val ACTION_STOP = "com.btnotifier.STOP_SCAN"

        // Alert cooldown: don't re-notify for the same device within this window
        private const val ALERT_COOLDOWN_MS = 5 * 60 * 1000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var scanner: BluetoothScanner
    private lateinit var prefs: PreferencesManager
    private lateinit var db: AppDatabase
    private lateinit var wifiNetworkGate: WifiNetworkGate

    // address -> timestamp of last alert sent
    private val lastAlertTime = mutableMapOf<String, Long>()

    private var scanDurationSeconds = PreferencesManager.DEFAULT_SCAN_DURATION
    private var scanIntervalMinutes = PreferencesManager.DEFAULT_SCAN_INTERVAL
    private var wifiScanGateEnabled = false
    private var selectedWifiSsid = ""
    private var prefsAndScheduleStarted = false

    private val periodicScanRunnable = object : Runnable {
        override fun run() {
            performScan()
            val intervalMs = scanIntervalMinutes * 60 * 1000L
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        scanner = BluetoothScanner(applicationContext)
        prefs = PreferencesManager(applicationContext)
        db = AppDatabase.getInstance(applicationContext)
        wifiNetworkGate = WifiNetworkGate(applicationContext)
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
        }

        val notification = NotificationHelper.buildServiceNotification(this, "Bluetooth scanning active")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot start foreground service - Bluetooth permission not granted", e)
            stopSelf()
            return START_NOT_STICKY
        }

        loadPrefsAndSchedule()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(periodicScanRunnable)
        scanner.stopScan()
        serviceScope.cancel()
    }

    private fun loadPrefsAndSchedule() {
        if (prefsAndScheduleStarted) return
        prefsAndScheduleStarted = true

        serviceScope.launch {
            scanIntervalMinutes = prefs.scanIntervalMinutes.first()
            scanDurationSeconds = prefs.scanDurationSeconds.first()
            wifiScanGateEnabled = prefs.wifiScanGateEnabled.first()
            selectedWifiSsid = prefs.selectedWifiSsid.first()

            // Watch for preference changes
            launch {
                prefs.scanIntervalMinutes.collect { minutes ->
                    scanIntervalMinutes = minutes
                }
            }
            launch {
                prefs.scanDurationSeconds.collect { seconds ->
                    scanDurationSeconds = seconds
                }
            }
            launch {
                prefs.wifiScanGateEnabled.collect { enabled ->
                    wifiScanGateEnabled = enabled
                }
            }
            launch {
                prefs.selectedWifiSsid.collect { ssid ->
                    selectedWifiSsid = ssid
                }
            }

            // Start the first scan immediately, then schedule
            handler.post(periodicScanRunnable)
        }
    }

    private fun performScan() {
        serviceScope.launch {
            val wifiDecision = if (wifiScanGateEnabled) {
                wifiNetworkGate.evaluate(
                    gateEnabled = true,
                    selectedSsid = selectedWifiSsid
                )
            } else {
                null
            }

            withContext(Dispatchers.Main) {
                if (wifiDecision != null && !wifiDecision.canScan) {
                    updateNotification(wifiBlockedNotificationText(wifiDecision.blockedReason))
                    return@withContext
                }
                performBluetoothScan()
            }
        }
    }

    private fun performBluetoothScan() {
        if (!scanner.isBluetoothEnabled) {
            updateNotification("Bluetooth is off - waiting")
            return
        }

        updateNotification("Scanning for devices...")
        scanner.startScan()

        // Stop scan after duration and check results
        handler.postDelayed({
            scanner.stopScan()
            checkForWatchedDevices()
            updateNotification("Next scan in $scanIntervalMinutes min")
        }, scanDurationSeconds * 1000L)
    }

    private fun wifiBlockedNotificationText(reason: WifiNetworkGate.BlockedReason?): String {
        return when (reason) {
            WifiNetworkGate.BlockedReason.NO_SELECTED_NETWORK -> "Select Wi-Fi in Settings"
            WifiNetworkGate.BlockedReason.MISSING_LOCATION_PERMISSION -> "Waiting for Wi-Fi permission"
            WifiNetworkGate.BlockedReason.LOCATION_DISABLED -> "Waiting for Location to check Wi-Fi"
            WifiNetworkGate.BlockedReason.WIFI_DISABLED -> "Wi-Fi is off - waiting"
            WifiNetworkGate.BlockedReason.TARGET_NOT_FOUND, null -> "Waiting for selected Wi-Fi"
        }
    }

    private fun checkForWatchedDevices() {
        serviceScope.launch {
            val shouldNotify = prefs.notifyOnFind.first()
            if (!shouldNotify) return@launch

            val watchedDevices = db.watchedDeviceDao().getAll()
            if (watchedDevices.isEmpty()) return@launch

            val scanned = scanner.devices.value
            val now = System.currentTimeMillis()

            for (watched in watchedDevices) {
                if (!watched.alertEnabled) continue
                val found = scanned[watched.address] ?: continue

                val lastAlert = lastAlertTime[watched.address] ?: 0L
                if (now - lastAlert < ALERT_COOLDOWN_MS) continue

                lastAlertTime[watched.address] = now
                db.watchedDeviceDao().updateLastSeen(watched.address, now)
                rememberActualName(watched.address, found.name)

                val metadata = db.deviceMetadataDao().getByAddress(watched.address)
                val name = DeviceDisplayName.resolve(
                    alias = metadata?.customName,
                    primaryName = found.name,
                    secondaryName = metadata?.lastSeenName ?: watched.name.takeIf { it.isNotBlank() },
                    fallback = watched.address
                )
                Log.i(TAG, "Watched device found: $name (${watched.address})")
                NotificationHelper.sendDeviceFoundAlert(applicationContext, name, watched.address)
            }
        }
    }

    private suspend fun rememberActualName(address: String, actualName: String?) {
        val normalizedActualName = DeviceDisplayName.normalizeActualName(actualName) ?: return
        val metaDao = db.deviceMetadataDao()
        val existing = metaDao.getByAddress(address)
        if (existing != null) {
            metaDao.setLastSeenName(address, normalizedActualName)
        } else {
            metaDao.upsert(DeviceMetadata(address, lastSeenName = normalizedActualName))
        }
        db.watchedDeviceDao().updateName(address, normalizedActualName)
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.buildServiceNotification(this, text)
        )
    }

    private fun stopForegroundService() {
        handler.removeCallbacks(periodicScanRunnable)
        scanner.stopScan()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
}
