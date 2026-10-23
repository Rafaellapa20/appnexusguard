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

    @Query("SELECT * FROM battery_samples ORDER BY timestampEpochMillis DESC LIMIT 120")
    fun observeRecentBatterySamples(): Flow<List<BatterySampleEntity>>

    @Query("SELECT * FROM battery_samples WHERE timestampEpochMillis >= :sinceMillis ORDER BY timestampEpochMillis ASC")
    suspend fun getBatterySamplesSince(sinceMillis: Long): List<BatterySampleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatterySample(sample: BatterySampleEntity)

    @Query("DELETE FROM battery_samples WHERE timestampEpochMillis < :cutoffMillis")
    suspend fun pruneOldBatterySamples(cutoffMillis: Long)

    @Query("SELECT * FROM file_hash_cache WHERE id IN (:ids)")
    suspend fun getCachedFileHashes(ids: List<Long>): List<FileHashCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFileHashCache(entry: FileHashCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFileHashCacheAll(entries: List<FileHashCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNexusTrashItems(items: List<NexusTrashEntity>)

    @Query("SELECT * FROM nexus_trash_items WHERE historyId = :historyId AND isRestored = 0")
    suspend fun getNexusTrashItemsByHistoryId(historyId: Long): List<NexusTrashEntity>

    @Query("UPDATE nexus_trash_items SET isRestored = 1 WHERE historyId = :historyId")
    suspend fun markNexusTrashItemsRestored(historyId: Long)

    @Query("SELECT * FROM nexus_trash_items WHERE expiresAtEpochMillis <= :nowMillis AND isRestored = 0")
    suspend fun getExpiredNexusTrashItems(nowMillis: Long): List<NexusTrashEntity>

    @Query("DELETE FROM nexus_trash_items WHERE id IN (:ids)")
    suspend fun deleteNexusTrashItemsByIds(ids: List<Long>)

    @Query("SELECT * FROM charging_sessions ORDER BY endEpochMillis DESC LIMIT 20")
    fun observeChargingSessions(): Flow<List<ChargingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChargingSession(session: ChargingSessionEntity)

    @Query("SELECT * FROM app_storage_snapshots")
    suspend fun getAllAppStorageSnapshots(): List<AppStorageSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppStorageSnapshots(snapshots: List<AppStorageSnapshotEntity>)
}
