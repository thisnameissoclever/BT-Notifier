package com.btnotifier.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DeviceMetadataDao {

    @Query("SELECT * FROM device_metadata")
    fun getAllLive(): LiveData<List<DeviceMetadata>>

    @Query("SELECT * FROM device_metadata WHERE isHidden = 1 ORDER BY customName IS NULL, customName ASC, lastSeenName IS NULL, lastSeenName ASC, address ASC")
    fun getHiddenLive(): LiveData<List<DeviceMetadata>>

    @Query("SELECT * FROM device_metadata WHERE address = :address LIMIT 1")
    suspend fun getByAddress(address: String): DeviceMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: DeviceMetadata)

    @Query("UPDATE device_metadata SET isHidden = :hidden WHERE address = :address")
    suspend fun setHidden(address: String, hidden: Boolean)

    @Query("UPDATE device_metadata SET customName = :name WHERE address = :address")
    suspend fun setCustomName(address: String, name: String?)

    @Query("UPDATE device_metadata SET lastSeenName = :name WHERE address = :address")
    suspend fun setLastSeenName(address: String, name: String?)
}
