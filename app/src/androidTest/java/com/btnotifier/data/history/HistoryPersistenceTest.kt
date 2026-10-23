package com.btnotifier.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.DetectionSource
import com.btnotifier.data.DeviceMetadata
import com.btnotifier.data.WatchedDevice
import com.btnotifier.scanner.ScannedDevice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun ingestionPreservesAliasesHiddenStateAndMonotonicNamesWithoutAlerts() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val now = 100L * 24 * 60 * 60 * 1000 + 30_000
            val repository = ObservationRepository(db) { now }
            db.watchedDeviceDao().insert(WatchedDevice("AA", "old", addedAt = 7, alertEnabled = false))
            db.deviceMetadataDao().upsert(DeviceMetadata("AA", customName = "My speaker", isHidden = true))
            repository.record(listOf(ScannedDevice("AA", "Latest actual", -60, true, now)), DetectionSource.BACKGROUND)
            repository.record(listOf(ScannedDevice("AA", "Stale actual", -90, false, now - 1)), DetectionSource.NEARBY)
            assertEquals(now, db.watchedDeviceDao().getByAddress("AA")!!.lastSeenAt)
            assertEquals("Latest actual", db.watchedDeviceDao().getByAddress("AA")!!.name)
            assertFalse(db.watchedDeviceDao().getByAddress("AA")!!.alertEnabled)
            val metadata = db.deviceMetadataDao().getByAddress("AA")!!
            assertEquals("My speaker", metadata.customName)
            assertEquals("Latest actual", metadata.lastSeenName)
            assertEquals(now, metadata.lastObservedAt)
            assertTrue(metadata.isHidden)
            assertTrue(repository.visibleSnapshot().isEmpty())
            db.deviceMetadataDao().setHidden("AA", false)
            assertEquals("Latest actual", repository.visibleSnapshot().single().observedName)
            repository.clearHistory()
            assertTrue(repository.visibleSnapshot().isEmpty())
            assertEquals(now, db.watchedDeviceDao().getByAddress("AA")!!.lastSeenAt)
            assertEquals("My speaker", db.deviceMetadataDao().getByAddress("AA")!!.customName)
        } finally {
            db.close()
        }
    }

    @Test fun repositoryCapsRowsAndPrunesExpiredRecords() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val now = 100L * 24 * 60 * 60 * 1000
            val repository = ObservationRepository(db) { now }
            val samples = (0..10_000).map { index ->
                ScannedDevice("device-$index", "Speaker", -65, true, now - index * 60_000L)
            } + ScannedDevice("expired", "Expired", -75, false, now - HistoryPolicy.RETENTION_MS - 1)
            repository.record(samples, DetectionSource.NEARBY)
            val retained = repository.visibleSnapshot()
            assertEquals(10_000, retained.size)
            assertEquals("device-0", retained.first().address)
            assertFalse(retained.any { it.address == "expired" || it.address == "device-10000" })
        } finally {
            db.close()
        }
    }

    @Test fun versionThreeUpgradePreservesExistingRowsAndAcceptsHistory() = runBlocking {
        val name = "history-upgrade-${System.nanoTime()}.db"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            sqlite.execSQL("CREATE TABLE watched_devices (address TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, addedAt INTEGER NOT NULL, lastSeenAt INTEGER, alertEnabled INTEGER NOT NULL)")
            sqlite.execSQL("CREATE TABLE device_metadata (address TEXT NOT NULL PRIMARY KEY, customName TEXT, lastSeenName TEXT, isHidden INTEGER NOT NULL DEFAULT 0)")
            sqlite.execSQL("INSERT INTO watched_devices VALUES ('AA', 'Actual speaker', 11, 22, 0)")
            sqlite.execSQL("INSERT INTO device_metadata VALUES ('AA', 'Custom speaker', 'Actual speaker', 1)")
            sqlite.version = 3
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4).build()
        try {
            val watched = db.watchedDeviceDao().getByAddress("AA")!!
            assertEquals(11L, watched.addedAt)
            assertEquals(22L, watched.lastSeenAt)
            assertFalse(watched.alertEnabled)
            val metadata = db.deviceMetadataDao().getByAddress("AA")!!
            assertEquals("Custom speaker", metadata.customName)
            assertEquals("Actual speaker", metadata.lastSeenName)
            assertTrue(metadata.isHidden)
            assertEquals(22L, metadata.lastObservedAt)
            ObservationRepository(db) { 60_000L }.record(
                listOf(ScannedDevice("AA", "Delayed pre-upgrade name", -80, true, 21)), DetectionSource.NEARBY
            )
            assertEquals("Actual speaker", db.deviceMetadataDao().getByAddress("AA")!!.lastSeenName)
            ObservationRepository(db) { 60_000L }.record(
                listOf(ScannedDevice("AA", "New actual", -50, true, 60_000)), DetectionSource.FINDER
            )
            db.deviceMetadataDao().setHidden("AA", false)
            assertEquals(2, ObservationRepository(db) { 60_000L }.visibleSnapshot().size)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
