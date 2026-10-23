package com.nexusguard.android.domain

import androidx.compose.runtime.Immutable
import com.nexusguard.android.data.local.BatterySampleEntity
import java.util.Locale
import kotlin.math.max

@Immutable
data class MeasuredBatteryConsumption(
    val hasEnoughSamples2h: Boolean = false,
    val collectedHours: Double = 0.0,
    val collectingStatusText: String = "a recolher dados (0,0/2 h)",
    val screenOnDrainPercentPerHour: Double? = null,
    val screenOffIdleDrainPercentPerHour: Double? = null,
    val overallDrainPercentPerHour6h: Double? = null,
    val estimatedRemainingMinutes6h: Long? = null,
    val estimatedRemainingTextPt: String = "a recolher dados (0,0/2 h)"
)

object BatteryRateCalculator {
    const val MIN_REQUIRED_SPAN_MILLIS: Long = 2L * 3600_000L // 2 horas
    const val SIX_HOURS_WINDOW_MILLIS: Long = 6L * 3600_000L // 6 horas

    /**
     * Fase 2 (1): Calcula a partir das amostras reais:
     * - Consumo em repouso (%/h, ecrã desligado);
     * - Consumo com ecrã ligado (%/h);
     * - Tempo restante estimado com base nas últimas 6 h reais;
     * - Com menos de 2 h de amostras, mostra «a recolher dados (x/2 h)».
     */
    fun calculateConsumption(
        samples: List<BatterySampleEntity>,
        currentLevelPercent: Int,
        nowMillis: Long
    ): MeasuredBatteryConsumption {
        val sorted = samples.sortedBy { it.timestampEpochMillis }
        if (sorted.size < 2) {
            return MeasuredBatteryConsumption(
                hasEnoughSamples2h = false,
                collectedHours = 0.0,
                collectingStatusText = "a recolher dados (0,0/2 h)",
                estimatedRemainingTextPt = "a recolher dados (0,0/2 h)"
            )
        }

        val oldestTs = sorted.first().timestampEpochMillis
        val newestTs = max(sorted.last().timestampEpochMillis, nowMillis)
        val spanMillis = (newestTs - oldestTs).coerceAtLeast(0L)
        val collectedHours = (spanMillis.toDouble() / 3_600_000.0).coerceAtMost(24.0)
        val formattedHours = String.format(Locale.forLanguageTag("pt-PT"), "%.1f", collectedHours.coerceAtMost(2.0))
        val collectingText = "a recolher dados ($formattedHours/2 h)"

        if (spanMillis < MIN_REQUIRED_SPAN_MILLIS) {
            return MeasuredBatteryConsumption(
                hasEnoughSamples2h = false,
                collectedHours = collectedHours,
                collectingStatusText = collectingText,
                estimatedRemainingTextPt = collectingText
            )
        }

        var screenOnDropPct = 0.0
        var screenOnDurationHours = 0.0
        var screenOffDropPct = 0.0
        var screenOffDurationHours = 0.0

        var window6hDropPct = 0.0
        var window6hDischargingHours = 0.0
        val sixHoursCutoff = nowMillis - SIX_HOURS_WINDOW_MILLIS

        for (i in 0 until sorted.lastIndex) {
            val prev = sorted[i]
            val next = sorted[i + 1]
            val dtMs = next.timestampEpochMillis - prev.timestampEpochMillis
            // Ignora intervalos inválidos ou superiores a 3 horas sem amostragem
            if (dtMs <= 10_000L || dtMs > 3L * 3600_000L) continue

            // Só contabiliza intervalos de descarga (ambas as amostras sem carregar e nível não subiu)
            if (!prev.isCharging && !next.isCharging && prev.levelPercent >= next.levelPercent) {
                val drop = (prev.levelPercent - next.levelPercent).toDouble()
                val dtHours = dtMs.toDouble() / 3_600_000.0

                if (prev.isScreenOn || next.isScreenOn) {
                    screenOnDropPct += drop
                    screenOnDurationHours += dtHours
                } else {
                    screenOffDropPct += drop
                    screenOffDurationHours += dtHours
                }

                if (next.timestampEpochMillis >= sixHoursCutoff) {
                    window6hDropPct += drop
                    window6hDischargingHours += dtHours
                }
            }
        }

        val screenOnRate = if (screenOnDurationHours >= 0.15) {
            screenOnDropPct / screenOnDurationHours
        } else {
            null
        }

        val screenOffRate = if (screenOffDurationHours >= 0.25) {
            screenOffDropPct / screenOffDurationHours
        } else {
            null
        }

        val overall6hRate = when {
            window6hDischargingHours >= 0.25 && window6hDropPct > 0.0 ->
                window6hDropPct / window6hDischargingHours
            (screenOnDurationHours + screenOffDurationHours) >= 0.25 && (screenOnDropPct + screenOffDropPct) > 0.0 ->
                (screenOnDropPct + screenOffDropPct) / (screenOnDurationHours + screenOffDurationHours)
            else -> null
        }

        val remainingMinutes = if (overall6hRate != null && overall6hRate > 0.1 && currentLevelPercent > 0) {
            val hoursRemaining = (currentLevelPercent.toDouble() / overall6hRate).coerceAtMost(96.0)
            (hoursRemaining * 60.0).toLong()
        } else {
            null
        }

        val remainingText = if (remainingMinutes != null && remainingMinutes > 0L) {
            val h = remainingMinutes / 60L
            val m = remainingMinutes % 60L
            if (h > 0L) "~${h} h ${m} min restantes (média 6 h)" else "~${m} min restantes (média 6 h)"
        } else {
            "Descarga estável nas últimas 6 h"
        }

        return MeasuredBatteryConsumption(
            hasEnoughSamples2h = true,
            collectedHours = collectedHours,
            collectingStatusText = "Amostras verificadas (${String.format(Locale.forLanguageTag("pt-PT"), "%.1f", collectedHours)} h)",
            screenOnDrainPercentPerHour = screenOnRate,
            screenOffIdleDrainPercentPerHour = screenOffRate,
            overallDrainPercentPerHour6h = overall6hRate,
            estimatedRemainingMinutes6h = remainingMinutes,
            estimatedRemainingTextPt = remainingText
        )
    }
}
