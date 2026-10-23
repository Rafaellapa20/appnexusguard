package com.nexusguard.android.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.compose.runtime.Immutable
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.data.preferences.UserPreferencesRepository
import com.nexusguard.android.data.preferences.UserPreferencesState
import com.nexusguard.android.domain.AppImpactPeriod
import com.nexusguard.android.domain.ForceStopBackendType
import com.nexusguard.android.domain.HibernationAppProgressItem
import com.nexusguard.android.domain.HibernationAppStepStatus
import com.nexusguard.android.domain.HibernationCalculator
import com.nexusguard.android.domain.HibernationCandidateApp
import com.nexusguard.android.domain.HibernationExecutionSummary
import com.nexusguard.android.domain.ProtectedAppsContext
import com.nexusguard.android.flavor.HibernationFlavorDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class HibernationStateSnapshot(
    val isFullFlavor: Boolean = HibernationFlavorDelegate.IS_HIBERNATION_SUPPORTED,
    val activeBackend: ForceStopBackendType = ForceStopBackendType.BASIC,
    val shizukuInstalled: Boolean = false,
    val shizukuRunningAndPermitted: Boolean = false,
    val shizukuWaitingPermission: Boolean = false,
    val accessibilityServiceEnabled: Boolean = false,
    val recommendedCandidates: ImmutableList<HibernationCandidateApp> = persistentListOf(),
    val protectedApps: ImmutableList<HibernationCandidateApp> = persistentListOf(),
    val idleDrainComparisonTextPt: String? = null
)

