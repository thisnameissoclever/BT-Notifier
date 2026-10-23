package com.btnotifier.data.history

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "detection_history",
    primaryKeys = ["address", "minuteBucket"],
    indices = [Index(value = ["observedAt"])]
)
data class DetectionObservation(
    val address: String,
    val minuteBucket: Long,
    val observedName: String,
    val rssi: Int,
    val isBle: Boolean,
    val observedAt: Long,
    val source: String
) {
    fun toSample() = HistorySample(address, observedName, rssi, isBle, observedAt, source)
}
