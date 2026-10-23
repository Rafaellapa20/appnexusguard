package com.nexusguard.android.domain

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class AppImpactPeriod(val days: Int, val labelPt: String) {
    HOURS_24(1, "24 h"),
    DAYS_7(7, "7 dias"),
    DAYS_30(30, "30 dias")
}

enum class AppImpactFilterView(val labelPt: String) {
    BATTERY("Bateria"),
    NETWORK("Dados e Wi-Fi"),
    STORAGE("Armazenamento"),
    UNUSED("Não usadas"),
    BLOATWARE("Pré-instaladas")
}

enum class UnusedThresholdDays(val days: Int, val labelPt: String) {
    DAYS_30(30, "> 30 dias"),
    DAYS_60(60, "> 60 dias"),
    DAYS_90(90, "> 90 dias")
}

@Immutable
data class AppImpactItem(
    val packageName: String,
    val appName: String,
    val monogram: String,
    val uid: Int,
    val isSystemApp: Boolean,
    val canBeDisabledBloatware: Boolean,
    // 1. Bateria (Fonte: UsageStatsManager.queryUsageStats + queryEvents FOREGROUND_SERVICE_START/STOP)
    val foregroundMillis: Long = 0L,
    val backgroundServiceMillis: Long = 0L,
    val estimatedWakeupsCount: Int = 0,
    val batteryReasonTextPt: String = "Sem atividade relevante no período",
    // 2. Dados móveis e Wi-Fi (Fonte: NetworkStatsManager.querySummary TYPE_MOBILE + TYPE_WIFI por UID e STATE_FOREGROUND/DEFAULT)
    val mobileForegroundBytes: Long = 0L,
    val mobileBackgroundBytes: Long = 0L,
    val wifiForegroundBytes: Long = 0L,
    val wifiBackgroundBytes: Long = 0L,
    // 3. Armazenamento (Fonte: StorageStatsManager.queryStatsForPackage + tendência em Room AppStorageSnapshotEntity)
    val appCodeBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val totalStorageBytes: Long = 0L,
    val storageDeltaBytes: Long = 0L,
    val storageTrendTextPt: String = "Primeira medição",
    val isStorageEstimatedApk: Boolean = false,
    // 4. Não usadas (Fonte: UsageStatsManager.queryAndAggregateUsageStats lastTimeUsed)
    val lastTimeUsedMillis: Long? = null,
    val daysSinceLastUse: Int? = null,
    val installedFirstTimeMillis: Long = 0L,
    // Gráfico simples de 7 dias (minutos por dia nos últimos 7 dias)
    val dailyUsageMinutes7d: ImmutableList<Int> = persistentListOf()
) {
    val totalNetworkBytes: Long
        get() = mobileForegroundBytes + mobileBackgroundBytes + wifiForegroundBytes + wifiBackgroundBytes

    val totalForegroundNetworkBytes: Long
        get() = mobileForegroundBytes + wifiForegroundBytes

    val totalBackgroundNetworkBytes: Long
        get() = mobileBackgroundBytes + wifiBackgroundBytes

    val batteryImpactScoreMillis: Long
        get() = foregroundMillis + (backgroundServiceMillis * 2L) + (estimatedWakeupsCount * 30_000L)
}

@Immutable
data class BatchUninstallProgress(
    val isRunning: Boolean = false,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentPackageName: String = "",
    val progressLabelPt: String = ""
)

object AppImpactCalculator {

    /**
     * Formata o motivo de impacto na bateria em texto claro PT-PT:
     * Ex.: «2 h 14 min em fundo com serviço ativo · 12 ativações»
     */
    fun buildBatteryReasonTextPt(
        foregroundMillis: Long,
        backgroundServiceMillis: Long,
        wakeupsCount: Int
    ): String {
        val parts = mutableListOf<String>()
        if (backgroundServiceMillis >= 60_000L) {
            parts.add("${formatDurationPt(backgroundServiceMillis)} em fundo com serviço ativo")
        }
        if (foregroundMillis >= 60_000L) {
            parts.add("${formatDurationPt(foregroundMillis)} em ecrã")
        }
        if (wakeupsCount > 0) {
            parts.add("$wakeupsCount ativações")
        }
        return if (parts.isEmpty()) {
            "Sem atividade relevante no período"
        } else {
            parts.joinToString(" · ")
        }
    }

    fun formatDurationPt(millis: Long): String {
        val totalMin = (millis / 60_000L).coerceAtLeast(0L)
        val hours = totalMin / 60L
        val mins = totalMin % 60L
        return when {
            hours > 0L && mins > 0L -> "${hours} h ${mins} min"
            hours > 0L -> "${hours} h"
            else -> "${mins.coerceAtLeast(1L)} min"
        }
    }

    fun buildStorageTrendTextPt(deltaBytes: Long, hadPreviousSnapshot: Boolean): String {
        if (!hadPreviousSnapshot) return "Estável (1.ª leitura)"
        return when {
            deltaBytes > 1024L * 512L -> "+${NexusFormatters.formatBytes(deltaBytes)} face à última análise"
            deltaBytes < -1024L * 512L -> "−${NexusFormatters.formatBytes(-deltaBytes)} face à última análise"
            else -> "Estável face à última análise"
        }
    }

    /**
     * Filtra apps não usadas há mais de [thresholdDays] (30, 60 ou 90 dias).
     */
    fun filterUnusedApps(
        apps: List<AppImpactItem>,
        thresholdDays: Int
    ): List<AppImpactItem> {
        return apps.filter { app ->
            !app.isSystemApp && (app.daysSinceLastUse ?: 0) >= thresholdDays
        }.sortedByDescending { it.totalStorageBytes }
    }

    /**
     * Desinstalação em lote: recebe a lista de pacotes selecionados e um predicado `isStillInstalled(pkg)`
     * que consulta `getPackageInfo`. Devolve apenas os que foram realmente removidos e o tamanho libertado.
     */
    fun verifyBatchUninstallResult(
        requestedApps: List<AppImpactItem>,
        isStillInstalled: (String) -> Boolean
    ): Pair<List<AppImpactItem>, Long> {
        val actuallyRemoved = requestedApps.filter { app -> !isStillInstalled(app.packageName) }
        val totalFreedBytes = actuallyRemoved.sumOf { it.totalStorageBytes }
        return actuallyRemoved to totalFreedBytes
    }

    fun formatBatchStepProgressPt(oneBasedIndex: Int, total: Int): String {
        return "$oneBasedIndex de $total"
    }
}
