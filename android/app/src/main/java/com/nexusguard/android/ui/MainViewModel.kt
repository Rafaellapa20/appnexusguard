package com.nexusguard.android.ui

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.local.BatterySampleEntity
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.data.preferences.UserPreferencesRepository
import com.nexusguard.android.data.preferences.UserPreferencesState
import com.nexusguard.android.data.repository.AppUpdateUiState
import com.nexusguard.android.data.repository.AppUpdaterRepository
import com.nexusguard.android.data.repository.BatteryProfileManager
import com.nexusguard.android.data.repository.CleanScanReport
import com.nexusguard.android.data.repository.CleanerRepository
import com.nexusguard.android.data.repository.RealSystemSnapshot
import com.nexusguard.android.data.repository.SystemSensorsRepository
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ScannedFileCandidate
import com.nexusguard.android.domain.ThermalLevelPt
import com.nexusguard.android.service.BoostSessionState
import com.nexusguard.android.service.ChargeMonitorForegroundService
import com.nexusguard.android.service.GameBoostManager
import com.nexusguard.android.util.PdfReportExporter
import com.nexusguard.android.worker.BatterySampleWorker
import com.nexusguard.android.worker.SystemMonitorWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val sensorsRepository: SystemSensorsRepository,
    private val cleanerRepository: CleanerRepository,
    private val batteryProfileManager: BatteryProfileManager,
    private val boostManager: GameBoostManager,
    private val appUpdaterRepository: AppUpdaterRepository,
    private val dao: NexusGuardDao
) : ViewModel() {

    companion object {
        private const val CLEAN_CACHE_MAX_AGE_MS = 10 * 60_000L // 10 minutos
    }

    val updateState: StateFlow<AppUpdateUiState> = appUpdaterRepository.updateState

    val preferencesState: StateFlow<UserPreferencesState> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferencesState()
        )

    val liveSnapshot: StateFlow<RealSystemSnapshot> = sensorsRepository.liveSnapshot

    val recentHistory: StateFlow<List<HistoryEntity>> = dao.observeRecentHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val allHistory: StateFlow<List<HistoryEntity>> = dao.observeAllHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val batterySamples24h: StateFlow<List<BatterySampleEntity>> = sensorsRepository.observeBatterySamples24h()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val boostSession: StateFlow<BoostSessionState> = boostManager.sessionState

    private val _hasDndPermission = MutableStateFlow(boostManager.hasDndPermission())
    val hasDndPermission: StateFlow<Boolean> = _hasDndPermission.asStateFlow()

    private val _hasWriteSettingsPermission = MutableStateFlow(batteryProfileManager.hasWriteSettingsPermission())
    val hasWriteSettingsPermission: StateFlow<Boolean> = _hasWriteSettingsPermission.asStateFlow()

    val chargingSessions = dao.observeChargingSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _cleanReport = MutableStateFlow(
        CleanScanReport(
            hasMediaPermission = cleanerRepository.hasMediaReadPermission(),
            hasManageStoragePermission = cleanerRepository.hasManageExternalStoragePermission(),
            categories = persistentListOf(),
            totalCleanableBytes = 0L,
            scannedFilesCount = 0
        )
    )
    val cleanReport: StateFlow<CleanScanReport> = _cleanReport.asStateFlow()

    private val _isScanningClean = MutableStateFlow(false)
    val isScanningClean: StateFlow<Boolean> = _isScanningClean.asStateFlow()

    private val _cleanScanStepText = MutableStateFlow("")
    val cleanScanStepText: StateFlow<String> = _cleanScanStepText.asStateFlow()

    private val _analysisState = MutableStateFlow(OneClickAnalysisState())
    val analysisState: StateFlow<OneClickAnalysisState> = _analysisState.asStateFlow()

    // Estados derivados por ecrã com distinctUntilChanged()
    val homeUiState: StateFlow<HomeUiState> = ScreenUiStateMappers.observeHomeUiState(
        snapshotFlow = liveSnapshot,
        recentHistoryFlow = recentHistory,
        analysisStateFlow = analysisState
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )

    val cleanUiState: StateFlow<CleanUiState> = ScreenUiStateMappers.observeCleanUiState(
        snapshotFlow = liveSnapshot,
        cleanReportFlow = cleanReport,
        isScanningFlow = isScanningClean,
        scanStepFlow = cleanScanStepText,
        scanProgressFlow = cleanerRepository.scanProgress
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CleanUiState(isLoading = true)
    )

    val gamesUiState: StateFlow<GamesUiState> = ScreenUiStateMappers.observeGamesUiState(
        snapshotFlow = liveSnapshot,
        boostSessionFlow = boostSession,
        hasDndFlow = hasDndPermission
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GamesUiState(isLoading = true)
    )

    val batteryUiState: StateFlow<BatteryUiState> = ScreenUiStateMappers.observeBatteryUiState(
        snapshotFlow = liveSnapshot,
        samplesFlow = batterySamples24h,
        prefsFlow = preferencesState,
        chargingSessionsFlow = chargingSessions,
        writeSettingsFlow = hasWriteSettingsPermission,
        hasOemNativeChargeLimit = batteryProfileManager.hasOemNativeChargeLimit()
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BatteryUiState(isLoading = true)
    )

    val systemUiState: StateFlow<SystemUiState> = ScreenUiStateMappers.observeSystemUiState(
        snapshotFlow = liveSnapshot
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SystemUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            SystemMonitorWorker.scheduleOrUpdateWeeklyMonitor(appContext, true)
            BatterySampleWorker.schedulePeriodicSampling(appContext)
            // Fase 2: Não corre refreshCleanScan() no init! Corre ao entrar em Limpar ou em "Analisar agora".
        }
        viewModelScope.launch {
            appUpdaterRepository.checkForUpdates(isStartupCheck = true)
        }
    }

    fun checkForUpdatesManual() {
        viewModelScope.launch {
            appUpdaterRepository.checkForUpdates(isStartupCheck = false)
        }
    }

    fun startAppUpdateDownload() {
        viewModelScope.launch {
            appUpdaterRepository.downloadAndInstallUpdate()
        }
    }

    fun installDownloadedUpdate() {
        appUpdaterRepository.installDownloadedApkIfReady()
    }

    fun downloadUpdateViaSystemManager() {
        appUpdaterRepository.downloadViaSystemDownloadManager()
    }

    fun onAppResumed() {
        _hasDndPermission.value = boostManager.hasDndPermission()
        _hasWriteSettingsPermission.value = batteryProfileManager.hasWriteSettingsPermission()
        appUpdaterRepository.onAppResumed()
        viewModelScope.launch {
            sensorsRepository.refreshSlowMetrics(force = false)
        }
    }

    fun openUpdateInBrowser() {
        appUpdaterRepository.openBrowserFallback()
    }

    fun dismissUpdateDialog() {
        viewModelScope.launch {
            appUpdaterRepository.dismissUpdateDialog()
        }
    }

    fun refreshAllRealData() {
        viewModelScope.launch {
            sensorsRepository.refreshSlowMetrics(force = true)
        }
    }

    /**
     * Chamado ao entrar no separador Limpar: reaproveita o último resultado se tiver menos de 10 min
     * e o armazenamento livre não tiver mudado mais de 1 %.
     */
    fun onEnterCleanTab() {
        refreshCleanScan(force = false)
    }

    fun refreshCleanScan(force: Boolean = true) {
        if (_isScanningClean.value) return
        viewModelScope.launch {
            val currentReport = _cleanReport.value
            val storage = liveSnapshot.value.storage
            val now = System.currentTimeMillis()
            val ageMs = now - currentReport.timestampMillis
            val totalStorage = storage.totalBytes.coerceAtLeast(1L)
            val freeDeltaRatio = abs(storage.freeBytes - currentReport.freeStorageAtScanBytes).toDouble() / totalStorage.toDouble()
            val permissionsUnchanged = currentReport.hasMediaPermission == cleanerRepository.hasMediaReadPermission() &&
                currentReport.hasManageStoragePermission == cleanerRepository.hasManageExternalStoragePermission()

            if (!force &&
                currentReport.timestampMillis > 0L &&
                ageMs < CLEAN_CACHE_MAX_AGE_MS &&
                freeDeltaRatio <= 0.01 &&
                permissionsUnchanged
            ) {
                return@launch
            }

            _isScanningClean.value = true
            _cleanScanStepText.value = "A pesquisar ficheiros no MediaStore..."
            try {
                val scanned = cleanerRepository.scanRealDeviceStorage(
                    onStepProgress = { step -> _cleanScanStepText.value = step }
                )
                _cleanReport.value = scanned.copy(
                    timestampMillis = System.currentTimeMillis(),
                    freeStorageAtScanBytes = storage.freeBytes
                )
            } finally {
                _cleanScanStepText.value = ""
                _isScanningClean.value = false
            }
        }
    }

    fun cancelCleanScan() {
        cleanerRepository.cancelCurrentScan()
        _isScanningClean.value = false
        _cleanScanStepText.value = ""
    }

    fun onAppCacheClearFinished(beforeCacheBytes: Long, onResult: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val freed = cleanerRepository.recordRealClearedAppCacheDiff(beforeCacheBytes)
            onResult(freed)
            refreshCleanScan(force = true)
            sensorsRepository.refreshSlowMetrics(force = true)
        }
    }

    fun runOneClickAnalysis() {
        if (_analysisState.value.isRunning) return
        viewModelScope.launch {
            _analysisState.value = OneClickAnalysisState(
                isRunning = true,
                progressFraction = 0.20f,
                stepText = "A verificar armazenamento real (StatFs / StorageStats)..."
            )
            delay(180L)
            val snapshot = sensorsRepository.readCurrentSnapshot()

            _analysisState.value = _analysisState.value.copy(
                progressFraction = 0.50f,
                stepText = "A ler estado térmico (PowerManager) e saúde da bateria..."
            )
            delay(180L)

            _analysisState.value = _analysisState.value.copy(
                progressFraction = 0.80f,
                stepText = "A analisar ficheiros desnecessários e apps sem uso..."
            )
            val clean = cleanerRepository.scanRealDeviceStorage(
                onStepProgress = { step ->
                    _analysisState.value = _analysisState.value.copy(stepText = step)
                }
            )
            _cleanReport.value = clean.copy(
                timestampMillis = System.currentTimeMillis(),
                freeStorageAtScanBytes = snapshot.storage.freeBytes
            )

            val fixes = mutableListOf<ProposedFixItem>()

            if (BuildConfig.IS_FULL_FLAVOR && snapshot.hasUsageStatsPermission && snapshot.installedApps.isNotEmpty()) {
                fixes.add(
                    ProposedFixItem(
                        id = "bg_processes",
                        title = "Libertar processos de apps em segundo plano",
                        subtitle = "Apenas apps ativas nas últimas 3 h e sem uso há > 30 min (o sistema pode reiniciá-las)",
                        requiresSystemConfirmation = false,
                        actionRouteOrType = "AUTO_BG"
                    )
                )
            }

            if (snapshot.unusedAppsCount > 0) {
                fixes.add(
                    ProposedFixItem(
                        id = "unused_apps",
                        title = "${snapshot.unusedAppsCount} app(s) sem uso há mais de 30 dias",
                        subtitle = "Rever no separador Sistema para desinstalar ou restringir bateria",
                        requiresSystemConfirmation = true,
                        actionRouteOrType = "OPEN_SYSTEM"
                    )
                )
            }

            if (!clean.hasMediaPermission) {
                fixes.add(
                    ProposedFixItem(
                        id = "media_perm",
                        title = "Permitir leitura de ficheiros para limpeza",
                        subtitle = "Necessário para detetar duplicados, capturas antigas e ficheiros grandes",
                        requiresSystemConfirmation = true,
                        actionRouteOrType = "OPEN_MEDIA_PERM"
                    )
                )
            } else if (clean.totalCleanableBytes > 0L) {
                fixes.add(
                    ProposedFixItem(
                        id = "clean_files",
                        title = "Mover ${NexusFormatters.formatBytes(clean.totalCleanableBytes)} para o Lixo do sistema",
                        subtitle = "Requer pré-visualização e confirmação no separador Limpar",
                        requiresSystemConfirmation = true,
                        actionRouteOrType = "OPEN_CLEAN"
                    )
                )
            }

            if (snapshot.thermal.level.severityRank >= ThermalLevelPt.MODERADO.severityRank) {
                fixes.add(
                    ProposedFixItem(
                        id = "thermal_warn",
                        title = "Estado térmico atual: ${snapshot.thermal.level.labelPt}",
                        subtitle = "Reduz o brilho ou fecha apps pesadas em Sistema",
                        requiresSystemConfirmation = true,
                        actionRouteOrType = "OPEN_SYSTEM"
                    )
                )
            }

            dao.insertHistory(
                HistoryEntity(
                    actionType = "ANALYSIS",
                    title = "Análise concluída · Saúde ${snapshot.healthScore}/100",
                    details = "Livre: ${NexusFormatters.formatBytes(snapshot.storage.freeBytes)} · Térmico: ${snapshot.thermal.level.labelPt} · ${fixes.size} sugestão(ões)",
                    timestampEpochMillis = System.currentTimeMillis()
                )
            )

            _analysisState.value = OneClickAnalysisState(
                isRunning = false,
                progressFraction = 1f,
                stepText = "Análise concluída",
                completedOnce = true,
                proposedFixes = fixes.toImmutableList(),
                statusMessage = if (fixes.isEmpty()) "Tudo ótimo! Nenhuma ação necessária." else null
            )
        }
    }

    fun applyAutomaticFixes() {
        viewModelScope.launch {
            val stopped = sensorsRepository.stopPermittedBackgroundApps(recordInHistory = true)
            val updated = _analysisState.value.proposedFixes.map { fix ->
                if (!fix.requiresSystemConfirmation) fix.copy(isResolved = true) else fix
            }.toImmutableList()
            _analysisState.value = _analysisState.value.copy(
                proposedFixes = updated,
                statusMessage = if (stopped > 0) {
                    "Pedido ao Android para libertar processos de $stopped apps (o sistema pode reiniciá-los)."
                } else {
                    "Nenhuma app candidata em fundo para libertar. As restantes ações têm atalho direto abaixo."
                }
            )
        }
    }

    fun stopBackgroundAppsManual(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = sensorsRepository.stopPermittedBackgroundApps(recordInHistory = true)
            onDone(count)
        }
    }

    fun buildMoveToTrashIntentSender(files: List<ScannedFileCandidate>): IntentSender? {
        val uris = files.mapNotNull { runCatching { Uri.parse(it.contentUri) }.getOrNull() }
        return cleanerRepository.buildTrashIntentSender(uris, moveToTrash = true)
    }

    fun onTrashRequestConfirmed(
        trashedFiles: List<ScannedFileCandidate>,
        emptyFoldersToDelete: List<String>
    ) {
        viewModelScope.launch {
            cleanerRepository.deleteEmptyFoldersAndRecord(
                emptyFolders = emptyFoldersToDelete,
                trashedFiles = trashedFiles
            )
            refreshCleanScan(force = true)
            sensorsRepository.refreshSlowMetrics(force = true)
        }
    }

    fun buildRestoreFromTrashIntentSender(entry: HistoryEntity): IntentSender? {
        val uris = entry.trashedUrisJson
            .split("|")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        return cleanerRepository.buildTrashIntentSender(uris, moveToTrash = false)
    }

    fun onRestoreConfirmed(entryId: Long) {
        viewModelScope.launch {
            cleanerRepository.markHistoryEntryRestored(entryId)
            refreshCleanScan(force = true)
            sensorsRepository.refreshSlowMetrics(force = true)
        }
    }

    fun startBoost(
        gamePackage: String,
        gameName: String,
        gameMonogram: String,
        enableDnd: Boolean,
        stopBackgroundApps: Boolean,
        launchExternalGame: Boolean
    ) {
        viewModelScope.launch {
            boostManager.startBoostSession(
                gamePackage = gamePackage,
                gameName = gameName,
                gameMonogram = gameMonogram,
                enableDnd = enableDnd,
                stopBackgroundApps = stopBackgroundApps,
                launchExternalGame = launchExternalGame
            )
        }
    }

    fun stopBoost() {
        boostManager.stopBoostSession()
    }

    fun dismissBoostSummary() {
        boostManager.dismissSummaryDialog()
    }

    fun exportHistoryPdf(targetUri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val entries = dao.getAllHistoryOnce()
            val score = liveSnapshot.value.healthScore
            val ok = PdfReportExporter.exportHistoryToPdf(appContext, targetUri, entries, score)
            onResult(ok)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
        }
    }

    fun reopenOnboarding() {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(false)
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDarkThemeEnabled(enabled)
        }
    }

    fun setWeeklyScan(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setWeeklyScanEnabled(enabled)
            SystemMonitorWorker.scheduleOrUpdateWeeklyMonitor(appContext, enabled)
        }
    }

    fun setStorageAlert(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setStorageAlertEnabled(enabled)
        }
    }

    fun setThermalAlert(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setThermalAlertEnabled(enabled)
        }
    }

    fun setBatteryAlert(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBatteryAlertEnabled(enabled)
        }
    }

    fun activateNexusBatterySaver(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = batteryProfileManager.activateNexusSaverProfile(isAutoTriggered = false)
            _hasWriteSettingsPermission.value = batteryProfileManager.hasWriteSettingsPermission()
            onResult(ok)
        }
    }

    fun restoreNexusBatterySaver(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = batteryProfileManager.restorePreviousSettings()
            _hasWriteSettingsPermission.value = batteryProfileManager.hasWriteSettingsPermission()
            onResult(ok)
        }
    }

    fun setAutoBatterySaver(enabled: Boolean, thresholdPercent: Int = 20) {
        viewModelScope.launch {
            preferencesRepository.setAutoBatterySaver(enabled, thresholdPercent)
        }
    }

    fun setChargeLimitAlarm(enabled: Boolean, targetPercent: Int = 80) {
        viewModelScope.launch {
            preferencesRepository.setChargeLimitAlarm(enabled, targetPercent)
            if (enabled && liveSnapshot.value.battery.isCharging) {
                try {
                    val intent = android.content.Intent(appContext, ChargeMonitorForegroundService::class.java).apply {
                        action = ChargeMonitorForegroundService.ACTION_START
                    }
                    androidx.core.content.ContextCompat.startForegroundService(appContext, intent)
                } catch (_: Exception) {
                }
            } else if (!enabled) {
                try {
                    val intent = android.content.Intent(appContext, ChargeMonitorForegroundService::class.java).apply {
                        action = ChargeMonitorForegroundService.ACTION_STOP
                    }
                    appContext.stopService(intent)
                } catch (_: Exception) {
                }
            }
        }
    }
}
