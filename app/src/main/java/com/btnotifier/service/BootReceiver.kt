package com.btnotifier.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.btnotifier.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        val prefs = PreferencesManager(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = prefs.serviceEnabled.first()
                if (enabled) {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, BluetoothScanService::class.java).apply {
                            action = BluetoothScanService.ACTION_START
                        }
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
