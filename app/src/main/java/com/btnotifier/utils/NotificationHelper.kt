package com.btnotifier.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.btnotifier.MainActivity
import com.btnotifier.R

object NotificationHelper {

    const val CHANNEL_SERVICE = "bt_scan_service"
    const val CHANNEL_ALERT = "bt_device_found"
    const val NOTIFICATION_ID_SERVICE = 1001
    private const val NOTIFICATION_ID_ALERT_BASE = 2000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "BT Scanner Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification while Bluetooth scanning is active"
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            "Device Found Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a watched Bluetooth device is detected"
        }

        nm.createNotificationChannels(listOf(serviceChannel, alertChannel))
    }

    fun buildServiceNotification(context: Context, statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun sendDeviceFoundAlert(context: Context, deviceName: String, deviceAddress: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val displayName = deviceName.ifBlank { deviceAddress }
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setContentTitle("Watched device found!")
            .setContentText(displayName)
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val id = NOTIFICATION_ID_ALERT_BASE + deviceAddress.hashCode()
        nm.notify(id, notification)
    }
}
