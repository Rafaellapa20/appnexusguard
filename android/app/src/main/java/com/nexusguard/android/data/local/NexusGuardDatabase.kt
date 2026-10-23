package com.nexusguard.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HistoryEntity::class,
        BatterySampleEntity::class,
        FileHashCacheEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NexusGuardDatabase : RoomDatabase() {
    abstract fun dao(): NexusGuardDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `file_hash_cache` (
                        `id` INTEGER NOT NULL,
                        `dateModified` INTEGER NOT NULL,
                        `size` INTEGER NOT NULL,
                        `partialHash` TEXT NOT NULL,
                        `fullHash` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
