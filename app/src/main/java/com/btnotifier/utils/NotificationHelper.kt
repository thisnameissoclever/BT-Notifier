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

    const val ACTION_RESUME_MONITORING = "com.btnotifier.RESUME_MONITORING"
    private const val NOTIFICATION_ID_RESUME = 1002
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

    fun showResumeNotification(context: Context, reason: String) {
        createChannels(context)
        val open = PendingIntent.getActivity(context, 1002,
            Intent(context, MainActivity::class.java).setAction(ACTION_RESUME_MONITORING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("Resume Bluetooth monitoring")
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setSmallIcon(R.drawable.ic_bluetooth)
            .setContentIntent(open)
            .addAction(R.drawable.ic_bluetooth, "Resume", open)
            .setAutoCancel(true)
            .build()
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID_RESUME, notification)
        } catch (error: SecurityException) {
            android.util.Log.e("NotificationHelper", "post resume notification denied", error)
        }
    }

    fun cancelResumeNotification(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID_RESUME)
    }

    fun sendDeviceFoundAlert(context: Context, deviceDisplayName: String, deviceAddress: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val displayName = deviceDisplayName.ifBlank { deviceAddress }
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
