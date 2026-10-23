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
import com.nexusguard.android.data.repository.CleanerRepository
import com.nexusguard.android.data.repository.SystemSensorsRepository
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ThermalLevelPt
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
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

        // Fase 3 (5): Limpeza agendada semanal — só analisa e notifica («2,3 GB prontos a limpar»). Nunca apaga sem confirmação.
        val cleanReport = cleanerRepository.scanRealDeviceStorage()
        val cleanableBytes = cleanReport.totalCleanableBytes

        dao.insertHistory(
            HistoryEntity(
                actionType = "ANALYSIS",
                title = "Análise semanal · Saúde ${snapshot.healthScore}/100",
                details = "Pronto a limpar: ${NexusFormatters.formatBytes(cleanableBytes)} · Térmico: ${snapshot.thermal.level.labelPt}",
                timestampEpochMillis = System.currentTimeMillis()
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

        // Notificação semanal de limpeza profunda (só análise)
        if (prefs.weeklyScanEnabled && cleanableBytes > 0L) {
            val formattedCleanable = NexusFormatters.formatBytes(cleanableBytes)
            val weeklyNotif = NotificationCompat.Builder(appContext, CHANNEL_WEEKLY)
                .setSmallIcon(R.drawable.ic_stat_gauge)
                .setContentTitle("$formattedCleanable prontos a limpar")
                .setContentText("A análise semanal terminou. Toca para rever e confirmar a Limpeza profunda.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingOpenClean)
                .setAutoCancel(true)
                .build()
            nm.notify(2000, weeklyNotif)
        }

        // 1. Alerta de armazenamento abaixo de 10%
        if (prefs.storageAlertEnabled && snapshot.storage.totalBytes > 0) {
            val freeRatio = snapshot.storage.freeBytes.toDouble() / snapshot.storage.totalBytes.toDouble()
            if (freeRatio < 0.10) {
                val notif = NotificationCompat.Builder(appContext, CHANNEL_STORAGE)
                    .setSmallIcon(R.drawable.ic_stat_gauge)
                    .setContentTitle("Armazenamento abaixo de 10 %")
                    .setContentText("Abre o Nexus Guard para pré-visualizar ficheiros grandes ou transferências antigas.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingOpenClean)
                    .setAutoCancel(true)
                    .build()
                nm.notify(2001, notif)
            }
        }

        // 2. Alerta de telemóvel quente (estado térmico Grave ou superior)
        if (prefs.thermalAlertEnabled && snapshot.thermal.level.severityRank >= ThermalLevelPt.GRAVE.severityRank) {
            val notif = NotificationCompat.Builder(appContext, CHANNEL_THERMAL)
                .setSmallIcon(R.drawable.ic_stat_gauge)
                .setContentTitle("Telemóvel quente (${snapshot.thermal.level.labelPt})")
                .setContentText("O sensor térmico reporta estado ${snapshot.thermal.level.labelPt}.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(2002, notif)
        }

        // 3. Alerta de bateria degradada
        if (prefs.batteryAlertEnabled && !snapshot.battery.isHealthy) {
            val notif = NotificationCompat.Builder(appContext, CHANNEL_BATTERY)
                .setSmallIcon(R.drawable.ic_stat_gauge)
                .setContentTitle("Saúde da bateria: ${snapshot.battery.healthLabelPt}")
                .setContentText("O BatteryManager indica estado ${snapshot.battery.healthLabelPt}.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            nm.notify(2003, notif)
        }

        return Result.success()
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
                    NotificationChannel(CHANNEL_WEEKLY, "Análise Semanal", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(CHANNEL_STORAGE, "Armazenamento Abaixo de 10 %", NotificationManager.IMPORTANCE_HIGH),
                    NotificationChannel(CHANNEL_THERMAL, "Telemóvel Quente (Térmico)", NotificationManager.IMPORTANCE_HIGH),
                    NotificationChannel(CHANNEL_BATTERY, "Saúde da Bateria", NotificationManager.IMPORTANCE_DEFAULT),
                    NotificationChannel(CHANNEL_TASKS, "Fim das Tarefas", NotificationManager.IMPORTANCE_LOW)
                )
                nm.createNotificationChannels(channels)
            }
        }

        fun scheduleOrUpdateWeeklyMonitor(context: Context, enabled: Boolean) {
            ensureNotificationChannels(context)
            val wm = WorkManager.getInstance(context)
            if (enabled) {
                val req = PeriodicWorkRequestBuilder<SystemMonitorWorker>(7, TimeUnit.DAYS).build()
                wm.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    req
                )
            } else {
                wm.cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
