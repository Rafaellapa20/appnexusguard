package com.nexusguard.android.worker

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nexusguard.android.R
import com.nexusguard.android.data.local.BatterySampleEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.data.preferences.UserPreferencesRepository
import com.nexusguard.android.data.repository.BatteryProfileManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@HiltWorker
class BatterySampleWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: NexusGuardDao,
    private val preferencesRepository: UserPreferencesRepository,
    private val batteryProfileManager: BatteryProfileManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        SystemMonitorWorker.ensureNotificationChannels(appContext)
        val now = System.currentTimeMillis()

        val intent = ContextCompat.registerReceiver(
            appContext,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val levelPercent = if (level >= 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        val rawTempDeci = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempCelsius = if (rawTempDeci > 0) rawTempDeci / 10f else 0f

        val status = intent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val currentNowUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentNowUa != Int.MIN_VALUE && currentNowUa != 0) {
            val raw = abs(currentNowUa)
            if (raw > 10_000) raw / 1000 else raw
        } else {
            0
        }

        val isScreenOn = powerManager.isInteractive

        if (rawTempDeci > 0) {
            dao.insertBatterySample(
                BatterySampleEntity(
                    timestampEpochMillis = now,
                    levelPercent = levelPercent,
                    temperatureCelsius = tempCelsius,
                    isCharging = isCharging,
                    currentMa = currentMa,
                    isScreenOn = isScreenOn
                )
            )
            dao.pruneOldBatterySamples(now - 24L * 3600_000L)
        }

        val prefs = preferencesRepository.preferencesFlow.first()
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Fase 2 (3): Ativar perfil Poupança Nexus automaticamente abaixo de X % se autorizado
        if (!isCharging &&
            prefs.autoBatterySaverEnabled &&
            !prefs.nexusBatterySaverActive &&
            levelPercent in 1..prefs.autoBatterySaverThresholdPercent
        ) {
            val applied = batteryProfileManager.activateNexusSaverProfile(isAutoTriggered = true)
            if (SystemMonitorWorker.canPostNotifications(appContext)) {
                val text = if (applied) {
                    "Bateria a $levelPercent% (≤ ${prefs.autoBatterySaverThresholdPercent}%). Ecrã ajustado para 30 s e brilho automático."
                } else {
                    "Bateria a $levelPercent%. Abre o Nexus Guard para autorizar WRITE_SETTINGS e ativar o perfil Poupança Nexus."
                }
                val notif = NotificationCompat.Builder(appContext, SystemMonitorWorker.CHANNEL_BATTERY)
                    .setSmallIcon(R.drawable.ic_stat_gauge)
                    .setContentTitle("Poupança Nexus · Bateria a $levelPercent%")
                    .setContentText(text)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
                nm.notify(2010, notif)
            }
        }

        // Fase 2 (4): Alerta quando a temperatura passa os 40 °C a carregar
        if (isCharging && tempCelsius > 40.0f && SystemMonitorWorker.canPostNotifications(appContext)) {
            val tempFormatted = String.format(Locale.forLanguageTag("pt-PT"), "%.1f °C", tempCelsius)
            val notif = NotificationCompat.Builder(appContext, SystemMonitorWorker.CHANNEL_THERMAL)
                .setSmallIcon(R.drawable.ic_stat_gauge)
                .setContentTitle("Bateria acima de 40 °C a carregar ($tempFormatted)")
                .setContentText("Retira a capa ou desliga o carregador para proteger a saúde química da bateria.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
            nm.notify(2011, notif)
        }

        return Result.success()
    }

    companion object {
        const val WORK_NAME = "nexus_battery_sample_15m_worker"

        fun schedulePeriodicSampling(context: Context) {
            val req = PeriodicWorkRequestBuilder<BatterySampleWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}
