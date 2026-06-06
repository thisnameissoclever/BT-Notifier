package com.btnotifier.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface WatchedDeviceDao {

    @Query("SELECT * FROM watched_devices ORDER BY name ASC")
    fun getAllLive(): LiveData<List<WatchedDevice>>

    @Query("SELECT * FROM watched_devices ORDER BY name ASC")
    suspend fun getAll(): List<WatchedDevice>

    @Query("SELECT * FROM watched_devices WHERE address = :address LIMIT 1")
    suspend fun getByAddress(address: String): WatchedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: WatchedDevice)

    @Delete
    suspend fun delete(device: WatchedDevice)

    @Query("UPDATE watched_devices SET lastSeenAt = :timestamp WHERE address = :address")
    suspend fun updateLastSeen(address: String, timestamp: Long)

    @Query("UPDATE watched_devices SET name = :name WHERE address = :address")
    suspend fun updateName(address: String, name: String)

    @Query("UPDATE watched_devices SET alertEnabled = :enabled WHERE address = :address")
    suspend fun setAlertEnabled(address: String, enabled: Boolean)
}
