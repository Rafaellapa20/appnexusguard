package com.nexusguard.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_entries")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val actionType: String, // CLEAN, BOOST, ANALYSIS, BACKGROUND_STOP
    val title: String,
    val details: String,
    val timestampEpochMillis: Long,
    val bytesFreed: Long = 0L,
    val trashedUrisJson: String = "", // Pipe-separated content URIs for MediaStore restore
    val canRestore: Boolean = false,
    val isRestored: Boolean = false
)

@Entity(tableName = "battery_samples")
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestampEpochMillis: Long,
    val levelPercent: Int,
    val temperatureCelsius: Float,
    val isCharging: Boolean
)
