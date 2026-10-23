package com.btnotifier.ui

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.btnotifier.MainActivity
import com.btnotifier.R
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.DeviceMetadata
import com.btnotifier.data.PreferencesManager
import com.btnotifier.data.WatchedDevice
import com.btnotifier.data.history.DetectionObservation
import com.btnotifier.data.history.HistoryCsv
import com.btnotifier.data.history.HistoryPolicy
import com.btnotifier.scanner.BluetoothScanner
import com.btnotifier.service.MonitoringStatus
import com.btnotifier.service.BluetoothScanService
import com.btnotifier.service.MonitoringServiceStarter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FeatureNavigationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun realActivityNavigatesFeaturesPreservesDisabledMonitoringAndReturnsFromFinder() {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mutableListOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) required.add(Manifest.permission.POST_NOTIFICATIONS)
        assumeTrue("Pre-grant launch permissions on the exclusively owned emulator; this test does not operate Android dialogs.",
            required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
        val db = AppDatabase.getInstance(context)
        val preferences = PreferencesManager(context)
        val originalEnabled = runBlocking { preferences.serviceEnabled.first() }
        val originalWifiGate = runBlocking { preferences.wifiScanGateEnabled.first() }
        val originalDuration = runBlocking { preferences.scanDurationSeconds.first() }
        val originalResumeRequired = runBlocking { preferences.monitoringResumeRequired.first() }
        val originalMessage = runBlocking { preferences.monitoringMessage.first() }
        val originalScanAt = runBlocking { preferences.lastBackgroundScanAt.first() }
        val originalCount = runBlocking { preferences.lastBackgroundDeviceCount.first() }
        val token = UUID.randomUUID().toString().take(8)
        val address = uniqueAddress()
        val hiddenAddress = uniqueAddress()
        val watchName = "000 Feature fixture $token"
        val observedName = "=Feature \"finder\", café $token"
        val observedAt = System.currentTimeMillis()
        var fixturesOwned = false
        var scenario: ActivityScenario<MainActivity>? = null
        var exportFile: File? = null
        var exportMonitor: Instrumentation.ActivityMonitor? = null
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            runBlocking {
                for (candidate in listOf(address, hiddenAddress)) {
                    assertNull(db.watchedDeviceDao().getByAddress(candidate))
                    assertNull(db.deviceMetadataDao().getByAddress(candidate))
                    db.openHelper.readableDatabase.query(
                        "SELECT COUNT(*) FROM detection_history WHERE address = ?", arrayOf(candidate)
                    ).use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
                }
                fixturesOwned = true
                preferences.setServiceEnabled(false)
                context.stopService(Intent(context, BluetoothScanService::class.java))
                db.watchedDeviceDao().insert(WatchedDevice(address, watchName, alertEnabled = false))
                db.deviceMetadataDao().upsert(DeviceMetadata(address, customName = watchName))
                db.deviceMetadataDao().upsert(DeviceMetadata(hiddenAddress, isHidden = true))
                db.detectionHistoryDao().upsert(DetectionObservation(address, HistoryPolicy.minuteBucket(observedAt),
                    observedName, -63, true, observedAt, "FINDER"))
                db.detectionHistoryDao().upsert(DetectionObservation(hiddenAddress, HistoryPolicy.minuteBucket(observedAt),
                    "Hidden fixture $token", -91, false, observedAt, "NEARBY"))
            }
            awaitCondition("Existing app service stops before UI verification") { !MonitoringStatus.state.value.running }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            onView(withId(R.id.btn_scan)).check(matches(isDisplayed()))

            onView(withId(R.id.healthFragment)).perform(click())
            val stopped = context.getString(R.string.health_service, context.getString(R.string.health_stopped))
            val disabled = context.getString(R.string.health_preference, context.getString(R.string.health_disabled))
            awaitView(allOf(withId(R.id.tv_monitoring), withText(allOf(containsString(stopped), containsString(disabled)))))
            onView(withId(R.id.btn_resume)).check(matches(not(isEnabled())))
            onView(withId(R.id.tv_resume)).check(matches(withText(R.string.health_enable_first)))

            onView(withId(R.id.historyFragment)).perform(click())
            awaitView(withText(observedName))
            onView(allOf(withId(R.id.tv_history_address), withText(address))).check(matches(isDisplayed()))
            onView(withId(R.id.btn_export_history)).check(matches(isEnabled()))
            val snapshot = runBlocking { db.detectionHistoryDao().visibleSnapshot(HistoryPolicy.retentionCutoff(observedAt)) }
            assertTrue(snapshot.any { it.address == address })
            assertFalse(snapshot.any { it.address == hiddenAddress })
            val bytes = ByteArrayOutputStream()
            OutputStreamWriter(bytes, Charsets.UTF_8).use { writer ->
                HistoryCsv.write(snapshot.filter { it.address == address }.map { it.toSample() }, writer)
            }
            val csv = bytes.toByteArray().toString(Charsets.UTF_8)
            assertTrue(csv.startsWith("observed_at_epoch_ms,address,observed_name,rssi_dbm,transport,source\r\n"))
            assertTrue(csv.contains("\"$address\""))
            assertTrue(csv.contains("\"'=Feature \"\"finder\"\", café $token\""))
            assertTrue(csv.contains(",-63,\"BLE\",\"FINDER\"\r\n"))
            assertFalse(csv.contains(hiddenAddress))

            val destination = File.createTempFile("feature-export-$token-", ".csv", context.cacheDir)
            exportFile = destination
            val createDocument = IntentFilter(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addDataType("text/csv")
            }
            // Simulate the chosen picker result, then exercise the real Fragment and ContentResolver export path.
            val monitor = instrumentation.addMonitor(createDocument,
                Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(destination))), true)
            exportMonitor = monitor
            try {
                onView(withId(R.id.btn_export_history)).perform(click())
                awaitView(allOf(withId(R.id.tv_history_status), withText(R.string.history_exported)))
                assertEquals("Export must launch CreateDocument exactly once", 1, monitor.hits)
                val exportedCsv = destination.readText(Charsets.UTF_8)
                assertTrue(exportedCsv.startsWith("observed_at_epoch_ms,address,observed_name,rssi_dbm,transport,source\r\n"))
                assertTrue("The chosen destination must receive the visible fixture", exportedCsv.contains(csv.substringAfter("\r\n")))
                assertFalse("Hidden devices must stay out of the real export", exportedCsv.contains(hiddenAddress))
            } finally {
                instrumentation.removeMonitor(monitor)
                exportMonitor = null
            }

            onView(withId(R.id.settingsFragment)).perform(click())
            onView(withId(R.id.switch_service)).check(matches(not(isChecked())))
            onView(withId(R.id.watchListFragment)).perform(click())
            val watchedPosition = runBlocking { db.watchedDeviceDao().getAll().indexOfFirst { it.address == address } }
            awaitRecyclerItem(R.id.recycler_view, watchedPosition)
            onView(allOf(withId(R.id.btn_find), hasSibling(hasDescendant(withText(address))))).perform(click())
            onView(withId(R.id.tv_address)).check(matches(withText(address)))
            onView(withId(R.id.tv_device_name)).check(matches(withText(watchName)))
            onView(withId(R.id.tv_signal)).check(matches(withText(R.string.finder_signal_none)))
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                assertEquals(R.id.finderFragment, host.navController.currentDestination?.id)
                assertEquals(address, host.navController.currentBackStackEntry?.arguments?.getString("address"))
            }

            val scanner = BluetoothScanner.getInstance(context)
            if (scanner.isBluetoothEnabled) {
                onView(withId(R.id.btn_search)).perform(scrollTo(), click())
                onView(withId(R.id.btn_search)).perform(stopOnlyIfSearching())
                onView(withId(R.id.tv_signal)).check(matches(withText(R.string.finder_signal_none)))
            } else {
                // Starting here launches Android's enable-Bluetooth UI. Keep the test inside this app.
                onView(withId(R.id.tv_status)).check(matches(withText(R.string.finder_ready)))
                onView(withId(R.id.btn_search)).check(matches(isEnabled()))
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            onView(withId(R.id.btn_search)).check(matches(withText(R.string.finder_start)))
            onView(withId(R.id.tv_signal)).check(matches(withText(R.string.finder_signal_none)))
            onView(withId(R.id.btn_back)).perform(scrollTo(), click())
            awaitView(allOf(withId(R.id.tv_title), withText("Watch List")))
            onView(withId(R.id.nearbyFragment)).perform(click())
            onView(withId(R.id.btn_scan)).check(matches(isDisplayed()))
            assertFalse(MonitoringStatus.state.value.running)
            assertFalse(runBlocking { preferences.serviceEnabled.first() })

            runBlocking {
                preferences.setWifiScanGateEnabled(true)
                preferences.setServiceEnabled(true)
                assertFalse(MonitoringServiceStarter.start(context, userInitiated = false))
                assertTrue(preferences.monitoringResumeRequired.first())
                assertTrue(preferences.monitoringMessage.first().contains("Wi-Fi"))
            }
            assertFalse(MonitoringStatus.state.value.running)
            onView(withId(R.id.healthFragment)).perform(click())
            awaitView(allOf(withId(R.id.tv_resume), withText(containsString("Wi-Fi"))))
            runBlocking {
                // Radio-independent visible recovery. Wi-Fi/location permission UI is outside this test.
                preferences.setWifiScanGateEnabled(false)
                preferences.setScanDurationSeconds(3)
            }
            onView(withId(R.id.btn_resume)).perform(scrollTo(), click())
            awaitCondition("Visible Resume starts the app service") { MonitoringStatus.state.value.running }
            awaitCondition("Visible recovery clears the persisted resume request") {
                !runBlocking { preferences.monitoringResumeRequired.first() }
            }
            if (scanner.isBluetoothEnabled) {
                awaitCondition("Background cycle enters its timed scan") { MonitoringStatus.state.value.phase == "Scanning" }
            }
            onView(withId(R.id.settingsFragment)).perform(click())
            awaitView(allOf(withId(R.id.switch_service), isChecked()))
            onView(withId(R.id.switch_service)).perform(click())
            awaitCondition("Settings stops the app service") { !MonitoringStatus.state.value.running }
            val completedAtStop = runBlocking { preferences.lastBackgroundScanAt.first() }
            val stoppedAt = SystemClock.uptimeMillis()
            while (SystemClock.uptimeMillis() - stoppedAt < 4_000L) {
                assertFalse("Stopped service must not reappear after the scan duration", MonitoringStatus.state.value.running)
                assertEquals("Cancelled cycle must not publish a completed scan", completedAtStop,
                    runBlocking { preferences.lastBackgroundScanAt.first() })
                SystemClock.sleep(100L)
            }
            onView(withId(R.id.healthFragment)).perform(click())
            awaitView(allOf(withId(R.id.tv_monitoring), withText(allOf(containsString(stopped), containsString(disabled)))))
        } finally {
            exportMonitor?.let { instrumentation.removeMonitor(it) }
            try {
                scenario?.close()
            } finally {
                try {
                    runBlocking {
                        try {
                            preferences.setServiceEnabled(false)
                            context.stopService(Intent(context, BluetoothScanService::class.java))
                            if (fixturesOwned) {
                                // Exact owned addresses only. Never clear the application's tables.
                                for (candidate in listOf(address, hiddenAddress)) {
                                    for (table in listOf("detection_history", "device_metadata", "watched_devices")) {
                                        db.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE address = ?", arrayOf(candidate))
                                    }
                                }
                            }
                        } finally {
                            preferences.setWifiScanGateEnabled(originalWifiGate)
                            preferences.setScanDurationSeconds(originalDuration)
                            preferences.setMonitoringResumeRequired(originalResumeRequired, originalMessage)
                            preferences.recordBackgroundScan(originalScanAt, originalCount)
                            preferences.setServiceEnabled(originalEnabled)
                        }
                    }
                } finally {
                    exportFile?.let { file ->
                        assertTrue("Remove only the owned export fixture", !file.exists() || file.delete())
                    }
                }
            }
        }
    }

    private fun awaitCondition(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        do {
            if (condition()) return
            SystemClock.sleep(50L)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError(description)
    }

    private fun uniqueAddress(): String {
        val value = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        return "02:" + value.chunked(2).joinToString(":")
    }

    private fun awaitView(matcher: Matcher<View>) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getDescription() = "Wait up to five seconds for $matcher"
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun perform(controller: UiController, root: View) {
                val deadline = SystemClock.uptimeMillis() + 5_000L
                do {
                    if (TreeIterables.breadthFirstViewTraversal(root).any { matcher.matches(it) }) return
                    controller.loopMainThreadForAtLeast(50L)
                } while (SystemClock.uptimeMillis() < deadline)
                throw AssertionError("View did not appear: $matcher")
            }
        })
    }

    private fun awaitRecyclerItem(id: Int, position: Int) {
        assertTrue("Owned watch fixture must exist", position >= 0)
        onView(withId(id)).perform(object : ViewAction {
            override fun getDescription() = "Scroll to the owned watch fixture after its adapter loads"
            override fun getConstraints(): Matcher<View> = isAssignableFrom(RecyclerView::class.java)
            override fun perform(controller: UiController, view: View) {
                val recycler = view as RecyclerView
                val deadline = SystemClock.uptimeMillis() + 5_000L
                while ((recycler.adapter?.itemCount ?: 0) <= position && SystemClock.uptimeMillis() < deadline) {
                    controller.loopMainThreadForAtLeast(50L)
                }
                assertTrue("Watch adapter did not load fixture", (recycler.adapter?.itemCount ?: 0) > position)
                recycler.scrollToPosition(position)
                controller.loopMainThreadForAtLeast(100L)
            }
        })
    }

    private fun stopOnlyIfSearching() = object : ViewAction {
        override fun getDescription() = "Stop an active Finder search, or verify a reported scanner failure"
        override fun getConstraints(): Matcher<View> = isAssignableFrom(TextView::class.java)
        override fun perform(controller: UiController, view: View) {
            if ((view as TextView).text.toString() == context.getString(R.string.finder_stop)) {
                click().perform(controller, view)
            } else {
                val root = view.rootView
                val status = root.findViewById<TextView>(R.id.tv_status).text.toString()
                assertTrue("Unavailable scanner must report a failure: $status",
                    status.startsWith(context.getString(R.string.finder_failed, "")))
            }
        }
    }
}
