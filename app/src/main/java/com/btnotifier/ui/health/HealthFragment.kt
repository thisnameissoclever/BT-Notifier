package com.btnotifier.ui.health

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.btnotifier.MainActivity
import com.btnotifier.R
import com.btnotifier.data.PreferencesManager
import com.btnotifier.databinding.FragmentHealthBinding
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.service.MonitoringStatus
import com.btnotifier.utils.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class HealthFragment : Fragment() {
    private var _binding: FragmentHealthBinding? = null
    private val binding get() = _binding!!
    private var enabled = false
    private var lastScanAt = 0L
    private var lastScanCount = 0
    private var resumeRequired = false
    private var resumeMessage = ""
    private var wifiGate = false
    private var selectedSsid = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHealthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val preferences = PreferencesManager(requireContext().applicationContext)
        binding.btnResume.setOnClickListener {
            (requireActivity() as MainActivity).startBackgroundServiceIfPermitted()
        }
        binding.btnPermissions.setOnClickListener { openAppSettings() }
        binding.btnBluetooth.setOnClickListener { openSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
        binding.btnNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName))
            } else openAppSettings()
        }
        binding.btnBattery.setOnClickListener { openSettings(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        binding.btnLocation.setOnClickListener { openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
        binding.btnWifi.setOnClickListener { openSettings(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { preferences.serviceEnabled.collect { enabled = it; render() } }
                launch { preferences.lastBackgroundScanAt.collect { lastScanAt = it; render() } }
                launch { preferences.lastBackgroundDeviceCount.collect { lastScanCount = it; render() } }
                launch { preferences.monitoringResumeRequired.collect { resumeRequired = it; render() } }
                launch { preferences.monitoringMessage.collect { resumeMessage = it; render() } }
                launch { preferences.wifiScanGateEnabled.collect { wifiGate = it; render() } }
                launch { preferences.selectedWifiSsid.collect { selectedSsid = it; render() } }
                launch { MonitoringStatus.state.collect { render() } }
                launch {
                    while (true) {
                        render()
                        delay(3_000L)
                    }
                }
            }
        }
    }

    private fun render() {
        if (_binding == null) return
        val context = requireContext()
        val snapshot = MonitoringStatus.state.value
        val scanner = BluetoothScanner.getInstance(context)
        binding.tvMonitoring.text = listOf(
            getString(R.string.health_service, getString(if (snapshot.running) R.string.health_running else R.string.health_stopped)),
            getString(R.string.health_preference, enabledText(enabled)),
            getString(R.string.health_phase, snapshot.phase),
            getString(R.string.health_next, snapshot.nextScanAt?.let(::formatTime) ?: getString(R.string.health_not_scheduled))
        ).joinToString("\n")
        binding.tvCompleted.text = getString(R.string.health_last_scan,
            if (lastScanAt > 0L) formatTime(lastScanAt) else getString(R.string.health_never_scanned)) +
            "\n" + getString(R.string.health_last_count, lastScanCount)
        binding.tvResume.text = when {
            !enabled -> getString(R.string.health_enable_first)
            resumeRequired -> getString(R.string.health_resume_required, resumeMessage)
            snapshot.error != null -> snapshot.error
            else -> getString(R.string.health_no_resume)
        }
        binding.btnResume.isEnabled = enabled
        val bluetoothStatus = getString(R.string.health_bluetooth_permission,
            grantedText(scanner.hasPermissions)) + "\n" +
            getString(R.string.health_bluetooth_state, enabledText(scanner.isBluetoothEnabled))
        binding.tvBluetooth.text = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            bluetoothStatus + "\n" + getString(R.string.health_location_services, enabledText(locationEnabled(context)))
        } else bluetoothStatus
        binding.tvNotifications.text = notificationStatus(context)
        binding.tvWifi.text = wifiStatus(context)
        binding.btnWifi.visibility = if (wifiGate) View.VISIBLE else View.GONE
        binding.btnLocation.visibility = if (wifiGate || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) View.VISIBLE else View.GONE
        binding.tvBattery.text = batteryStatus(context)
    }

    private fun notificationStatus(context: Context): String {
        val runtimePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        val allowed = runtimePermission && NotificationManagerCompat.from(context).areNotificationsEnabled()
        val lines = mutableListOf(getString(R.string.health_notifications,
            getString(if (allowed) R.string.health_allowed else R.string.health_blocked)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            for ((channelId, title) in listOf(
                NotificationHelper.CHANNEL_SERVICE to R.string.health_service_channel,
                NotificationHelper.CHANNEL_ALERT to R.string.health_alert_channel
            )) {
                val channel = manager?.getNotificationChannel(channelId)
                val groupBlocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && channel?.group?.let {
                    manager?.getNotificationChannelGroup(it)?.isBlocked
                } == true
                val value = when {
                    channel == null -> R.string.health_channel_uncreated
                    !allowed || groupBlocked || channel.importance == NotificationManager.IMPORTANCE_NONE -> R.string.health_blocked
                    else -> R.string.health_allowed
                }
                lines.add(getString(R.string.health_channel, getString(title), getString(value)))
            }
        } else lines.add(getString(R.string.health_channels_legacy))
        return lines.joinToString("\n")
    }

    private fun wifiStatus(context: Context): String {
        if (!wifiGate) return getString(R.string.health_wifi_off)
        val permissions = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES))
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiEnabled = try { wifi?.isWifiEnabled == true } catch (_: SecurityException) { false }
        return getString(R.string.health_wifi_status,
            selectedSsid.ifBlank { getString(R.string.health_wifi_unselected) },
            grantedText(permissions), enabledText(locationEnabled(context)), enabledText(wifiEnabled))
    }

    private fun locationEnabled(context: Context): Boolean {
        val location = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) location?.isLocationEnabled == true
            else location?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                location?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        } catch (_: SecurityException) { false }
    }

    private fun batteryStatus(context: Context): String {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val optimized = when (power?.isIgnoringBatteryOptimizations(context.packageName)) {
            true -> R.string.health_unrestricted
            false -> R.string.health_optimized
            null -> R.string.health_unknown
        }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val restriction = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when (activityManager?.isBackgroundRestricted) {
                true -> R.string.health_restricted
                false -> R.string.health_not_restricted
                null -> R.string.health_unknown
            }
        } else R.string.health_not_restricted
        return getString(R.string.health_battery, getString(optimized), getString(restriction))
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
    private fun grantedText(granted: Boolean) = getString(if (granted) R.string.health_granted else R.string.health_missing)
    private fun enabledText(enabled: Boolean) = getString(if (enabled) R.string.health_enabled else R.string.health_disabled)
    private fun formatTime(at: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(at))
    private fun openAppSettings() = openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${requireContext().packageName}")))

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.feature_settings_unavailable, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(requireContext(), R.string.feature_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
