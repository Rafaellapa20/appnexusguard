package com.nexusguard.android.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexusguard.android.MainActivity
import com.nexusguard.android.R
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.data.preferences.UserPreferencesRepository
import com.nexusguard.android.data.repository.CleanCategoryType
import com.nexusguard.android.data.repository.CleanerRepository
import com.nexusguard.android.data.repository.SystemSensorsRepository
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ScheduledCleanAuthorization
import com.nexusguard.android.domain.ScheduledCleanPlanner
import com.nexusguard.android.domain.SmartAlertEvaluationInput
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

@HiltWorker
class SystemMonitorWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sensorsRepository: SystemSensorsRepository,
    private val cleanerRepository: CleanerRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val dao: NexusGuardDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        ensureNotificationChannels(appContext)
        val prefs = preferencesRepository.preferencesFlow.first()
        val snapshot = sensorsRepository.readCurrentSnapshot()
        val nowMillis = System.currentTimeMillis()

        val cleanReport = cleanerRepository.scanRealDeviceStorage()
        val emptyPaths = cleanReport.categories
            .find { it.type == CleanCategoryType.EMPTY_FOLDERS }
            ?.emptyFoldersPaths
            .orEmpty()

        val (expiredTrashCount, expiredTrashBytes) = countExpiredNexusTrash(nowMillis)
        val ownCacheBytes = computeDirectoryBytes(appContext.cacheDir)

        val authorization = ScheduledCleanAuthorization(
            autoCleanEmptyFolders = prefs.autoCleanEmptyFolders,
            autoPurgeExpiredNexusTrash = prefs.autoPurgeExpiredTrash,
            autoClearOwnCache = prefs.autoClearOwnCache
        )
        val plan = ScheduledCleanPlanner.buildExecutionPlan(
            authorization = authorization,
            emptyFoldersCount = emptyPaths.size,
            expiredTrashFilesCount = expiredTrashCount,
            expiredTrashBytes = expiredTrashBytes,
            ownCacheBytes = ownCacheBytes,
            remainingCleanableBytes = cleanReport.totalCleanableBytes
        )

        var actualEmptyDeleted = 0
        var actualBytesAutoFreed = 0L

        if (prefs.weeklyScanEnabled) {
            if (plan.shouldDeleteEmptyFolders && emptyPaths.isNotEmpty()) {
                for (path in emptyPaths) {
                    try {
                        val dir = File(path)
                        if (dir.exists() && dir.isDirectory && (dir.listFiles()?.isEmpty() == true)) {
                            if (dir.delete()) actualEmptyDeleted++
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            if (plan.shouldPurgeExpiredNexusTrash) {
                actualBytesAutoFreed += purgeExpiredNexusTrashItems(nowMillis)
            }
            if (plan.shouldClearOwnCache) {
                actualBytesAutoFreed += clearOwnCacheDir(appContext.cacheDir)
            }
        }

        val freqLabel = when (prefs.scheduledCleanFrequencyDays) {
            1 -> "diária"
            30 -> "mensal"
            else -> "semanal"
        }

        dao.insertHistory(
            HistoryEntity(
                actionType = "ANALYSIS",
                title = "Limpeza agendada ($freqLabel) · Saúde ${snapshot.healthScore}/100",
                details = buildString {
                    append("Auto-limpo: ${NexusFormatters.formatBytes(actualBytesAutoFreed)} ($actualEmptyDeleted pastas vazias)")
                    append(" · Para rever: ${NexusFormatters.formatBytes(plan.remainingBytesRequiringReview)}")
                },
                timestampEpochMillis = nowMillis,
                bytesFreed = actualBytesAutoFreed
            )
        )

        if (!canPostNotifications(appContext)) {
            return Result.success()
        }

        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val openCleanIntent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_START_DEEP_CLEAN, true)
        }
        val pendingOpenClean = PendingIntent.getActivity(
            appContext,
            2000,
            openCleanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notificação da limpeza agendada com botão «Rever» para o que exige confirmação
        if (prefs.weeklyScanEnabled && (plan.remainingBytesRequiringReview > 0L || actualBytesAutoFreed > 0L)) {
            val formattedRemaining = NexusFormatters.formatBytes(plan.remainingBytesRequiringReview)
            val title = if (plan.remainingBytesRequiringReview > 0L) {
                "$formattedRemaining prontos para rever"
            } else {
                "Limpeza automática concluída (${NexusFormatters.formatBytes(actualBytesAutoFreed)})"
            }
            val body = if (actualBytesAutoFreed > 0L || actualEmptyDeleted > 0) {
                "Auto-limpo: ${NexusFormatters.formatBytes(actualBytesAutoFreed)} e $actualEmptyDeleted pasta(s) vazia(s). Toca em Rever para o restante."
            } else {
                "A análise agendada encontrou $formattedRemaining que exigem a tua confirmação."
            }

            val scheduledNotif = NotificationCompat.Builder(appContext, CHANNEL_WEEKLY)
                .setSmallIcon(R.drawable.ic_stat_gauge)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingOpenClean)
                .addAction(0, "Rever", pendingOpenClean)
                .setAutoCancel(true)
                .build()
            nm.notify(2000, scheduledNotif)
        }

        // Fase 8: Avisos inteligentes configuráveis (máximo 1 notificação agrupada por dia)
        if (ScheduledCleanPlanner.canSendDailyGroupedAlert(nowMillis, prefs.lastSmartAlertEpochMillis)) {
            val newAppDrain = snapshot.installedApps.firstOrNull { app ->
                (app.foregroundTime24hMillis ?: 0L) >= 45 * 60_000L &&
                    (app.daysSinceUse ?: 0) <= 1
            }?.appName

            val activeAlerts = ScheduledCleanPlanner.evaluateSmartAlerts(
                SmartAlertEvaluationInput(
                    storageAlertEnabled = prefs.storageAlertEnabled,
                    freeStorageBytes = snapshot.storage.freeBytes,
                    totalStorageBytes = snapshot.storage.totalBytes,
                    junkOver1GbAlertEnabled = prefs.junkOver1GbAlertEnabled,
                    totalCleanableJunkBytes = cleanReport.totalCleanableBytes,
                    newAppBatteryAlertEnabled = prefs.newAppBatteryAlertEnabled,
                    newAppHighBatteryName = newAppDrain,
                    thermalAlertEnabled = prefs.thermalAlertEnabled,
                    thermalLevel = snapshot.thermal.level,
                    charge80AlertEnabled = prefs.chargeLimitAlarmEnabled,
                    isCharging = snapshot.battery.isCharging,
                    batteryPercent = snapshot.battery.levelPercent,
                    chargeTargetPercent = prefs.chargeLimitTargetPercent
                )
            )

            if (activeAlerts.isNotEmpty()) {
                val inboxStyle = NotificationCompat.InboxStyle()
                    .setBigContentTitle("Avisos inteligentes Nexus (${activeAlerts.size})")
                activeAlerts.forEach { line -> inboxStyle.addLine("• $line") }

                val groupedNotif = NotificationCompat.Builder(appContext, CHANNEL_STORAGE)
                    .setSmallIcon(R.drawable.ic_stat_gauge)
                    .setContentTitle(
                        if (activeAlerts.size == 1) activeAlerts.first()
                        else "${activeAlerts.size} avisos inteligentes do sistema"
                    )
                    .setContentText(activeAlerts.joinToString(" · "))
                    .setStyle(inboxStyle)
                    .setGroup("nexus_smart_alerts_daily")
                    .setGroupSummary(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingOpenClean)
                    .addAction(0, "Rever", pendingOpenClean)
                    .setAutoCancel(true)
                    .build()

                nm.notify(2010, groupedNotif)
                preferencesRepository.setLastSmartAlertSent(nowMillis)
            }
        }

        return Result.success()
    }

    private suspend fun countExpiredNexusTrash(nowMillis: Long): Pair<Int, Long> {
        return try {
            val expired = dao.getExpiredNexusTrashItems(nowMillis)
            expired.size to expired.sumOf { it.sizeBytes }
        } catch (_: Exception) {
            0 to 0L
        }
    }

    private suspend fun purgeExpiredNexusTrashItems(nowMillis: Long): Long {
        return try {
            val expired = dao.getExpiredNexusTrashItems(nowMillis)
            var freed = 0L
            val purgedIds = mutableListOf<Long>()
            for (item in expired) {
                val file = File(item.trashFilePath)
                if (!file.exists() || file.deleteRecursively()) {
                    purgedIds.add(item.id)
                    freed += item.sizeBytes
                }
            }
            if (purgedIds.isNotEmpty()) {
                dao.deleteNexusTrashItemsByIds(purgedIds)
            }
            freed
        } catch (_: Exception) {
            0L
        }
    }

    private fun computeDirectoryBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }

    private fun clearOwnCacheDir(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var freed = 0L
        dir.listFiles()?.forEach { child ->
            val len = computeDirectoryBytes(child)
            if (child.deleteRecursively()) {
                freed += len
            }
        }
        return freed
    }

    companion object {
        const val WORK_NAME = "nexus_guard_weekly_monitor"
        const val CHANNEL_WEEKLY = "nexus_channel_weekly"
        const val CHANNEL_STORAGE = "nexus_channel_storage"
        const val CHANNEL_THERMAL = "nexus_channel_thermal"
        const val CHANNEL_BATTERY = "nexus_channel_battery"
        const val CHANNEL_TASKS = "nexus_channel_tasks"

        fun canPostNotifications(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channels = listOf(
                    NotificationChannel(CHANNEL_WEEKLY, "Limpeza Agendada", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(CHANNEL_STORAGE, "Avisos Inteligentes (Agrupados)", NotificationManager.IMPORTANCE_HIGH),
                    NotificationChannel(CHANNEL_THERMAL, "Telemóvel Quente (Térmico)", NotificationManager.IMPORTANCE_HIGH),
                    NotificationChannel(CHANNEL_BATTERY, "Saúde da Bateria", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(CHANNEL_TASKS, "Fim das Tarefas", NotificationManager.IMPORTANCE_LOW)
                )
                nm.createNotificationChannels(channels)
            }
        }

        fun scheduleOrUpdateWeeklyMonitor(
            context: Context,
            enabled: Boolean,
            frequencyDays: Int = 7,
            targetHourOfDay: Int = 3
        ) {
            ensureNotificationChannels(context)
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                val delayMs = ScheduledCleanPlanner.computeInitialDelayMillis(
                    nowEpochMillis = System.currentTimeMillis(),
                    targetHourOfDay = targetHourOfDay
                )
                val intervalDays = frequencyDays.coerceIn(1, 30).toLong()
                val req = PeriodicWorkRequestBuilder<SystemMonitorWorker>(intervalDays, TimeUnit.DAYS)
                    .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                    .build()
                wm.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    req
                )
            } else {
                wm.cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
