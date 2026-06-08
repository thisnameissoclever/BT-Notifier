package com.btnotifier.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.btnotifier.data.PreferencesManager
import com.btnotifier.service.BluetoothScanService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ValidationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_CONFIGURE_WIFI_GATE -> configureWifiGate(appContext, intent)
                    ACTION_SCAN_NOW -> startScanNow(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun configureWifiGate(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)
        val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
        val selectedSsid = intent.getStringExtra(EXTRA_SSID).orEmpty()
        val scanDurationSeconds = intent.getIntExtra(EXTRA_SCAN_DURATION_SECONDS, 5)
            .coerceIn(5, 60)

        prefs.setServiceEnabled(true)
        prefs.setScanDurationSeconds(scanDurationSeconds)
        prefs.setWifiScanGateEnabled(false)
        prefs.setSelectedWifiSsid(selectedSsid)
        prefs.setWifiScanGateEnabled(enabled)

        Log.i(TAG, "Configured Wi-Fi gate for validation: enabled=$enabled selected=$selectedSsid")
        startScanNow(context)
    }

    private fun startScanNow(context: Context) {
        Log.i(TAG, "Requested validation scan")
        ContextCompat.startForegroundService(
            context,
            Intent(context, BluetoothScanService::class.java).apply {
                action = BluetoothScanService.ACTION_SCAN_NOW
            }
        )
    }

    private companion object {
        const val TAG = "BTValidation"
        const val ACTION_CONFIGURE_WIFI_GATE = "com.btnotifier.debug.CONFIGURE_WIFI_GATE"
        const val ACTION_SCAN_NOW = "com.btnotifier.debug.SCAN_NOW"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_SSID = "ssid"
        const val EXTRA_SCAN_DURATION_SECONDS = "scan_duration_seconds"
    }
}
