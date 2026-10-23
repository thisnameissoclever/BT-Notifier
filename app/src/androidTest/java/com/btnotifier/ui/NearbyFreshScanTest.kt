package com.btnotifier.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.scanner.ScannedDevice
import com.btnotifier.scanner.ScanLease
import com.btnotifier.service.MonitoringStatus
import com.btnotifier.ui.nearby.NearbyViewModel
import com.btnotifier.utils.NotificationHelper
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbyFreshScanTest {
    @Test fun eachExplicitScanRemovesAbsentDevicesFromPreviousDisplayResults() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val scanner = BluetoothScanner.getInstance(context)
        assumeTrue("Pre-grant Bluetooth permissions and enable the exclusively owned emulator adapter.",
            scanner.hasPermissions && scanner.isBluetoothEnabled)
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assumeTrue("Start with no active app scan or monitoring service; preserve unrelated work.",
            !scanner.status.value.isScanning && !MonitoringStatus.state.value.running &&
                notifications.activeNotifications.none { it.id == NotificationHelper.NOTIFICATION_ID_SERVICE })

        // Test-only injection seeds the display cache without inventing an actual radio observation.
        @Suppress("UNCHECKED_CAST")
        val cache = scanner.devices as MutableStateFlow<Map<String, ScannedDevice>>
        val original = cache.value
        val store = ViewModelStore()
        val observer = Observer<List<NearbyViewModel.DeviceItem>> { }
        lateinit var viewModel: NearbyViewModel
        var created = false
        var otherLease: ScanLease? = null
        val retainedAddress = uniqueAddress()
        val retained = ScannedDevice(retainedAddress, "Owned other consumer result", -60, true)
        try {
            instrumentation.runOnMainSync {
                val application = context.applicationContext as Application
                viewModel = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory.getInstance(application))
                    .get(NearbyViewModel::class.java)
                created = true
                viewModel.devices.observeForever(observer)
                otherLease = requireNotNull(scanner.acquire("fresh-scan-test-background", setOf(retainedAddress)))
                otherLease!!.results.value = mapOf(retainedAddress to retained)
            }
            repeat(2) {
                val address = uniqueAddress()
                val stale = ScannedDevice(address, "Absent previous scan fixture", -55, true,
                    System.currentTimeMillis() - 60_000)
                instrumentation.runOnMainSync { cache.value = cache.value + (address to stale) }
                await("The real Nearby view model must first display the owned stale fixture") {
                    viewModel.devices.value.orEmpty().any { item -> item.device.address == address }
                }
                instrumentation.runOnMainSync { viewModel.startScan() }
                assertFalse("An explicit Nearby scan retained an absent device in the singleton display cache",
                    scanner.devices.value.containsKey(address))
                org.junit.Assert.assertEquals("Clearing the display cache must preserve another consumer's lease results",
                    mapOf(retainedAddress to retained), otherLease!!.devices.value)
                await("The fresh Nearby device list must exclude the previous scan fixture") {
                    viewModel.devices.value.orEmpty().none { item -> item.device.address == address }
                }
                instrumentation.runOnMainSync { viewModel.stopScan() }
            }
        } finally {
            instrumentation.runOnMainSync {
                if (created) {
                    viewModel.stopScan()
                    viewModel.devices.removeObserver(observer)
                }
                store.clear()
                otherLease?.let { scanner.release(it) }
                cache.value = original
            }
        }
    }

    private fun uniqueAddress(): String {
        val random = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        return "02:" + random.chunked(2).joinToString(":")
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 3_000
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(description)
    }
}
