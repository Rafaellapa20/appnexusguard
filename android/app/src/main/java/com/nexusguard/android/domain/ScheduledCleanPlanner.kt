package com.nexusguard.android.domain

import androidx.compose.runtime.Immutable
import java.util.Calendar

enum class ScheduledCleanFrequency(val intervalDays: Int, val labelPt: String) {
    DAILY(1, "Diária"),
    WEEKLY(7, "Semanal"),
    MONTHLY(30, "Mensal");

    companion object {
        fun fromDays(days: Int): ScheduledCleanFrequency {
            return entries.find { it.intervalDays == days } ?: WEEKLY
        }
    }
}

@Immutable
data class ScheduledCleanAuthorization(
    val autoCleanEmptyFolders: Boolean = true,
    val autoPurgeExpiredNexusTrash: Boolean = true,
    val autoClearOwnCache: Boolean = true
)

@Immutable
data class ScheduledCleanPlanResult(
    val shouldDeleteEmptyFolders: Boolean,
    val emptyFoldersToCleanCount: Int,
    val shouldPurgeExpiredNexusTrash: Boolean,
    val expiredTrashFilesCount: Int,
    val expiredTrashBytes: Long,
    val shouldClearOwnCache: Boolean,
    val ownCacheBytes: Long,
    val totalAutoCleanedBytes: Long,
    val remainingBytesRequiringReview: Long
)

@Immutable
data class SmartAlertEvaluationInput(
    val storageAlertEnabled: Boolean,
    val freeStorageBytes: Long,
    val totalStorageBytes: Long,
    val junkOver1GbAlertEnabled: Boolean,
    val totalCleanableJunkBytes: Long,
    val newAppBatteryAlertEnabled: Boolean,
    val newAppHighBatteryName: String?,
    val thermalAlertEnabled: Boolean,
    val thermalLevel: ThermalLevelPt,
    val charge80AlertEnabled: Boolean,
    val isCharging: Boolean,
    val batteryPercent: Int,
    val chargeTargetPercent: Int = 80
)

@Immutable
data class MonthlyReportSummary(
    val periodLabelPt: String = "Últimos 30 dias",
    val totalBytesFreed30d: Long = 0L,
    val actionsCount30d: Int = 0,
    val currentHealthScore: Int = 100,
    val healthEvolutionPoints: List<Int> = emptyList(),
    val topImpactApps: List<AppImpactItem> = emptyList(),
    val avgScreenOnDrainPerHour: Double? = null,
    val avgIdleDrainPerHour: Double? = null
)

object ScheduledCleanPlanner {

    const val ONE_GIGABYTE_BYTES: Long = 1024L * 1024L * 1024L
    const val MIN_SMART_ALERT_INTERVAL_MS: Long = 24L * 3600L * 1000L // Máximo 1 notificação por dia

    /**
     * Constrói o plano da limpeza agendada:
     * - limpa automaticamente apenas o que o utilizador autorizou expressamente e não exige confirmação do sistema:
     *   pastas vazias, ficheiros do Lixo Nexus expirados (> 30 dias) e cache própria da app;
     * - o restante fica intacto e é comunicado numa notificação com botão «Rever».
     */
    fun buildExecutionPlan(
        authorization: ScheduledCleanAuthorization,
        emptyFoldersCount: Int,
        expiredTrashFilesCount: Int,
        expiredTrashBytes: Long,
        ownCacheBytes: Long,
        remainingCleanableBytes: Long
    ): ScheduledCleanPlanResult {
        val cleanEmpty = authorization.autoCleanEmptyFolders && emptyFoldersCount > 0
        val cleanTrash = authorization.autoPurgeExpiredNexusTrash && expiredTrashFilesCount > 0
        val cleanOwnCache = authorization.autoClearOwnCache && ownCacheBytes > 0L

        val autoBytes = (if (cleanTrash) expiredTrashBytes else 0L) +
            (if (cleanOwnCache) ownCacheBytes else 0L)

        return ScheduledCleanPlanResult(
            shouldDeleteEmptyFolders = cleanEmpty,
            emptyFoldersToCleanCount = if (cleanEmpty) emptyFoldersCount else 0,
            shouldPurgeExpiredNexusTrash = cleanTrash,
            expiredTrashFilesCount = if (cleanTrash) expiredTrashFilesCount else 0,
            expiredTrashBytes = if (cleanTrash) expiredTrashBytes else 0L,
            shouldClearOwnCache = cleanOwnCache,
            ownCacheBytes = if (cleanOwnCache) ownCacheBytes else 0L,
            totalAutoCleanedBytes = autoBytes,
            remainingBytesRequiringReview = remainingCleanableBytes.coerceAtLeast(0L)
        )
    }

    /**
     * Calcula o atraso inicial em milissegundos até à próxima ocorrência da hora escolhida (0..23).
     */
    fun computeInitialDelayMillis(nowEpochMillis: Long, targetHourOfDay: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowEpochMillis
            set(Calendar.HOUR_OF_DAY, targetHourOfDay.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= nowEpochMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return (cal.timeInMillis - nowEpochMillis).coerceAtLeast(60_000L)
    }

    /**
     * Garante no máximo 1 notificação agrupada de avisos inteligentes por dia (24 h).
     */
    fun canSendDailyGroupedAlert(nowEpochMillis: Long, lastAlertSentEpochMillis: Long): Boolean {
        if (lastAlertSentEpochMillis <= 0L) return true
        return (nowEpochMillis - lastAlertSentEpochMillis) >= MIN_SMART_ALERT_INTERVAL_MS
    }

    /**
     * Avalia os 5 avisos inteligentes configuráveis e devolve a lista de mensagens ativas para agrupar.
     */
    fun evaluateSmartAlerts(input: SmartAlertEvaluationInput): List<String> {
        val alerts = mutableListOf<String>()

        if (input.storageAlertEnabled && input.totalStorageBytes > 0L) {
            val freeRatio = input.freeStorageBytes.toDouble() / input.totalStorageBytes.toDouble()
            if (freeRatio < 0.10) {
                val pct = (freeRatio * 100.0).toInt()
                alerts.add("Armazenamento livre abaixo de 10 % ($pct % disponível)")
            }
        }

        if (input.junkOver1GbAlertEnabled && input.totalCleanableJunkBytes >= ONE_GIGABYTE_BYTES) {
            alerts.add("Mais de 1 GB pronto a limpar (${NexusFormatters.formatBytes(input.totalCleanableJunkBytes)})")
        }

        if (input.newAppBatteryAlertEnabled && !input.newAppHighBatteryName.isNullOrBlank()) {
            alerts.add("App recente com consumo elevado nas últimas 24 h: ${input.newAppHighBatteryName}")
        }

        if (input.thermalAlertEnabled && input.thermalLevel.severityRank >= ThermalLevelPt.GRAVE.severityRank) {
            alerts.add("Telemóvel quente (estado térmico ${input.thermalLevel.labelPt})")
        }

        if (input.charge80AlertEnabled && input.isCharging && input.batteryPercent >= input.chargeTargetPercent) {
            alerts.add("Carga atingiu ${input.batteryPercent} % (meta ${input.chargeTargetPercent} %)")
        }

        return alerts
    }
}
