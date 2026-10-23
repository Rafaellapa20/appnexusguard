package com.nexusguard.android.domain

import android.os.PowerManager
import java.util.Locale
import kotlin.math.roundToInt

enum class ThermalLevelPt(
    val labelPt: String,
    val severityRank: Int,
    val penaltyPoints: Int
) {
    NORMAL("Normal", 0, 0),
    LIGEIRO("Ligeiro", 1, 5),
    MODERADO("Moderado", 2, 12),
    GRAVE("Grave", 3, 22),
    CRITICO("Crítico", 4, 35),
    INDISPONIVEL("N/D (API < 29)", -1, 0)
}

object ThermalStatusMapper {
    fun fromPowerManagerStatus(status: Int): ThermalLevelPt {
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalLevelPt.NORMAL
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevelPt.LIGEIRO
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevelPt.MODERADO
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevelPt.GRAVE
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevelPt.CRITICO
            else -> ThermalLevelPt.INDISPONIVEL
        }
    }
}

data class SystemHealthInputs(
    val freeStorageBytes: Long,
    val totalStorageBytes: Long,
    val thermalLevel: ThermalLevelPt,
    val batteryTempCelsius: Float?,
    val isBatteryHealthy: Boolean,
    val unusedAppsOver30Days: Int,
    val availableMemoryBytes: Long,
    val totalMemoryBytes: Long
)

object HealthScoreCalculator {

    /**
     * Calcula a pontuação real de Saúde do Sistema (0 a 100) a partir de:
     * - armazenamento livre
     * - estado térmico
     * - saúde e temperatura da bateria
     * - apps sem uso há mais de 30 dias
     * - memória RAM disponível
     */
    fun calculateScore(inputs: SystemHealthInputs): Int {
        var score = 100

        // 1. Armazenamento livre (até -30 pontos)
        if (inputs.totalStorageBytes > 0) {
            val freeRatio = inputs.freeStorageBytes.toDouble() / inputs.totalStorageBytes.toDouble()
            score -= when {
                freeRatio < 0.05 -> 30
                freeRatio < 0.10 -> 22
                freeRatio < 0.15 -> 14
                freeRatio < 0.25 -> 6
                else -> 0
            }
        }

        // 2. Estado térmico (até -35 pontos)
        score -= inputs.thermalLevel.penaltyPoints

        // 3. Saúde e temperatura da bateria (até -25 pontos)
        if (!inputs.isBatteryHealthy) {
            score -= 15
        }
        val temp = inputs.batteryTempCelsius
        if (temp != null) {
            score -= when {
                temp >= 45f -> 15
                temp >= 41f -> 10
                temp >= 38f -> 5
                else -> 0
            }
        }

        // 4. Apps sem uso há > 30 dias (até -10 pontos)
        score -= when {
            inputs.unusedAppsOver30Days >= 15 -> 10
            inputs.unusedAppsOver30Days >= 8 -> 6
            inputs.unusedAppsOver30Days >= 3 -> 3
            else -> 0
        }

        // 5. Memória disponível (até -10 pontos)
        if (inputs.totalMemoryBytes > 0) {
            val memRatio = inputs.availableMemoryBytes.toDouble() / inputs.totalMemoryBytes.toDouble()
            score -= when {
                memRatio < 0.10 -> 10
                memRatio < 0.18 -> 6
                memRatio < 0.25 -> 3
                else -> 0
            }
        }

        return score.coerceIn(0, 100)
    }

    fun statusLabelPt(score: Int): String {
        return when {
            score >= 80 -> "Saúde boa"
            score >= 60 -> "Atenção recomendada"
            else -> "Ação necessária"
        }
    }
}

object NexusFormatters {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale("pt", "PT"), "%.1f GB", bytes / gb)
            bytes >= mb -> String.format(Locale("pt", "PT"), "%d MB", (bytes / mb).roundToInt())
            bytes >= kb -> String.format(Locale("pt", "PT"), "%d KB", (bytes / kb).roundToInt())
            else -> "$bytes B"
        }
    }

    fun formatGigabytesInt(bytes: Long): String {
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale("pt", "PT"), "%.0f GB", gb)
    }

    fun formatRelativeTimePt(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diffMillis = (nowMillis - timestampMillis).coerceAtLeast(0L)
        val minutes = diffMillis / 60_000L
        val hours = minutes / 60L
        val days = hours / 24L
        return when {
            minutes < 1 -> "agora"
            minutes < 60 -> "há $minutes min"
            hours < 24 -> "há $hours h"
            days == 1L -> "ontem"
            else -> "há $days d"
        }
    }
}
