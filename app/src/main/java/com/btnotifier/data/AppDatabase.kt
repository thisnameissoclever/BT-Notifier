package com.btnotifier.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WatchedDevice::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun watchedDeviceDao(): WatchedDeviceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bt_notifier.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
