package com.nexusguard.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NexusGuardDao {

    @Query("SELECT * FROM history_entries ORDER BY timestampEpochMillis DESC")
    fun observeAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_entries ORDER BY timestampEpochMillis DESC LIMIT 3")
    fun observeRecentHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_entries ORDER BY timestampEpochMillis DESC")
    suspend fun getAllHistoryOnce(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntity): Long

    @Query("UPDATE history_entries SET isRestored = 1, canRestore = 0 WHERE id = :id")
    suspend fun markHistoryRestored(id: Long)

    @Query("SELECT * FROM battery_samples ORDER BY timestampEpochMillis DESC LIMIT 24")
    fun observeRecentBatterySamples(): Flow<List<BatterySampleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatterySample(sample: BatterySampleEntity)

    @Query("DELETE FROM battery_samples WHERE timestampEpochMillis < :cutoffMillis")
    suspend fun pruneOldBatterySamples(cutoffMillis: Long)
}
