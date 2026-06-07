package com.btnotifier.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

class WifiNetworkGate(private val context: Context) {

    enum class BlockedReason {
        NO_SELECTED_NETWORK,
        MISSING_LOCATION_PERMISSION,
        LOCATION_DISABLED,
        WIFI_DISABLED,
        TARGET_NOT_FOUND
    }

    data class Decision(
        val canScan: Boolean,
        val blockedReason: BlockedReason?
    )

    data class AvailableNetworksResult(
        val ssids: List<String>,
        val blockedReason: BlockedReason?
    )

    private val wifiManager: WifiManager?
        get() = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun evaluate(gateEnabled: Boolean, selectedSsid: String): Decision {
        return evaluate(
            gateEnabled = gateEnabled,
            selectedSsid = selectedSsid,
            currentSsid = currentSsid(),
            visibleSsids = availableSsids(requestScan = true).ssids,
            hasLocationPermission = hasLocationPermission(),
            locationEnabled = isLocationEnabled(),
            wifiEnabled = wifiManager?.isWifiEnabled == true
        )
    }

    fun availableSsids(requestScan: Boolean): AvailableNetworksResult {
        if (!hasLocationPermission()) {
            return AvailableNetworksResult(emptyList(), BlockedReason.MISSING_LOCATION_PERMISSION)
        }
        if (!isLocationEnabled()) {
            return AvailableNetworksResult(emptyList(), BlockedReason.LOCATION_DISABLED)
        }
        val manager = wifiManager ?: return AvailableNetworksResult(emptyList(), BlockedReason.WIFI_DISABLED)
        if (!manager.isWifiEnabled) {
            return AvailableNetworksResult(emptyList(), BlockedReason.WIFI_DISABLED)
        }

        if (requestScan) {
            try {
                @Suppress("DEPRECATION")
                manager.startScan()
            } catch (_: SecurityException) {
                return AvailableNetworksResult(emptyList(), BlockedReason.MISSING_LOCATION_PERMISSION)
            }
        }

        val networks = mutableSetOf<String>()
        currentSsid()?.let { networks.add(it) }
        try {
            @Suppress("DEPRECATION")
            manager.scanResults
                .mapNotNull { normalizeSsid(it.SSID) }
                .forEach { networks.add(it) }
        } catch (_: SecurityException) {
            return AvailableNetworksResult(emptyList(), BlockedReason.MISSING_LOCATION_PERMISSION)
        }

        return AvailableNetworksResult(networks.sorted(), null)
    }

    private fun currentSsid(): String? {
        if (!hasLocationPermission()) return null
        val manager = wifiManager ?: return null
        return try {
            val ssid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNetwork = connectivityManager?.activeNetwork
                val capabilities = connectivityManager?.getNetworkCapabilities(activeNetwork)
                val wifiInfo = capabilities
                    ?.takeIf { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
                    ?.transportInfo as? WifiInfo
                wifiInfo?.ssid
            } else {
                @Suppress("DEPRECATION")
                manager.connectionInfo?.ssid
            }
            normalizeSsid(ssid)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    companion object {
        private const val UNKNOWN_SSID = "<unknown ssid>"

        fun normalizeSsid(rawSsid: String?): String? {
            val trimmed = rawSsid?.trim().orEmpty()
            if (trimmed.isBlank() || trimmed.equals(UNKNOWN_SSID, ignoreCase = true)) return null

            val unquoted = if (trimmed.length >= 2 && trimmed.first() == '"' && trimmed.last() == '"') {
                trimmed.substring(1, trimmed.length - 1).trim()
            } else {
                trimmed
            }
            return unquoted.takeIf { it.isNotBlank() && !it.equals(UNKNOWN_SSID, ignoreCase = true) }
        }

        fun evaluate(
            gateEnabled: Boolean,
            selectedSsid: String,
            currentSsid: String?,
            visibleSsids: List<String>,
            hasLocationPermission: Boolean,
            locationEnabled: Boolean,
            wifiEnabled: Boolean
        ): Decision {
            if (!gateEnabled) return Decision(canScan = true, blockedReason = null)

            val normalizedSelected = normalizeSsid(selectedSsid)
                ?: return Decision(canScan = false, blockedReason = BlockedReason.NO_SELECTED_NETWORK)

            if (!hasLocationPermission) {
                return Decision(canScan = false, blockedReason = BlockedReason.MISSING_LOCATION_PERMISSION)
            }
            if (!locationEnabled) {
                return Decision(canScan = false, blockedReason = BlockedReason.LOCATION_DISABLED)
            }
            if (!wifiEnabled) {
                return Decision(canScan = false, blockedReason = BlockedReason.WIFI_DISABLED)
            }

            val normalizedCurrent = normalizeSsid(currentSsid)
            val normalizedVisible = visibleSsids.mapNotNull { normalizeSsid(it) }.toSet()
            val matched = normalizedCurrent == normalizedSelected || normalizedSelected in normalizedVisible

            return if (matched) {
                Decision(canScan = true, blockedReason = null)
            } else {
                Decision(canScan = false, blockedReason = BlockedReason.TARGET_NOT_FOUND)
            }
        }
    }
}
