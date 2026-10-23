package com.nexusguard.android.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nexusguard.android.MainActivity
import com.nexusguard.android.R
import com.nexusguard.android.data.local.ChargingSessionEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.data.preferences.UserPreferencesRepository
import com.nexusguard.android.worker.SystemMonitorWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Fase 2 (4): FGS curto `specialUse` ativo apenas enquanto o telemóvel carrega
 * e apenas quando o alarme de limite de carga (80/85/90 %) está ligado.
 */
@AndroidEntryPoint
class ChargeMonitorForegroundService : Service() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var dao: NexusGuardDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var batteryReceiver: BroadcastReceiver? = null
    private var alarmTriggeredInSession = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        SystemMonitorWorker.ensureNotificationChannels(this)
        startShortForegroundNotification(targetPercent = 80, currentPercent = 0)

        if (batteryReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, battIntent: Intent?) {
                    handleBatteryUpdate(battIntent)
                }
            }
            batteryReceiver = receiver
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        return START_STICKY
    }

    private fun handleBatteryUpdate(battIntent: Intent?) {
        if (battIntent == null) return
        val level = battIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val pct = if (level >= 0) ((level * 100f) / scale).toInt().coerceIn(0, 100) else return
        val rawTempDeci = battIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
        val tempC = rawTempDeci / 10f
        val status = battIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        scope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            val now = System.currentTimeMillis()
            val startMillis = if (prefs.activeChargeStartEpochMillis > 0L) prefs.activeChargeStartEpochMillis else now
            val startPct = if (prefs.activeChargeStartEpochMillis > 0L) prefs.activeChargeStartPercent else pct
            val maxTempDeci = maxOf(prefs.activeChargeMaxTempDeci, rawTempDeci)

            preferencesRepository.updateActiveChargingSession(
                startMillis = startMillis,
                startPercent = startPct,
                maxTempDeci = maxTempDeci
            )

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Alerta se a temperatura passar os 40 °C a carregar
            if (isCharging && tempC > 40.0f && SystemMonitorWorker.canPostNotifications(this@ChargeMonitorForegroundService)) {
                val tempFormatted = String.format(Locale.forLanguageTag("pt-PT"), "%.1f °C", tempC)
                val hotNotif = NotificationCompat.Builder(this@ChargeMonitorForegroundService, SystemMonitorWorker.CHANNEL_THERMAL)
                    .setSmallIcon(R.drawable.ic_stat_gauge)
                    .setContentTitle("Temperatura de carga elevada ($tempFormatted)")
                    .setContentText("A bateria ultrapassou os 40 °C durante o carregamento.")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
                nm.notify(2011, hotNotif)
            }

            if (!isCharging) {
                finalizeChargingSession(pct, maxTempDeci)
                stopSelf()
                return@launch
            }

            if (!prefs.chargeLimitAlarmEnabled) {
                stopSelf()
                return@launch
            }

            startShortForegroundNotification(targetPercent = prefs.chargeLimitTargetPercent, currentPercent = pct)

            if (!alarmTriggeredInSession && pct >= prefs.chargeLimitTargetPercent) {
                alarmTriggeredInSession = true
                if (SystemMonitorWorker.canPostNotifications(this@ChargeMonitorForegroundService)) {
                    val alarmNotif = NotificationCompat.Builder(this@ChargeMonitorForegroundService, SystemMonitorWorker.CHANNEL_BATTERY)
                        .setSmallIcon(R.drawable.ic_stat_gauge)
                        .setContentTitle("Limite de carga atingido ($pct%)")
                        .setContentText("A bateria chegou ao limite configurado de ${prefs.chargeLimitTargetPercent}%. Podes desligar o carregador para preservar a saúde.")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(2012, alarmNotif)
                }
                stopSelf()
            }
        }
    }

    private suspend fun finalizeChargingSession(endPercent: Int, maxTempDeci: Int) {
        val prefs = preferencesRepository.preferencesFlow.first()
        if (prefs.activeChargeStartEpochMillis > 0L) {
            val now = System.currentTimeMillis()
            if (now - prefs.activeChargeStartEpochMillis >= 60_000L) {
                dao.insertChargingSession(
                    ChargingSessionEntity(
                        startEpochMillis = prefs.activeChargeStartEpochMillis,
                        endEpochMillis = now,
                        startPercent = prefs.activeChargeStartPercent,
                        endPercent = endPercent,
                        maxTempCelsius = (maxOf(prefs.activeChargeMaxTempDeci, maxTempDeci)) / 10f
                    )
                )
            }
            preferencesRepository.clearActiveChargingSession()
        }
    }

    private fun startShortForegroundNotification(targetPercent: Int, currentPercent: Int) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, ChargeMonitorForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (currentPercent > 0) {
            "Alarme de carga ativo ($currentPercent% → $targetPercent%)"
        } else {
            "Alarme de carga ativo (meta $targetPercent%)"
        }

        val notif = NotificationCompat.Builder(this, SystemMonitorWorker.CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_stat_gauge)
            .setContentTitle(title)
            .setContentText("Notifica quando a bateria atingir $targetPercent% para proteger os ciclos")
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(0, "Desligar alarme", pendingStop)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notif, fgsType)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        batteryReceiver = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 3102
        const val ACTION_START = "com.nexusguard.android.action.START_CHARGE_MONITOR"
        const val ACTION_STOP = "com.nexusguard.android.action.STOP_CHARGE_MONITOR"
    }
}
