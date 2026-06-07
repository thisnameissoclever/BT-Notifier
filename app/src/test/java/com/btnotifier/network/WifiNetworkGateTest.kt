package com.btnotifier.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiNetworkGateTest {

    @Test
    fun normalizeSsidTrimsAndroidQuotesAndRejectsUnknownValues() {
        assertEquals("Home WiFi", WifiNetworkGate.normalizeSsid("\"Home WiFi\""))
        assertEquals("Cafe WiFi", WifiNetworkGate.normalizeSsid(" Cafe WiFi "))
        assertNull(WifiNetworkGate.normalizeSsid(""))
        assertNull(WifiNetworkGate.normalizeSsid("\"\""))
        assertNull(WifiNetworkGate.normalizeSsid("<unknown ssid>"))
    }

    @Test
    fun evaluateAllowsScanWhenGateIsDisabled() {
        val decision = WifiNetworkGate.evaluate(
            gateEnabled = false,
            selectedSsid = "",
            currentSsid = null,
            visibleSsids = emptyList(),
            hasLocationPermission = false,
            locationEnabled = false,
            wifiEnabled = false
        )

        assertTrue(decision.canScan)
        assertNull(decision.blockedReason)
    }

    @Test
    fun evaluateAllowsScanWhenConnectedSsidMatchesSelectedSsid() {
        val decision = WifiNetworkGate.evaluate(
            gateEnabled = true,
            selectedSsid = "Home WiFi",
            currentSsid = "\"Home WiFi\"",
            visibleSsids = emptyList(),
            hasLocationPermission = true,
            locationEnabled = true,
            wifiEnabled = true
        )

        assertTrue(decision.canScan)
        assertNull(decision.blockedReason)
    }

    @Test
    fun evaluateAllowsScanWhenVisibleSsidMatchesSelectedSsid() {
        val decision = WifiNetworkGate.evaluate(
            gateEnabled = true,
            selectedSsid = "Home WiFi",
            currentSsid = "\"Other WiFi\"",
            visibleSsids = listOf("\"Cafe WiFi\"", "Home WiFi"),
            hasLocationPermission = true,
            locationEnabled = true,
            wifiEnabled = true
        )

        assertTrue(decision.canScan)
        assertNull(decision.blockedReason)
    }

    @Test
    fun evaluateBlocksWhenEnabledWithoutSelectedSsid() {
        val decision = WifiNetworkGate.evaluate(
            gateEnabled = true,
            selectedSsid = "   ",
            currentSsid = "\"Home WiFi\"",
            visibleSsids = listOf("Home WiFi"),
            hasLocationPermission = true,
            locationEnabled = true,
            wifiEnabled = true
        )

        assertFalse(decision.canScan)
        assertEquals(WifiNetworkGate.BlockedReason.NO_SELECTED_NETWORK, decision.blockedReason)
    }

    @Test
    fun evaluateBlocksBeforeMatchingWhenPermissionsOrDeviceStateAreMissing() {
        val missingPermission = WifiNetworkGate.evaluate(
            gateEnabled = true,
            selectedSsid = "Home WiFi",
            currentSsid = "\"Home WiFi\"",
            visibleSsids = listOf("Home WiFi"),
            hasLocationPermission = false,
            locationEnabled = true,
            wifiEnabled = true
        )
        val locationDisabled = WifiNetworkGate.evaluate(
            gateEnabled = true,
            selectedSsid = "Home WiFi",
            currentSsid = "\"Home WiFi\"",
            visibleSsids = listOf("Home WiFi"),
            hasLocationPermission = true,
            locationEnabled = false,
            wifiEnabled = true
        )
        val wifiDisabled = WifiNetworkGate.evaluate(
            gateEnabled = true,
            selectedSsid = "Home WiFi",
            currentSsid = "\"Home WiFi\"",
            visibleSsids = listOf("Home WiFi"),
            hasLocationPermission = true,
            locationEnabled = true,
            wifiEnabled = false
        )

        assertEquals(WifiNetworkGate.BlockedReason.MISSING_WIFI_PERMISSION, missingPermission.blockedReason)
        assertEquals(WifiNetworkGate.BlockedReason.LOCATION_DISABLED, locationDisabled.blockedReason)
        assertEquals(WifiNetworkGate.BlockedReason.WIFI_DISABLED, wifiDisabled.blockedReason)
    }

    @Test
    fun evaluateBlocksWhenSelectedSsidIsNotCurrentOrVisible() {
        val decision = WifiNetworkGate.evaluate(
            gateEnabled = true,
            selectedSsid = "Home WiFi",
            currentSsid = "\"Other WiFi\"",
            visibleSsids = listOf("Cafe WiFi", "Office WiFi"),
            hasLocationPermission = true,
            locationEnabled = true,
            wifiEnabled = true
        )

        assertFalse(decision.canScan)
        assertEquals(WifiNetworkGate.BlockedReason.TARGET_NOT_FOUND, decision.blockedReason)
    }
}
