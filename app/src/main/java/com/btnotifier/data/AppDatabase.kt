package com.btnotifier.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.btnotifier.data.history.DetectionHistoryDao
import com.btnotifier.data.history.DetectionObservation

@Database(
    entities = [WatchedDevice::class, DeviceMetadata::class, DetectionObservation::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchedDeviceDao(): WatchedDeviceDao
    abstract fun deviceMetadataDao(): DeviceMetadataDao
    abstract fun detectionHistoryDao(): DetectionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS device_metadata (
                        address TEXT NOT NULL PRIMARY KEY,
                        customName TEXT,
                        isHidden INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN lastSeenName TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE device_metadata ADD COLUMN lastObservedAt INTEGER")
                db.execSQL("""UPDATE device_metadata SET lastObservedAt =
                    (SELECT lastSeenAt FROM watched_devices WHERE watched_devices.address = device_metadata.address)""")
                db.execSQL("""CREATE TABLE IF NOT EXISTS detection_history (
                    address TEXT NOT NULL,
                    minuteBucket INTEGER NOT NULL,
                    observedName TEXT NOT NULL,
                    rssi INTEGER NOT NULL,
                    isBle INTEGER NOT NULL,
                    observedAt INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    PRIMARY KEY(address, minuteBucket)
                )""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detection_history_observedAt ON detection_history(observedAt)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bt_notifier.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
