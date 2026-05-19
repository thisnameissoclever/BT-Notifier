package com.btnotifier.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watched_devices")
data class WatchedDevice(
    @PrimaryKey val address: String,
    val name: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long? = null,
    val alertEnabled: Boolean = true
)
