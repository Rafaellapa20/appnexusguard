package com.nexusguard.android.domain

import androidx.compose.runtime.Immutable
import com.nexusguard.android.data.local.BatterySampleEntity
import com.nexusguard.android.data.local.HistoryEntity
import java.util.Locale
import kotlin.math.abs

enum class ForceStopBackendType(val badgeLabelPt: String, val shortNamePt: String) {
    SHIZUKU("Shizuku ativo · Rápido", "Shizuku"),
    ACCESSIBILITY("Acessibilidade · Visível", "Acessibilidade"),
    BASIC("Básico · Parcial", "Básico"),
    NONE_PLAY("Atalhos do sistema · Play", "Play")
}

enum class HibernationAppStepStatus(val symbolPt: String) {
    PENDING("…"),
    RUNNING("↻"),
    STOPPED("✓"),
    ALREADY_STOPPED("✓"),
    FAILED("✕")
}

@Immutable
data class HibernationCandidateApp(
    val packageName: String,
    val appName: String,
    val monogram: String,
    val hasUnstoppedForegroundService: Boolean = false,
    val hasActivityInLast3Hours: Boolean = false,
    val isTop10Battery24h: Boolean = false,
    val isInAlwaysHibernateList: Boolean = false,
    val isProtected: Boolean = false,
    val protectionReasonPt: String? = null
)

@Immutable
data class HibernationAppProgressItem(
    val packageName: String,
    val appName: String,
    val monogram: String,
    val status: HibernationAppStepStatus
)

@Immutable
data class HibernationExecutionSummary(
    val modeLabelPt: String,
    val backendType: ForceStopBackendType,
    val stoppedCount: Int,
    val alreadyStoppedCount: Int,
    val failedCount: Int,
    val honestSummaryCopyPt: String,
    val timestampEpochMillis: Long = System.currentTimeMillis()
)

@Immutable
data class AccessibilityNodeSnapshot(
    val index: Int,
    val viewIdResourceName: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val isClickable: Boolean = true,
    val isEnabled: Boolean = true
)

sealed class ForceStopNodeLookupResult {
    data class ClickableForceStop(val nodeIndex: Int) : ForceStopNodeLookupResult()
    data class AlreadyStoppedDisabled(val nodeIndex: Int) : ForceStopNodeLookupResult()
    data object NotFound : ForceStopNodeLookupResult()
}

@Immutable
data class ProtectedAppsContext(
    val ownPackageNames: Set<String> = setOf("com.nexusguard.android", "com.nexusguard.android.play"),
    val activeImePackage: String? = null,
    val launcherPackages: Set<String> = emptySet(),
    val defaultDialerPackage: String? = null,
    val defaultSmsPackage: String? = null,
    val activeMediaOrNavPackages: Set<String> = emptySet(),
    val userNeverClosePackages: Set<String> = emptySet()
)

object HibernationCalculator {

