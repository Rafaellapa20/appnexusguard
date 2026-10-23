package com.nexusguard.android.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexusguard.android.data.preferences.UserPreferencesRepository
import com.nexusguard.android.data.repository.HibernationEngine
import com.nexusguard.android.flavor.HibernationFlavorDelegate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class AutoHibernationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val preferencesRepository: UserPreferencesRepository,
    private val hibernationEngine: HibernationEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!HibernationFlavorDelegate.IS_HIBERNATION_SUPPORTED) {
            return Result.success()
        }
        val prefs = preferencesRepository.preferencesFlow.first()
        if (!prefs.autoHibernationEnabled) {
            return Result.success()
        }
        hibernationEngine.runAutomaticHibernationNow(modeLabelPt = "Automático · Periódico")
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "nexus_auto_hibernation_periodic_work"

        fun syncPeriodicSchedule(context: Context, enabled: Boolean, intervalHours: Int) {
            val wm = WorkManager.getInstance(context)
            if (!enabled || !HibernationFlavorDelegate.IS_HIBERNATION_SUPPORTED) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val safeHours = intervalHours.coerceIn(6, 24).toLong()
            val request = PeriodicWorkRequestBuilder<AutoHibernationWorker>(safeHours, TimeUnit.HOURS)
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
