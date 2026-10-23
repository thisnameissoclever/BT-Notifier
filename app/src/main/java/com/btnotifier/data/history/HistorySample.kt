package com.btnotifier.data.history

data class HistorySample(
    val address: String,
    val observedName: String,
    val rssi: Int,
    val isBle: Boolean,
    val observedAt: Long,
    val source: String
)
