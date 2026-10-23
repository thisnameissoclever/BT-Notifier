package com.btnotifier.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.btnotifier.data.PreferencesManager
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.utils.NotificationHelper
import kotlinx.coroutines.flow.first

object MonitoringServiceStarter {
    const val EXTRA_USER_INITIATED = "user_initiated"

    /** Call from a visible Activity for Wi-Fi/location foreground-service eligibility. */
    suspend fun start(context: Context, userInitiated: Boolean): Boolean {
        val app = context.applicationContext
        val prefs = PreferencesManager(app)
        if (!prefs.serviceEnabled.first()) return false
        val reason = when {
            !BluetoothScanner.getInstance(app).hasPermissions -> "Bluetooth permissions are required. Open the app to resume monitoring."
            prefs.wifiScanGateEnabled.first() && !userInitiated -> "Open the app to resume Wi-Fi-gated monitoring after restart."
            else -> null
        }
        if (reason != null) {
            requestResume(app, reason)
            return false
        }
        return try {
            ContextCompat.startForegroundService(app, Intent(app, BluetoothScanService::class.java).apply {
                action = BluetoothScanService.ACTION_START
                putExtra(EXTRA_USER_INITIATED, userInitiated)
            })
            true
        } catch (error: SecurityException) {
            Log.e("MonitoringStarter", "start foreground monitoring permission denied", error)
            requestResume(app, "Monitoring permission was denied. Open the app to review permissions.")
            false
        } catch (error: IllegalStateException) {
            Log.e("MonitoringStarter", "start foreground monitoring restricted by Android", error)
            requestResume(app, "Android restricted this background start. Open the app to resume monitoring.")
            false
        }
    }

    internal suspend fun requestResume(context: Context, reason: String) {
        PreferencesManager(context).setMonitoringResumeRequired(true, reason)
        NotificationHelper.showResumeNotification(context, reason)
    }
}
