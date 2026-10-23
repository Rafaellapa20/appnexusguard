package com.nexusguard.android.service

import android.app.GameManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.data.repository.SystemSensorsRepository
import com.nexusguard.android.domain.ThermalLevelPt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class BoostSessionSummary(
    val gameName: String,
    val durationFormatted: String,
    val maxBatteryTempCelsius: Float?,
    val worstThermalLevel: ThermalLevelPt,
    val batteryUsedPercent: Int,
    val appsStopped: Int
)

data class BoostSessionState(
    val isActive: Boolean = false,
    val gamePackage: String = "",
    val gameName: String = "",
    val gameMonogram: String = "",
    val elapsedSeconds: Long = 0L,
    val dndEnabled: Boolean = false,
    val appsStoppedCount: Int = 0,
    val startBatteryPercent: Int = 0,
    val currentBatteryPercent: Int = 0,
    val currentTempCelsius: Float? = null,
    val maxTempCelsius: Float? = null,
    val currentThermalLevel: ThermalLevelPt = ThermalLevelPt.NORMAL,
    val worstThermalLevel: ThermalLevelPt = ThermalLevelPt.NORMAL,
    val gameModeLabelPt: String = "Padrão",
    val lastSummary: BoostSessionSummary? = null
) {
    val formattedElapsed: String
        get() {
            val h = elapsedSeconds / 3600L
            val m = (elapsedSeconds % 3600L) / 60L
            val s = elapsedSeconds % 60L
            return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        }

    val batteryUsedPercent: Int
        get() = (startBatteryPercent - currentBatteryPercent).coerceAtLeast(0)
}

@Singleton
class GameBoostManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sensorsRepository: SystemSensorsRepository,
    private val dao: NexusGuardDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null
    private var previousInterruptionFilter: Int? = null

    private val _sessionState = MutableStateFlow(BoostSessionState())
    val sessionState: StateFlow<BoostSessionState> = _sessionState.asStateFlow()

    fun hasDndPermission(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun readAndroidGameModeLabel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                val gm = context.getSystemService(Context.GAME_SERVICE) as? GameManager
                when (gm?.gameMode) {
                    GameManager.GAME_MODE_PERFORMANCE -> "Desempenho (GameManager)"
                    GameManager.GAME_MODE_BATTERY -> "Poupança de Bateria (GameManager)"
                    GameManager.GAME_MODE_STANDARD -> "Padrão (GameManager)"
                    else -> "Não suportado pelo jogo"
                }
            } catch (_: Exception) {
                "Padrão"
            }
        }
        return "API < 31"
    }

    suspend fun startBoostSession(
        gamePackage: String,
        gameName: String,
        gameMonogram: String,
        enableDnd: Boolean,
        stopBackgroundApps: Boolean,
        launchExternalGame: Boolean
    ) {
        val snapshot = sensorsRepository.readCurrentSnapshot()
        val stoppedCount = if (stopBackgroundApps) {
            sensorsRepository.stopPermittedBackgroundApps(recordInHistory = false)
        } else {
            0
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        var dndActive = false
        if (enableDnd && nm.isNotificationPolicyAccessGranted) {
            previousInterruptionFilter = nm.currentInterruptionFilter
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                dndActive = true
            } catch (_: Exception) {
            }
        }

        val initialTemp = snapshot.battery.temperatureCelsius
        val initialThermal = snapshot.thermal.level

        _sessionState.value = BoostSessionState(
            isActive = true,
            gamePackage = gamePackage,
            gameName = gameName,
            gameMonogram = gameMonogram,
            elapsedSeconds = 0L,
            dndEnabled = dndActive,
            appsStoppedCount = stoppedCount,
            startBatteryPercent = snapshot.battery.levelPercent,
            currentBatteryPercent = snapshot.battery.levelPercent,
            currentTempCelsius = initialTemp,
            maxTempCelsius = initialTemp,
            currentThermalLevel = initialThermal,
            worstThermalLevel = initialThermal,
            gameModeLabelPt = readAndroidGameModeLabel(),
            lastSummary = null
        )

        // Inicia o serviço em primeiro plano com notificação contínua
        try {
            val serviceIntent = Intent(context, GameBoostForegroundService::class.java).apply {
                action = GameBoostForegroundService.ACTION_START
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (_: Exception) {
        }

        timerJob?.cancel()
        timerJob = scope.launch {
            while (_sessionState.value.isActive) {
                delay(1_000L)
                val live = sensorsRepository.liveSnapshot.value
                val cur = _sessionState.value
                val liveTemp = live.battery.temperatureCelsius ?: cur.currentTempCelsius
                val maxTemp = when {
                    cur.maxTempCelsius == null -> liveTemp
                    liveTemp == null -> cur.maxTempCelsius
                    else -> maxOf(cur.maxTempCelsius, liveTemp)
                }
                val worstThermal = if (live.thermal.level.severityRank > cur.worstThermalLevel.severityRank) {
                    live.thermal.level
                } else {
                    cur.worstThermalLevel
                }

                _sessionState.value = cur.copy(
                    elapsedSeconds = cur.elapsedSeconds + 1L,
                    currentBatteryPercent = live.battery.levelPercent,
                    currentTempCelsius = liveTemp,
                    maxTempCelsius = maxTemp,
                    currentThermalLevel = live.thermal.level,
                    worstThermalLevel = worstThermal
                )
            }
        }

        if (launchExternalGame && gamePackage.isNotBlank() && gamePackage != context.packageName) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(gamePackage)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun stopBoostSession() {
        val current = _sessionState.value
        if (!current.isActive) return
        timerJob?.cancel()

        // Repõe o Não Incomodar
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val prevFilter = previousInterruptionFilter
        if (prevFilter != null && nm.isNotificationPolicyAccessGranted) {
            try {
                nm.setInterruptionFilter(prevFilter)
            } catch (_: Exception) {
            }
        }
        previousInterruptionFilter = null

        val summary = BoostSessionSummary(
            gameName = current.gameName,
            durationFormatted = current.formattedElapsed,
            maxBatteryTempCelsius = current.maxTempCelsius,
            worstThermalLevel = current.worstThermalLevel,
            batteryUsedPercent = current.batteryUsedPercent,
            appsStopped = current.appsStoppedCount
        )

        _sessionState.value = current.copy(
            isActive = false,
            lastSummary = summary
        )

        try {
            val stopIntent = Intent(context, GameBoostForegroundService::class.java).apply {
                action = GameBoostForegroundService.ACTION_STOP
            }
            context.startService(stopIntent)
        } catch (_: Exception) {
        }

        scope.launch(Dispatchers.IO) {
            val maxTempLabel = summary.maxBatteryTempCelsius?.let {
                String.format(Locale("pt", "PT"), "%.1f °C", it)
            } ?: "N/D"
            dao.insertHistory(
                HistoryEntity(
                    actionType = "BOOST",
                    title = "Boost · ${summary.gameName} (${summary.durationFormatted})",
                    details = "Temp. máx. bateria: $maxTempLabel · Pior estado térmico: ${summary.worstThermalLevel.labelPt} · Bateria gasta: ${summary.batteryUsedPercent}%",
                    timestampEpochMillis = System.currentTimeMillis(),
                    bytesFreed = 0L,
                    canRestore = false
                )
            )
        }
    }

    fun dismissSummaryDialog() {
        _sessionState.value = _sessionState.value.copy(lastSummary = null)
    }
}
