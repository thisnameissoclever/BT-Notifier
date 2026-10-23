package com.btnotifier.data.history

import android.content.Context
import androidx.room.withTransaction
import com.btnotifier.data.AppDatabase
import com.btnotifier.data.DetectionSource
import com.btnotifier.device.DeviceDisplayName
import com.btnotifier.scanner.ScannedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class ObservationRepository internal constructor(
    private val db: AppDatabase,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val history = db.detectionHistoryDao()

    suspend fun record(devices: Collection<ScannedDevice>, source: DetectionSource) = withContext(Dispatchers.IO) {
        val samples = HistoryPolicy.coalesce(devices.map {
            HistorySample(it.address, it.name, it.rssi, it.isBle, it.timestamp, source.name)
        })
        val at = now()
        db.withTransaction {
            samples.groupBy { it.address }.values.forEach { addressSamples ->
                val newest = addressSamples.maxBy { it.observedAt }
                val actualName = DeviceDisplayName.normalizeActualName(newest.observedName)
                db.watchedDeviceDao().recordObservation(newest.address, newest.observedAt, actualName)
                db.deviceMetadataDao().recordObservation(newest.address, newest.observedAt, actualName)
            }
            samples.filter { HistoryPolicy.shouldRetain(it.observedAt, at) }.forEach { sample ->
                val bucket = HistoryPolicy.minuteBucket(sample.observedAt)
                val previous = history.getBucket(sample.address, bucket)
                if (HistoryPolicy.shouldReplace(previous?.observedAt, sample.observedAt)) {
                    history.upsert(DetectionObservation(
                        sample.address, bucket, sample.observedName, sample.rssi,
                        sample.isBle, sample.observedAt, sample.source
                    ))
                }
            }
            pruneInTransaction(at)
        }
    }

    fun observeVisible(): Flow<List<DetectionObservation>> = combine(
        history.observeVisible(),
        flow {
            while (true) {
                emit(now())
                delay(60_000)
            }
        }
    ) { rows, _ -> rows.filter { HistoryPolicy.shouldRetain(it.observedAt, now()) } }

    suspend fun visibleSnapshot(): List<DetectionObservation> = withContext(Dispatchers.IO) {
        db.withTransaction {
            val at = now()
            pruneInTransaction(at)
            history.visibleSnapshot(HistoryPolicy.retentionCutoff(at)).toList()
        }
    }

    suspend fun prune() = withContext(Dispatchers.IO) {
        db.withTransaction { pruneInTransaction(now()) }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) { history.clear() }

    private suspend fun pruneInTransaction(at: Long) {
        history.deleteExpired(HistoryPolicy.retentionCutoff(at))
        history.deleteExcess(HistoryPolicy.MAX_ROWS)
    }

    companion object {
        @Volatile private var instance: ObservationRepository? = null
        fun get(context: Context): ObservationRepository = instance ?: synchronized(this) {
            instance ?: ObservationRepository(AppDatabase.getInstance(context.applicationContext)).also { instance = it }
        }
    }
}
