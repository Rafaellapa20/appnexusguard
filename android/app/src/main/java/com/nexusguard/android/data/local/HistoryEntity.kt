package com.nexusguard.android.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "history_entries")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val actionType: String, // CLEAN, BOOST, ANALYSIS, BACKGROUND_STOP, CACHE_CLEAR
    val title: String,
    val details: String,
    val timestampEpochMillis: Long,
    val bytesFreed: Long = 0L,
    val trashedUrisJson: String = "", // Pipe-separated content URIs or nexus-trash IDs for restore
    val canRestore: Boolean = false,
    val isRestored: Boolean = false
)

@Immutable
@Entity(tableName = "battery_samples")
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampEpochMillis: Long,
    val levelPercent: Int,
    val temperatureCelsius: Float,
    val isCharging: Boolean,
    val currentMa: Int = 0,
    val isScreenOn: Boolean = true
)

@Immutable
@Entity(tableName = "file_hash_cache")
data class FileHashCacheEntity(
    @PrimaryKey
    val id: Long,
    val dateModified: Long,
    val size: Long,
    val partialHash: String,
    val fullHash: String? = null,
    val dHash: Long? = null,
    val laplacianSharpness: Double? = null
)

@Immutable
@Entity(tableName = "nexus_trash_items")
data class NexusTrashEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val historyId: Long,
    val originalPath: String,
    val trashFilePath: String,
    val displayName: String,
    val sizeBytes: Long,
    val trashedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val isRestored: Boolean = false
)

@Immutable
@Entity(tableName = "charging_sessions")
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val startPercent: Int,
    val endPercent: Int,
    val maxTempCelsius: Float
)

@Immutable
@Entity(tableName = "app_storage_snapshots")
data class AppStorageSnapshotEntity(
    @PrimaryKey
    val packageName: String,
    val totalBytes: Long,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val timestampEpochMillis: Long
)