@Singleton
class HibernationEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appImpactRepository: AppImpactRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val dao: NexusGuardDao
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var screenOffJob: Job? = null
    private var screenReceiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOffJob?.cancel()
                    screenOffJob = engineScope.launch {
                        // Aguarda 1 minuto depois de o ecrã desligar antes de hibernar automaticamente
                        delay(60_000L)
                        val prefs = preferencesRepository.preferencesFlow.first()
                        if (prefs.autoHibernationEnabled && prefs.autoHibernateOnScreenOff && HibernationFlavorDelegate.IS_HIBERNATION_SUPPORTED) {
                            runAutomaticHibernationNow(modeLabelPt = "Automático · Ecrã desligado")
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOffJob?.cancel()
                    screenOffJob = null
                }
            }
        }
    }

    fun ensureScreenOffTriggerRegistered() {
        if (screenReceiverRegistered || !HibernationFlavorDelegate.IS_HIBERNATION_SUPPORTED) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            context.registerReceiver(screenStateReceiver, filter)
            screenReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    fun buildProtectedContext(prefs: UserPreferencesState): ProtectedAppsContext {
        val pm = context.packageManager

        val activeImePkg = runCatching {
            val defaultIme = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
            defaultIme?.substringBefore('/')?.takeIf { it.isNotBlank() }
        }.getOrNull()

        val launcherPkgs = runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())

        val dialerPkg = runCatching {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            tm?.defaultDialerPackage
        }.getOrNull()

        val smsPkg = runCatching {
            Telephony.Sms.getDefaultSmsPackage(context)
        }.getOrNull()

        val neverCloseSet = prefs.neverClosePackagesCsv
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        return ProtectedAppsContext(
            ownPackageNames = setOf(context.packageName, "com.nexusguard.android", "com.nexusguard.android.play"),
            activeImePackage = activeImePkg,
            launcherPackages = launcherPkgs,
            defaultDialerPackage = dialerPkg,
            defaultSmsPackage = smsPkg,
            activeMediaOrNavPackages = emptySet(),
            userNeverClosePackages = neverCloseSet
        )
    }

    suspend fun loadHibernationState(prefs: UserPreferencesState): HibernationStateSnapshot = withContext(Dispatchers.IO) {
        ensureScreenOffTriggerRegistered()

        val isFull = HibernationFlavorDelegate.IS_HIBERNATION_SUPPORTED
        val backend = HibernationFlavorDelegate.detectActiveBackend(context)
        val shizukuInstalled = HibernationFlavorDelegate.isShizukuInstalled(context)
        val shizukuReady = HibernationFlavorDelegate.isShizukuRunningAndAuthorized()
        val shizukuWaiting = HibernationFlavorDelegate.isShizukuRunningWithoutPermission()
        val accessibilityReady = HibernationFlavorDelegate.isAccessibilityServiceEnabled(context)

        val protectedContext = buildProtectedContext(prefs)
        val alwaysHibernateSet = prefs.alwaysHibernatePackagesCsv
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        // Impacto nas últimas 24 h para identificar top 10 de bateria e serviços em 1.º plano
        val impacts24h = appImpactRepository.loadAppsImpact(
            period = AppImpactPeriod.HOURS_24,
            saveStorageSnapshotsToRoom = false
        )
        val top10BatteryPkgs = impacts24h
            .sortedByDescending { it.batteryImpactScoreMillis }
            .take(10)
            .map { it.packageName }
            .toSet()

        // Eventos nas últimas 3 horas via UsageStatsManager.queryEvents
        val recent3hPackages = HashSet<String>()
        val unstoppedFgsPackages = HashSet<String>()
        if (appImpactRepository.hasUsageStatsPermission()) {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm != null) {
                val now = System.currentTimeMillis()
                val threeHoursAgo = now - (3L * 3600_000L)
                val activeFgsStart = HashMap<String, Long>()
                runCatching {
                    val events = usm.queryEvents(threeHoursAgo, now)
                    val event = UsageEvents.Event()
                    while (events != null && events.hasNextEvent()) {
                        events.getNextEvent(event)
                        val pkg = event.packageName ?: continue
                        recent3hPackages.add(pkg)
                        when (event.eventType) {
                            UsageEvents.Event.FOREGROUND_SERVICE_START -> activeFgsStart[pkg] = event.timeStamp
                            UsageEvents.Event.FOREGROUND_SERVICE_STOP -> activeFgsStart.remove(pkg)
                        }
                    }
                }
                unstoppedFgsPackages.addAll(activeFgsStart.keys)
            }
        }

        val pm = context.packageManager
        val installedApps = runCatching {
            pm.getInstalledApplications(0)
        }.getOrDefault(emptyList())

        val recommended = mutableListOf<HibernationCandidateApp>()
        val protectedList = mutableListOf<HibernationCandidateApp>()

        for (appInfo in installedApps) {
            val pkg = appInfo.packageName ?: continue
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                .getOrDefault(pkg.substringAfterLast('.'))
                .ifBlank { pkg }
            val monogram = label.trim().take(2).uppercase(Locale.ROOT).ifEmpty { "AP" }

            val protectionReason = HibernationCalculator.getProtectionReasonPt(pkg, protectedContext)
            val hasUnstoppedFgs = pkg in unstoppedFgsPackages
            val hasRecent3h = pkg in recent3hPackages
            val isTop10 = pkg in top10BatteryPkgs
            val isAlways = pkg in alwaysHibernateSet

            val candidate = HibernationCandidateApp(
                packageName = pkg,
                appName = label,
                monogram = monogram,
                hasUnstoppedForegroundService = hasUnstoppedFgs,
                hasActivityInLast3Hours = hasRecent3h,
                isTop10Battery24h = isTop10,
                isInAlwaysHibernateList = isAlways,
                isProtected = protectionReason != null,
                protectionReasonPt = protectionReason
            )

            if (protectionReason != null) {
                protectedList.add(candidate)
            } else if (!isSystem) {
                // Filtra candidatas conforme as preferências (só top 10 no modo automático ou qualquer candidata ativa)
                val shouldInclude = isAlways || hasUnstoppedFgs || hasRecent3h || isTop10
                if (shouldInclude) {
                    recommended.add(candidate)
                }
            }
        }

        // Se UsageStats não estiver concedida ou nenhuma app tiver atividade nas últimas 3 h, mostra apps de utilizador elegíveis
        if (recommended.isEmpty()) {
            for (appInfo in installedApps) {
                val pkg = appInfo.packageName ?: continue
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                if (isSystem) continue
                val protectionReason = HibernationCalculator.getProtectionReasonPt(pkg, protectedContext)
                if (protectionReason != null) continue
                val label = runCatching { pm.getApplicationLabel(appInfo).toString() }
                    .getOrDefault(pkg.substringAfterLast('.'))
                    .ifBlank { pkg }
                val monogram = label.trim().take(2).uppercase(Locale.ROOT).ifEmpty { "AP" }
                recommended.add(
                    HibernationCandidateApp(
                        packageName = pkg,
                        appName = label,
                        monogram = monogram,
                        isTop10Battery24h = pkg in top10BatteryPkgs,
                        isInAlwaysHibernateList = pkg in alwaysHibernateSet
                    )
                )
            }
        }

        val sortedRecommended = recommended.sortedWith(
            compareByDescending<HibernationCandidateApp> { it.isInAlwaysHibernateList }
                .thenByDescending { it.hasUnstoppedForegroundService }
                .thenByDescending { it.isTop10Battery24h }
                .thenByDescending { it.hasActivityInLast3Hours }
                .thenBy { it.appName.lowercase(Locale.ROOT) }
        )

        val sortedProtected = protectedList.sortedWith(
            compareByDescending<HibernationCandidateApp> { it.protectionReasonPt == "Marcada como «Nunca fechar»" }
                .thenBy { it.appName.lowercase(Locale.ROOT) }
        )

        val idleComparisonText = if (prefs.autoHibernationActivatedAtMillis > 0L) {
            val sevenDaysAgo = System.currentTimeMillis() - (7L * 24L * 3600_000L)
            val samples = dao.getBatterySamplesSince(sevenDaysAgo)
            HibernationCalculator.computeIdleDrainComparisonTextPt(
                samples = samples,
                activatedAtEpochMillis = prefs.autoHibernationActivatedAtMillis
            )
        } else {
            null
        }

        HibernationStateSnapshot(
            isFullFlavor = isFull,
            activeBackend = backend,
            shizukuInstalled = shizukuInstalled,
            shizukuRunningAndPermitted = shizukuReady,
            shizukuWaitingPermission = shizukuWaiting,
            accessibilityServiceEnabled = accessibilityReady,
            recommendedCandidates = sortedRecommended.toImmutableList(),
            protectedApps = sortedProtected.toImmutableList(),
            idleDrainComparisonTextPt = idleComparisonText
        )
    }

    suspend fun executeHibernationBatch(
        modeLabelPt: String,
        candidates: List<HibernationCandidateApp>,
        prefs: UserPreferencesState,
        onProgress: (List<HibernationAppProgressItem>) -> Unit = {}
    ): HibernationExecutionSummary = withContext(Dispatchers.IO) {
        val protectedContext = buildProtectedContext(prefs)
        val eligible = HibernationCalculator.selectEligibleForHibernation(candidates, protectedContext)
        val backend = HibernationFlavorDelegate.detectActiveBackend(context)

        if (eligible.isEmpty() || !HibernationFlavorDelegate.IS_HIBERNATION_SUPPORTED) {
            val emptySummary = HibernationExecutionSummary(
                modeLabelPt = modeLabelPt,
                backendType = backend,
                stoppedCount = 0,
                alreadyStoppedCount = 0,
                failedCount = 0,
                honestSummaryCopyPt = HibernationCalculator.buildExactSummaryCopyPt(backend, 0)
            )
            return@withContext emptySummary
        }

        val progressList = eligible.map { app ->
            HibernationAppProgressItem(
                packageName = app.packageName,
                appName = app.appName,
                monogram = app.monogram,
                status = HibernationAppStepStatus.PENDING
            )
        }.toMutableList()

        onProgress(progressList.toList())

        val packageOrder = eligible.map { it.packageName }
        val outcomes = HibernationFlavorDelegate.executeHibernation(
            context = context,
            packages = packageOrder,
            backend = backend,
            onProgress = { idx, _, pkg, status ->
                val itemIdx = progressList.indexOfFirst { it.packageName == pkg }
                if (itemIdx >= 0) {
                    progressList[itemIdx] = progressList[itemIdx].copy(status = status)
                }
                if (idx < progressList.size && progressList[idx].status == HibernationAppStepStatus.PENDING) {
                    progressList[idx] = progressList[idx].copy(status = HibernationAppStepStatus.RUNNING)
                }
                onProgress(progressList.toList())
            }
        )

        val stoppedCount = outcomes.values.count { it == HibernationAppStepStatus.STOPPED }
        val alreadyStoppedCount = outcomes.values.count { it == HibernationAppStepStatus.ALREADY_STOPPED }
        val failedCount = outcomes.values.count { it == HibernationAppStepStatus.FAILED }

        val summary = HibernationExecutionSummary(
            modeLabelPt = modeLabelPt,
            backendType = backend,
            stoppedCount = stoppedCount,
            alreadyStoppedCount = alreadyStoppedCount,
            failedCount = failedCount,
            honestSummaryCopyPt = HibernationCalculator.buildExactSummaryCopyPt(backend, stoppedCount)
        )

        dao.insertHistory(HibernationCalculator.buildHistoryEntry(summary))
        summary
    }

    suspend fun runAutomaticHibernationNow(modeLabelPt: String = "Automático"): HibernationExecutionSummary? {
        if (!HibernationFlavorDelegate.IS_HIBERNATION_SUPPORTED) return null
        val prefs = preferencesRepository.preferencesFlow.first()
        val state = loadHibernationState(prefs)
        val filtered = if (prefs.autoHibernateTop10BatteryOnly) {
            state.recommendedCandidates.filter { it.isTop10Battery24h || it.isInAlwaysHibernateList || it.hasUnstoppedForegroundService }
        } else {
            state.recommendedCandidates
        }
        if (filtered.isEmpty()) return null
        return executeHibernationBatch(
            modeLabelPt = modeLabelPt,
            candidates = filtered,
            prefs = prefs
        )
    }
}
