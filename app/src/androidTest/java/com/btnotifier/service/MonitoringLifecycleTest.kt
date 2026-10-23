package com.btnotifier.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.btnotifier.MainActivity
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.PreferencesManager
import com.btnotifier.data.WatchedDevice
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.utils.NotificationHelper
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonitoringLifecycleTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = PreferencesManager(context)
    private val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Test fun monitoringSurvivesLeavingActivityBeyondCycleCompletion() = withFixture { fixture ->
        val completedBeforeStart = readCompletedAt()
        val firstPhase = fixture.startVisible()
        fixture.scenario!!.moveToState(Lifecycle.State.CREATED)
        assertEquals(Lifecycle.State.CREATED, fixture.scenario!!.state)

        assertFor(3_000, "Foreground monitoring must survive leaving the activity beyond its two-second cycle") {
            assertTrue(MonitoringStatus.state.value.toString(), MonitoringStatus.state.value.running)
            assertTrue("Service notification 1001 disappeared after leaving the activity", hasServiceNotification())
        }
        when (firstPhase) {
            "Scanning" -> {
                await("The background cycle should complete while the activity stays stopped") {
                    MonitoringStatus.state.value.phase == "Waiting" && readCompletedAt() > completedBeforeStart
                }
                assertTrue(readCompletedAt() >= completedBeforeStart)
            }
            "Blocked" -> {
                assertEquals("An unavailable radio must not manufacture a completed scan", completedBeforeStart, readCompletedAt())
                assertNotNull("Blocked service must explain the unavailable scanner", MonitoringStatus.state.value.error)
                Log.i(TAG, "Survival verified in Blocked state; emulator radio cannot prove discovery or cycle completion")
            }
            else -> assertTrue(readCompletedAt() >= completedBeforeStart)
        }
        assertTrue(MonitoringStatus.state.value.running)
        assertTrue(hasServiceNotification())
    }

    @Test fun stopRevokesPendingCompletionAndNotificationThenRestartsCleanly() = withFixture { fixture ->
        val firstPhase = fixture.startVisible()
        val completedAtStop = readCompletedAt()
        val stoppedPhase = MonitoringStatus.state.value.phase
        context.stopService(Intent(context, BluetoothScanService::class.java))
        await("Stopping this app's service must remove its process state and notification") {
            !MonitoringStatus.state.value.running && !hasServiceNotification()
        }
        fixture.scenario!!.moveToState(Lifecycle.State.CREATED)
        assertFor(3_000, "A stopped cycle must not complete late or restore notification 1001") {
            assertFalse(MonitoringStatus.state.value.toString(), MonitoringStatus.state.value.running)
            assertFalse("A delayed callback restored service notification 1001", hasServiceNotification())
            assertEquals("A cancelled cycle updated lastBackgroundScanAt", completedAtStop, readCompletedAt())
        }
        if (firstPhase != "Scanning" || stoppedPhase != "Scanning") {
            Log.i(TAG, "Cancellation verified from $stoppedPhase; emulator radio did not expose an in-flight physical scan")
        }

        fixture.scenario!!.moveToState(Lifecycle.State.RESUMED)
        fixture.scenario!!.onActivity { it.startBackgroundServiceIfPermitted(requestMissingPermissions = false) }
        val restartPhase = awaitSettledService()
        await("Restart must create foreground notification 1001") { hasServiceNotification() }
        assertTrue(MonitoringStatus.state.value.running)
        if (restartPhase == "Scanning") {
            await("The restarted cycle should complete using its fresh service scope") {
                MonitoringStatus.state.value.phase == "Waiting" && readCompletedAt() > completedAtStop
            }
        } else if (restartPhase == "Blocked") {
            assertEquals(completedAtStop, readCompletedAt())
            assertNotNull(MonitoringStatus.state.value.error)
        }
        assertTrue("Restart must not regress the completed timestamp", readCompletedAt() >= completedAtStop)
    }

    // withFixture verifies BLUETOOTH_CONNECT before this test reads the adapter state.
    @SuppressLint("MissingPermission")
    @Test fun adapterOffDuringCycleRevokesTransportStateAndSuccessfulCompletion() {
        val emulator = Build.HARDWARE in setOf("ranchu", "goldfish") ||
            Build.FINGERPRINT.startsWith("generic/") || Build.FINGERPRINT.startsWith("google/sdk_gphone")
        assumeTrue("Bluetooth toggling is restricted to the exclusively owned emulator.", emulator)
        withFixture { fixture ->
            val adapter = requireNotNull((context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter)
            assumeTrue("This regression requires an initially enabled emulator Bluetooth adapter.", adapter.isEnabled)
            val originallyEnabled = adapter.isEnabled
            val completedBeforeStart = readCompletedAt()
            runBlocking { preferences.setScanDurationSeconds(5) }
            try {
                assertEquals("This regression requires an in-flight physical scan", "Scanning", fixture.startVisible())
                runBluetoothShellCommand("disable")
                await("The emulator adapter should turn off") { !adapter.isEnabled }
                val scanner = BluetoothScanner.getInstance(context)
                await("Adapter OFF must clear all scanner transport flags and block the cycle") {
                    val status = scanner.status.value
                    !status.isScanning && !status.bleScanning && !status.classicScanning &&
                        MonitoringStatus.state.value.phase == "Blocked"
                }
                assertFor(1_000, "Adapter OFF must never manufacture a successful background completion") {
                    assertEquals(completedBeforeStart, readCompletedAt())
                    assertNotNull(MonitoringStatus.state.value.error)
                }
            } finally {
                if (originallyEnabled) {
                    runBluetoothShellCommand("enable")
                    await("Restore the emulator adapter before fixture cleanup") { adapter.isEnabled }
                }
            }
        }
    }

    private fun runBluetoothShellCommand(action: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cmd bluetooth_manager $action")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    private data class SavedPreferences(
        val enabled: Boolean,
        val interval: Int,
        val duration: Int,
        val notify: Boolean,
        val wifi: Boolean,
        val resumeRequired: Boolean,
        val message: String,
        val completedAt: Long,
        val count: Int
    )

    private inner class Fixture {
        var scenario: ActivityScenario<MainActivity>? = null

        fun startVisible(): String {
            // Launch while disabled so this test controls the first monitoring start.
            scenario = ActivityScenario.launch(MainActivity::class.java)
            runBlocking { preferences.setServiceEnabled(true) }
            scenario!!.onActivity { it.startBackgroundServiceIfPermitted(requestMissingPermissions = false) }
            val phase = awaitSettledService()
            await("Started service must post foreground notification 1001") { hasServiceNotification() }
            return phase
        }
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mutableListOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) required.add(Manifest.permission.POST_NOTIFICATIONS)
        assumeTrue("Pre-grant launch permissions on the exclusively owned emulator.",
            required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
        assumeFalse("Start with this app's service stopped; do not interrupt an existing run.",
            MonitoringStatus.state.value.running || hasServiceNotification())
        assertFalse("Polling must run outside the Android main thread", Looper.myLooper() == Looper.getMainLooper())

        val saved = runBlocking {
            SavedPreferences(preferences.serviceEnabled.first(), preferences.scanIntervalMinutes.first(),
                preferences.scanDurationSeconds.first(), preferences.notifyOnFind.first(),
                preferences.wifiScanGateEnabled.first(), preferences.monitoringResumeRequired.first(),
                preferences.monitoringMessage.first(), preferences.lastBackgroundScanAt.first(),
                preferences.lastBackgroundDeviceCount.first())
        }
        val savedResume = notifications.activeNotifications.firstOrNull { it.id == RESUME_NOTIFICATION_ID }?.notification
        val random = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        val address = "02:" + random.chunked(2).joinToString(":")
        val db = AppDatabase.getInstance(context)
        val fixture = Fixture()
        var ownsFixture = false
        try {
            runBlocking {
                assertEquals(null, db.watchedDeviceDao().getByAddress(address))
                assertEquals(null, db.deviceMetadataDao().getByAddress(address))
                db.openHelper.readableDatabase.query(
                    "SELECT COUNT(*) FROM detection_history WHERE address = ?", arrayOf(address)
                ).use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
                preferences.setServiceEnabled(false)
                preferences.setWifiScanGateEnabled(false)
                preferences.setScanIntervalMinutes(1)
                preferences.setScanDurationSeconds(2)
                preferences.setNotifyOnFind(false)
                preferences.setMonitoringResumeRequired(false, "")
                NotificationHelper.cancelResumeNotification(context)
                db.watchedDeviceDao().insert(WatchedDevice(address, "Lifecycle fixture", alertEnabled = false))
                ownsFixture = true
            }
            block(fixture)
        } finally {
            try {
                // Close the visible activity before stopping, so onPostResume cannot restart monitoring.
                fixture.scenario?.close()
                context.stopService(Intent(context, BluetoothScanService::class.java))
                await("Cleanup must stop only this app's service") {
                    !MonitoringStatus.state.value.running && !hasServiceNotification()
                }
            } finally {
                runBlocking {
                    preferences.setServiceEnabled(false)
                    preferences.setWifiScanGateEnabled(saved.wifi)
                    preferences.setScanIntervalMinutes(saved.interval)
                    preferences.setScanDurationSeconds(saved.duration)
                    preferences.setNotifyOnFind(saved.notify)
                    preferences.recordBackgroundScan(saved.completedAt, saved.count)
                    preferences.setMonitoringResumeRequired(saved.resumeRequired, saved.message)
                    if (ownsFixture) {
                        for (table in listOf("detection_history", "device_metadata", "watched_devices")) {
                            db.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE address = ?", arrayOf(address))
                        }
                    }
                    preferences.setServiceEnabled(saved.enabled)
                }
                NotificationHelper.cancelResumeNotification(context)
                if (savedResume != null) notifications.notify(RESUME_NOTIFICATION_ID, savedResume)
            }
        }
    }

    private fun awaitSettledService(): String {
        await("Monitoring should reach Scanning, Waiting, or a truthful Blocked state") {
            val state = MonitoringStatus.state.value
            state.running && state.phase in listOf("Scanning", "Waiting", "Blocked")
        }
        return MonitoringStatus.state.value.phase
    }

    private fun hasServiceNotification(): Boolean =
        notifications.activeNotifications.any { it.id == NotificationHelper.NOTIFICATION_ID_SERVICE }

    private fun readCompletedAt(): Long = runBlocking { preferences.lastBackgroundScanAt.first() }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("$description. Last state: ${MonitoringStatus.state.value}")
    }

    private fun assertFor(duration: Long, description: String, assertion: () -> Unit) {
        val deadline = SystemClock.elapsedRealtime() + duration
        do {
            try {
                assertion()
            } catch (error: AssertionError) {
                throw AssertionError(description, error)
            }
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
    }

    private companion object {
        const val TAG = "MonitoringLifecycleTest"
        const val RESUME_NOTIFICATION_ID = 1002
    }
}