    private val CORE_SYSTEM_PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "moe.shizuku.privileged.api"
    )

    private val KNOWN_CLOCK_ALARM_PACKAGES = setOf(
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.sec.android.app.clockpackage",
        "com.oneplus.deskclock",
        "com.coloros.alarmclock"
    )

    private val KNOWN_PASSWORD_MANAGERS = setOf(
        "com.x8bit.bitwarden",
        "com.bitwarden.mobile",
        "com.agilebits.onepassword",
        "com.dashlane",
        "proton.android.pass",
        "keepass2android.keepass2android"
    )

    private val FORCE_STOP_RESOURCE_IDS = setOf(
        "com.android.settings:id/right_button",
        "com.android.settings:id/force_stop_button",
        "com.android.settings:id/force_stop",
        "com.android.settings:id/button2_positive",
        "com.miui.securitycenter:id/btn_force_stop"
    )

    private val CONFIRM_DIALOG_RESOURCE_IDS = setOf(
        "android:id/button1",
        "com.android.settings:id/button1",
        "miui:id/button1"
    )

    // Textos multilíngues (PT, EN, ES, FR, DE) para o botão «Forçar paragem»
    private val FORCE_STOP_LABELS_LOWER = setOf(
        "forçar paragem",
        "forçar interrupção",
        "force stop",
        "forzar detención",
        "forcer l'arrêt",
        "forcer l’arrêt",
        "beenden erzwingen",
        "stopp erzwingen"
    )

    // Textos multilíngues (PT, EN, ES, FR, DE) para o botão de confirmação no diálogo
    private val CONFIRM_LABELS_LOWER = setOf(
        "ok",
        "forçar paragem",
        "forçar interrupção",
        "force stop",
        "confirmar",
        "aceitar",
        "forzar detención",
        "forcer l'arrêt",
        "forcer l’arrêt",
        "beenden erzwingen"
    )

    /**
     * Verifica se um pacote pertence à lista protegida e devolve o motivo em PT-PT (ou `null` se não for protegido).
     */
    fun getProtectionReasonPt(
        packageName: String,
        context: ProtectedAppsContext
    ): String? {
        val lower = packageName.lowercase(Locale.ROOT)
        return when {
            packageName in context.ownPackageNames || lower.startsWith("com.nexusguard.android") ->
                "Própria aplicação Nexus Guard"
            packageName == context.activeImePackage ->
                "Teclado ativo"
            packageName in context.launcherPackages ->
                "Launcher do sistema"
            packageName in CORE_SYSTEM_PROTECTED_PACKAGES ->
                "Serviço essencial do sistema"
            packageName == context.defaultDialerPackage ->
                "App de telefone predefinida"
            packageName == context.defaultSmsPackage ->
                "App de SMS predefinida"
            packageName in KNOWN_CLOCK_ALARM_PACKAGES || lower.contains("deskclock") || lower.contains("alarmclock") ->
                "Relógio e despertador"
            packageName in KNOWN_PASSWORD_MANAGERS || lower.contains("keepass") || lower.contains("bitwarden") ->
                "Gestor de palavras-passe"
            packageName in context.activeMediaOrNavPackages ->
                "Música ou navegação ativa"
            packageName in context.userNeverClosePackages ->
                "Marcada como «Nunca fechar»"
            else -> null
        }
    }

    /**
     * Filtra a lista de candidatas para execução, removendo todas as apps protegidas.
     */
    fun selectEligibleForHibernation(
        candidates: List<HibernationCandidateApp>,
        protectedContext: ProtectedAppsContext
    ): List<HibernationCandidateApp> {
        return candidates.filter { app ->
            getProtectionReasonPt(app.packageName, protectedContext) == null
        }
    }

    /**
     * Escolhe automaticamente o melhor backend disponível.
     * Nota: Com o ecrã desligado (`isScreenInteractive == false`), o Serviço de Acessibilidade
     * não pode interagir com as Definições, pelo que usa apenas Shizuku ou Básico.
     */
    fun selectBestBackend(
        shizukuAvailableAndPermitted: Boolean,
        accessibilityServiceEnabled: Boolean,
        isScreenInteractive: Boolean = true,
        preferredBackendOverride: ForceStopBackendType? = null
    ): ForceStopBackendType {
        if (preferredBackendOverride != null) {
            when (preferredBackendOverride) {
                ForceStopBackendType.SHIZUKU -> if (shizukuAvailableAndPermitted) return ForceStopBackendType.SHIZUKU
                ForceStopBackendType.ACCESSIBILITY -> if (accessibilityServiceEnabled && isScreenInteractive) return ForceStopBackendType.ACCESSIBILITY
                ForceStopBackendType.BASIC -> return ForceStopBackendType.BASIC
                ForceStopBackendType.NONE_PLAY -> return ForceStopBackendType.NONE_PLAY
            }
        }
        return when {
            shizukuAvailableAndPermitted -> ForceStopBackendType.SHIZUKU
            accessibilityServiceEnabled && isScreenInteractive -> ForceStopBackendType.ACCESSIBILITY
            else -> ForceStopBackendType.BASIC
        }
    }

    /**
     * Procura o botão «Forçar paragem» numa lista de nós de acessibilidade por ID de recurso ou texto multilíngue (PT/EN/ES/FR/DE).
     * Se o botão for encontrado mas estiver desativado (`!isEnabled`), devolve `AlreadyStoppedDisabled`.
     */
    fun findForceStopNode(nodes: List<AccessibilityNodeSnapshot>): ForceStopNodeLookupResult {
        val matched = nodes.firstOrNull { node ->
            val idMatch = node.viewIdResourceName != null && node.viewIdResourceName in FORCE_STOP_RESOURCE_IDS
            val textNorm = node.text?.trim()?.lowercase(Locale.ROOT)
            val descNorm = node.contentDescription?.trim()?.lowercase(Locale.ROOT)
            val labelMatch = (textNorm != null && textNorm in FORCE_STOP_LABELS_LOWER) ||
                (descNorm != null && descNorm in FORCE_STOP_LABELS_LOWER)
            if (idMatch) {
                // Nos layouts AOSP onde right_button é partilhado, garante que o texto/descrição corresponde se presente
                textNorm == null || textNorm in FORCE_STOP_LABELS_LOWER || descNorm in FORCE_STOP_LABELS_LOWER
            } else {
                labelMatch
            }
        } ?: return ForceStopNodeLookupResult.NotFound

        return if (matched.isEnabled) {
            ForceStopNodeLookupResult.ClickableForceStop(matched.index)
        } else {
            ForceStopNodeLookupResult.AlreadyStoppedDisabled(matched.index)
        }
    }

    /**
     * Procura o botão de confirmação no diálogo de alerta de «Forçar paragem».
     */
    fun findConfirmDialogNodeIndex(nodes: List<AccessibilityNodeSnapshot>): Int? {
        return nodes.firstOrNull { node ->
            if (!node.isEnabled) return@firstOrNull false
            val idMatch = node.viewIdResourceName != null && node.viewIdResourceName in CONFIRM_DIALOG_RESOURCE_IDS
            val textNorm = node.text?.trim()?.lowercase(Locale.ROOT)
            val descNorm = node.contentDescription?.trim()?.lowercase(Locale.ROOT)
            val labelMatch = (textNorm != null && textNorm in CONFIRM_LABELS_LOWER) ||
                (descNorm != null && descNorm in CONFIRM_LABELS_LOWER)
            idMatch || labelMatch
        }?.index
    }

    /**
     * Copy exata exigida pela especificação:
     * - Shizuku/Acessibilidade: «12 apps paradas. Voltam a abrir quando as usares ou quando receberem notificações.»
     * - Básico: «Pedido ao Android para libertar processos de 12 apps. O sistema pode reiniciá-los.»
     */
    fun buildExactSummaryCopyPt(backend: ForceStopBackendType, stoppedCount: Int): String {
        return when (backend) {
            ForceStopBackendType.SHIZUKU, ForceStopBackendType.ACCESSIBILITY ->
                "$stoppedCount apps paradas. Voltam a abrir quando as usares ou quando receberem notificações."
            ForceStopBackendType.BASIC, ForceStopBackendType.NONE_PLAY ->
                "Pedido ao Android para libertar processos de $stoppedCount apps. O sistema pode reiniciá-los."
        }
    }

    /**
     * Constrói o registo no Histórico (Room) com o modo, o backend, as apps paradas, as já paradas e as falhadas.
     */
    fun buildHistoryEntry(summary: HibernationExecutionSummary): HistoryEntity {
        return HistoryEntity(
            actionType = "HIBERNATION",
            title = "Hibernação (${summary.modeLabelPt} · ${summary.backendType.shortNamePt})",
            details = "${summary.honestSummaryCopyPt} (Paradas: ${summary.stoppedCount} · Já paradas: ${summary.alreadyStoppedCount} · Falhadas: ${summary.failedCount})",
            timestampEpochMillis = summary.timestampEpochMillis,
            bytesFreed = 0L,
            canRestore = false
        )
    }

    /**
     * Compara o consumo em repouso (%/h) nos 3 dias antes e nos 3 dias depois de ativar a hibernação automática.
     * Se não houver dados suficientes nos dois períodos, devolve `null`.
     */
    fun computeIdleDrainComparisonTextPt(
        samples: List<BatterySampleEntity>,
        activatedAtEpochMillis: Long
    ): String? {
        if (activatedAtEpochMillis <= 0L) return null
        val threeDaysMs = 3L * 24L * 3600L * 1000L
        val beforeSamples = samples.filter {
            it.timestampEpochMillis in (activatedAtEpochMillis - threeDaysMs)..activatedAtEpochMillis
        }
        val afterSamples = samples.filter {
            it.timestampEpochMillis in activatedAtEpochMillis..(activatedAtEpochMillis + threeDaysMs)
        }
        if (beforeSamples.size < 2 || afterSamples.size < 2) return null

        val beforeConsumption = BatteryRateCalculator.calculateConsumption(
            samples = beforeSamples,
            currentLevelPercent = beforeSamples.last().levelPercent,
            nowMillis = activatedAtEpochMillis
        )
        val afterConsumption = BatteryRateCalculator.calculateConsumption(
            samples = afterSamples,
            currentLevelPercent = afterSamples.last().levelPercent,
            nowMillis = afterSamples.last().timestampEpochMillis
        )

        val beforeIdle = beforeConsumption.screenOffIdleDrainPercentPerHour ?: return null
        val afterIdle = afterConsumption.screenOffIdleDrainPercentPerHour ?: return null
        val delta = afterIdle - beforeIdle
        val sign = if (delta < 0.0) "−" else "+"
        val formattedDelta = String.format(Locale("pt", "PT"), "%.1f", abs(delta))
        return "$sign$formattedDelta %/h em repouso desde que ativaste"
    }
}
