package com.btnotifier.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_metadata")
data class DeviceMetadata(
    @PrimaryKey val address: String,
    val customName: String? = null,
    val lastSeenName: String? = null,
    val isHidden: Boolean = false
)
