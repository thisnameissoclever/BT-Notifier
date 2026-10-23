package com.btnotifier.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionHistoryDao {
    @Query("""SELECT h.* FROM detection_history h
        WHERE NOT EXISTS (SELECT 1 FROM device_metadata m WHERE m.address = h.address AND m.isHidden = 1)
        ORDER BY h.observedAt DESC, h.address ASC, h.minuteBucket DESC LIMIT 10000""")
    fun observeVisible(): Flow<List<DetectionObservation>>

    @Query("""SELECT h.* FROM detection_history h
        WHERE h.observedAt >= :cutoff
        AND NOT EXISTS (SELECT 1 FROM device_metadata m WHERE m.address = h.address AND m.isHidden = 1)
        ORDER BY h.observedAt DESC, h.address ASC, h.minuteBucket DESC LIMIT 10000""")
    suspend fun visibleSnapshot(cutoff: Long): List<DetectionObservation>

    @Query("SELECT * FROM detection_history WHERE address = :address AND minuteBucket = :bucket LIMIT 1")
    suspend fun getBucket(address: String, bucket: Long): DetectionObservation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(observation: DetectionObservation)

    @Query("DELETE FROM detection_history WHERE observedAt < :cutoff")
    suspend fun deleteExpired(cutoff: Long)

    @Query("""DELETE FROM detection_history WHERE rowid IN
        (SELECT rowid FROM detection_history ORDER BY observedAt DESC, address ASC, minuteBucket DESC LIMIT -1 OFFSET :maxRows)""")
    suspend fun deleteExcess(maxRows: Int)

    @Query("DELETE FROM detection_history")
    suspend fun clear()
}
