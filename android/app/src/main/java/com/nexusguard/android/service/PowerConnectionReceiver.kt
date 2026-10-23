package com.nexusguard.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.nexusguard.android.data.local.ChargingSessionEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.data.preferences.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fase 2 (4): Receiver de `ACTION_POWER_CONNECTED` e `ACTION_POWER_DISCONNECTED`
 * para iniciar/terminar o histórico de sessões de carga e o FGS curto `specialUse` do alarme 80/85/90 %.
 */
@AndroidEntryPoint
class PowerConnectionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var dao: NexusGuardDao

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        scope.launch {
            try {
                val battIntent = ContextCompat.registerReceiver(
                    context,
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                val level = battIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
                val scale = (battIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
                val pct = ((level * 100f) / scale).toInt().coerceIn(0, 100)
                val rawTempDeci = battIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                val now = System.currentTimeMillis()
                val prefs = preferencesRepository.preferencesFlow.first()

                when (action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        preferencesRepository.updateActiveChargingSession(
                            startMillis = now,
                            startPercent = pct,
                            maxTempDeci = rawTempDeci
                        )
                        if (prefs.chargeLimitAlarmEnabled) {
                            try {
                                val fgsIntent = Intent(context, ChargeMonitorForegroundService::class.java).apply {
                                    this.action = ChargeMonitorForegroundService.ACTION_START
                                }
                                ContextCompat.startForegroundService(context, fgsIntent)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        if (prefs.activeChargeStartEpochMillis > 0L && now - prefs.activeChargeStartEpochMillis >= 60_000L) {
                            dao.insertChargingSession(
                                ChargingSessionEntity(
                                    startEpochMillis = prefs.activeChargeStartEpochMillis,
                                    endEpochMillis = now,
                                    startPercent = prefs.activeChargeStartPercent,
                                    endPercent = pct,
                                    maxTempCelsius = maxOf(prefs.activeChargeMaxTempDeci, rawTempDeci) / 10f
                                )
                            )
                        }
                        preferencesRepository.clearActiveChargingSession()
                        try {
                            val stopIntent = Intent(context, ChargeMonitorForegroundService::class.java).apply {
                                this.action = ChargeMonitorForegroundService.ACTION_STOP
                            }
                            context.stopService(stopIntent)
                        } catch (_: Exception) {
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
