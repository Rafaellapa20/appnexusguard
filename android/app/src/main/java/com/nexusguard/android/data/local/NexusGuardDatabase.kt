package com.nexusguard.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HistoryEntity::class,
        BatterySampleEntity::class,
        FileHashCacheEntity::class,
        NexusTrashEntity::class,
        ChargingSessionEntity::class,
        AppStorageSnapshotEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class NexusGuardDatabase : RoomDatabase() {
    abstract fun dao(): NexusGuardDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_storage_snapshots` (
                        `packageName` TEXT NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `appBytes` INTEGER NOT NULL,
                        `dataBytes` INTEGER NOT NULL,
                        `cacheBytes` INTEGER NOT NULL,
                        `timestampEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`packageName`)
                    )
                    """.trimIndent()
                )
            }
        }

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `file_hash_cache` ADD COLUMN `dHash` INTEGER")
                db.execSQL("ALTER TABLE `file_hash_cache` ADD COLUMN `laplacianSharpness` REAL")
                db.execSQL("ALTER TABLE `battery_samples` ADD COLUMN `currentMa` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `battery_samples` ADD COLUMN `isScreenOn` INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `nexus_trash_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `historyId` INTEGER NOT NULL,
                        `originalPath` TEXT NOT NULL,
                        `trashFilePath` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `trashedAtEpochMillis` INTEGER NOT NULL,
                        `expiresAtEpochMillis` INTEGER NOT NULL,
                        `isRestored` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `charging_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `startEpochMillis` INTEGER NOT NULL,
                        `endEpochMillis` INTEGER NOT NULL,
                        `startPercent` INTEGER NOT NULL,
                        `endPercent` INTEGER NOT NULL,
                        `maxTempCelsius` REAL NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
