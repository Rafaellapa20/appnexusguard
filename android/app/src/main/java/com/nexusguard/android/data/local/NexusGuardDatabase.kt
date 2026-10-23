package com.nexusguard.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class, BatterySampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class NexusGuardDatabase : RoomDatabase() {
    abstract fun dao(): NexusGuardDao
}
