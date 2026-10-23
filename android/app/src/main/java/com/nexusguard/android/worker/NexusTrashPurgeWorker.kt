package com.nexusguard.android.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexusguard.android.data.local.NexusGuardDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class NexusTrashPurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: NexusGuardDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val expiredItems = dao.getExpiredNexusTrashItems(now)
        if (expiredItems.isNotEmpty()) {
            val purgedIds = mutableListOf<Long>()
            for (item in expiredItems) {
                try {
                    val file = File(item.trashFilePath)
                    if (!file.exists() || file.deleteRecursively()) {
                        purgedIds.add(item.id)
                    }
                } catch (_: Exception) {
                }
            }
            if (purgedIds.isNotEmpty()) {
                dao.deleteNexusTrashItemsByIds(purgedIds)
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "nexus_trash_purge_worker"

        fun scheduleDailyPurge(context: Context) {
            val req = PeriodicWorkRequestBuilder<NexusTrashPurgeWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
