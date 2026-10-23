package com.btnotifier.device

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.DetectionSource
import com.btnotifier.data.DeviceMetadata
import com.btnotifier.data.WatchedDevice
import com.btnotifier.data.history.ObservationRepository
import com.btnotifier.scanner.ScannedDevice
import com.btnotifier.ui.nearby.NearbyViewModel
import com.btnotifier.ui.watchlist.WatchListViewModel
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AliasObservationRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = instrumentation.targetContext.applicationContext as Application
    private val db = AppDatabase.getInstance(application)

    @Test fun watchListKeepsNewerWatchedNameAfterStaleDialogSaveAndClear() = staleDialogCase("Watch List")
    @Test fun nearbyKeepsNewerWatchedNameAfterStaleDialogSaveAndClear() = staleDialogCase("Nearby")
    @Test fun watchListKeepsNewerMetadataNameAfterStaleDialogSaveAndClear() = newerMetadataCase("Watch List")
    @Test fun nearbyKeepsNewerMetadataNameAfterStaleDialogSaveAndClear() = newerMetadataCase("Nearby")

    private fun staleDialogCase(path: String) {
        withRenamePath(path) { label, address, rename ->
            val now = System.currentTimeMillis()
            runBlocking {
                db.watchedDeviceDao().insert(WatchedDevice(address, "Captured name A", lastSeenAt = now - 1_000))
                ObservationRepository.get(application).record(
                    listOf(ScannedDevice(address, "Current name B", -60, true, now)), DetectionSource.NEARBY
                )
                assertNull(db.deviceMetadataDao().getByAddress(address))
            }
            instrumentation.runOnMainSync { rename(address, "Custom alias", "Captured name A") }
            awaitAlias(address, "Custom alias")
            assertStoredObservation(label, address, "Current name B", now)
            instrumentation.runOnMainSync { rename(address, "", "Captured name A") }
            awaitAlias(address, null)
            assertStoredObservation(label, address, "Current name B", now)
        }
    }

    private fun newerMetadataCase(path: String) {
        withRenamePath(path) { label, address, rename ->
            val now = System.currentTimeMillis()
            runBlocking {
                db.watchedDeviceDao().insert(WatchedDevice(address, "Older watched name B", lastSeenAt = now - 1_000))
                db.deviceMetadataDao().upsert(DeviceMetadata(address, customName = "Old alias",
                    lastSeenName = "Newest metadata name C", isHidden = true, lastObservedAt = now))
            }
            instrumentation.runOnMainSync { rename(address, "Custom alias", "Captured name A") }
            awaitAlias(address, "Custom alias")
            assertStoredObservation(label, address, "Newest metadata name C", now)
            instrumentation.runOnMainSync { rename(address, "", "Captured name A") }
            awaitAlias(address, null)
            assertStoredObservation(label, address, "Newest metadata name C", now)
            runBlocking { assertEquals(true, db.deviceMetadataDao().getByAddress(address)!!.isHidden) }
        }
    }

    private fun assertStoredObservation(label: String, address: String, expectedName: String, expectedAt: Long) = runBlocking {
        val metadata = db.deviceMetadataDao().getByAddress(address)!!
        val watched = db.watchedDeviceDao().getByAddress(address)!!
        assertEquals("$label metadata name", expectedName, metadata.lastSeenName)
        assertEquals("$label metadata timestamp", expectedAt, metadata.lastObservedAt)
        assertEquals("$label watched name", expectedName, watched.name)
        assertEquals("$label watched timestamp", expectedAt, watched.lastSeenAt)
    }

    private fun awaitAlias(address: String, alias: String?) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            val metadata = runBlocking { db.deviceMetadataDao().getByAddress(address) }
            if (metadata != null && metadata.customName == alias) return
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Alias write did not complete for owned fixture")
    }

    private fun withRenamePath(label: String, block: (String, String, (String, String, String?) -> Unit) -> Unit) {
            val store = ViewModelStore()
            val token = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
            val address = "02:" + token.chunked(2).joinToString(":")
            lateinit var rename: (String, String, String?) -> Unit
            var owned = false
            try {
                runBlocking {
                    assertNull(db.watchedDeviceDao().getByAddress(address))
                    assertNull(db.deviceMetadataDao().getByAddress(address))
                    db.openHelper.readableDatabase.query(
                        "SELECT COUNT(*) FROM detection_history WHERE address = ?", arrayOf(address)
                    ).use { cursor -> cursor.moveToFirst(); assertEquals(0, cursor.getInt(0)) }
                    owned = true
                }
                instrumentation.runOnMainSync {
                    val provider = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(application))
                    if (label == "Watch List") {
                        val vm = provider[WatchListViewModel::class.java]
                        rename = { target, alias, actual -> vm.renameDevice(target, alias, actual) }
                    } else {
                        val vm = provider[NearbyViewModel::class.java]
                        rename = { target, alias, actual -> vm.renameDevice(target, alias, actual) }
                    }
                }
                block(label, address, rename)
            } finally {
                instrumentation.runOnMainSync { store.clear() }
                if (owned) runBlocking {
                    for (table in listOf("detection_history", "device_metadata", "watched_devices")) {
                        db.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE address = ?", arrayOf(address))
                    }
                }
            }
    }
}
