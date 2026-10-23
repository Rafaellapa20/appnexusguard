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
import com.nexusguard.android.data.repository.AdditionalCleanRepository
import com.nexusguard.android.data.repository.AppImpactRepository
import com.nexusguard.android.data.repository.AppUpdateUiState
import com.nexusguard.android.data.repository.AppUpdaterRepository
import com.nexusguard.android.data.repository.BatteryProfileManager
import com.nexusguard.android.data.repository.CleanScanReport
import com.nexusguard.android.data.repository.CleanerRepository
import com.nexusguard.android.data.repository.DeepCleanCompletionResult
import com.nexusguard.android.data.repository.HibernationEngine
import com.nexusguard.android.data.repository.HibernationStateSnapshot
import com.nexusguard.android.data.repository.RealSystemSnapshot
import com.nexusguard.android.data.repository.SystemSensorsRepository
import com.nexusguard.android.domain.AppImpactItem
import com.nexusguard.android.domain.AppImpactPeriod
import com.nexusguard.android.domain.EntitlementsRepository
import com.nexusguard.android.domain.HibernationAppProgressItem
import com.nexusguard.android.domain.HibernationExecutionSummary
import com.nexusguard.android.domain.MonthlyReportSummary
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.PhotoOptimizeQuality
import com.nexusguard.android.domain.PhotoOptimizerCandidate
import com.nexusguard.android.domain.PremiumFeature
import com.nexusguard.android.domain.ScannedFileCandidate
import com.nexusguard.android.domain.ThermalLevelPt
import com.nexusguard.android.flavor.HibernationFlavorDelegate
import com.nexusguard.android.service.BoostSessionState
import com.nexusguard.android.service.ChargeMonitorForegroundService
import com.nexusguard.android.service.GameBoostManager
import com.nexusguard.android.ui.clean.AdditionalCleanToolsState
import com.nexusguard.android.util.PdfReportExporter
import com.nexusguard.android.widget.NexusWidgetUpdater
import com.nexusguard.android.worker.AutoHibernationWorker
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
    private val additionalCleanRepository: AdditionalCleanRepository,
    private val appImpactRepository: AppImpactRepository,
    private val batteryProfileManager: BatteryProfileManager,
    private val boostManager: GameBoostManager,
    private val appUpdaterRepository: AppUpdaterRepository,
    private val entitlementsRepository: EntitlementsRepository,
    private val hibernationEngine: HibernationEngine,
    private val dao: NexusGuardDao
) : ViewModel() {

    fun isFeatureUnlocked(feature: PremiumFeature): Boolean {
        return entitlementsRepository.isPremiumFeatureEnabled(feature)
    }

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

    private val _lastCompletionResult = MutableStateFlow<DeepCleanCompletionResult?>(null)
    val lastCompletionResult: StateFlow<DeepCleanCompletionResult?> = _lastCompletionResult.asStateFlow()

    private val _openDeepCleanEvent = MutableStateFlow(false)
    val openDeepCleanEvent: StateFlow<Boolean> = _openDeepCleanEvent.asStateFlow()

    private val _openHibernationEvent = MutableStateFlow(false)
    val openHibernationEvent: StateFlow<Boolean> = _openHibernationEvent.asStateFlow()

    private val _hibernationState = MutableStateFlow(HibernationStateSnapshot())
    val hibernationState: StateFlow<HibernationStateSnapshot> = _hibernationState.asStateFlow()

    private val _isExecutingHibernation = MutableStateFlow(false)
    val isExecutingHibernation: StateFlow<Boolean> = _isExecutingHibernation.asStateFlow()

    private val _hibernationProgressItems = MutableStateFlow<kotlinx.collections.immutable.ImmutableList<HibernationAppProgressItem>>(persistentListOf())
    val hibernationProgressItems: StateFlow<kotlinx.collections.immutable.ImmutableList<HibernationAppProgressItem>> = _hibernationProgressItems.asStateFlow()

    private val _lastHibernationSummary = MutableStateFlow<HibernationExecutionSummary?>(null)
    val lastHibernationSummary: StateFlow<HibernationExecutionSummary?> = _lastHibernationSummary.asStateFlow()

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
        scanProgressFlow = cleanerRepository.scanProgress,
        lastCompletionFlow = _lastCompletionResult
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

    private val _appsImpactUiState = MutableStateFlow(AppsImpactUiState(isLoading = true))
    val appsImpactUiState: StateFlow<AppsImpactUiState> = _appsImpactUiState.asStateFlow()

    private val _additionalCleanToolsState = MutableStateFlow(
        AdditionalCleanToolsState(
            isCallLogAvailable = additionalCleanRepository.isCallLogCleanerAvailable,
            hasCallLogPermissions = additionalCleanRepository.hasCallLogPermissions()
        )
    )
    val additionalCleanToolsState: StateFlow<AdditionalCleanToolsState> = _additionalCleanToolsState.asStateFlow()

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
        _additionalCleanToolsState.value = _additionalCleanToolsState.value.copy(
            hasClipboardContent = additionalCleanRepository.hasClipboardContent(),
            hasCallLogPermissions = additionalCleanRepository.hasCallLogPermissions()
        )
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
        refreshAdditionalCleanTools()
    }

    fun refreshAdditionalCleanTools(
        quality: PhotoOptimizeQuality = _additionalCleanToolsState.value.selectedQuality,
        limitMax4000px: Boolean = _additionalCleanToolsState.value.limitMaxResolution4000px,
        callLogMonths: Int = _additionalCleanToolsState.value.callLogMonthsThreshold
    ) {
        viewModelScope.launch {
            val hasClip = additionalCleanRepository.hasClipboardContent()
            val downloads = additionalCleanRepository.scanDownloadsFolder()
            val videos = additionalCleanRepository.scanLargeVideos()
            val photos = additionalCleanRepository.scanLargePhotosForOptimizer(quality, limitMax4000px)
            val browsers = additionalCleanRepository.getBrowserPrivacyShortcuts()
            val hasCallPerms = additionalCleanRepository.hasCallLogPermissions()
            val oldCallsCount = if (hasCallPerms) {
                additionalCleanRepository.countOldCallLogs(callLogMonths)
            } else 0
            _additionalCleanToolsState.value = AdditionalCleanToolsState(
                hasClipboardContent = hasClip,
                downloads = downloads,
                largeVideos = videos,
                photoCandidates = photos,
                selectedQuality = quality,
                limitMaxResolution4000px = limitMax4000px,
                isOptimizingPhotos = false,
                browserShortcuts = browsers,
                isCallLogAvailable = additionalCleanRepository.isCallLogCleanerAvailable,
                hasCallLogPermissions = hasCallPerms,
                oldCallLogsCount = oldCallsCount,
                callLogMonthsThreshold = callLogMonths
            )
        }
    }

    fun clearClipboardContent() {
        viewModelScope.launch {
            additionalCleanRepository.clearClipboard()
            _additionalCleanToolsState.value = _additionalCleanToolsState.value.copy(
                hasClipboardContent = additionalCleanRepository.hasClipboardContent()
            )
        }
    }

    fun setPhotoOptimizerOptions(quality: PhotoOptimizeQuality, limitMax4000px: Boolean) {
        refreshAdditionalCleanTools(quality = quality, limitMax4000px = limitMax4000px)
    }

    fun optimizeSelectedPhotos(
        candidates: List<PhotoOptimizerCandidate>,
        onFinished: (Int, Long) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val current = _additionalCleanToolsState.value
            _additionalCleanToolsState.value = current.copy(isOptimizingPhotos = true)
            val (count, saved) = additionalCleanRepository.optimizeSelectedPhotos(
                candidates = candidates,
                quality = current.selectedQuality,
                limitMax4000px = current.limitMaxResolution4000px
            )
            onFinished(count, saved)
            refreshAdditionalCleanTools()
            sensorsRepository.refreshSlowMetrics(force = true)
        }
    }

    fun setCallLogMonthsThreshold(months: Int) {
        refreshAdditionalCleanTools(callLogMonths = months)
    }

    fun deleteOldCallLogs(months: Int, onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val deleted = additionalCleanRepository.deleteOldCallLogs(months)
            onResult(deleted)
            refreshAdditionalCleanTools(callLogMonths = months)
        }
    }

    fun onCallLogPermissionsUpdated() {
        refreshAdditionalCleanTools()
    }

    fun onEnterAppsImpactTab() {
        if (_appsImpactUiState.value.apps.isEmpty()) {
            refreshAppsImpact(period = _appsImpactUiState.value.selectedPeriod, saveStorageSnapshots = true)
        }
    }

    fun selectAppsImpactPeriod(period: AppImpactPeriod) {
        val effectivePeriod = if (
            period != AppImpactPeriod.HOURS_24 &&
            !entitlementsRepository.isPremiumFeatureEnabled(PremiumFeature.APP_IMPACT_7D_30D)
        ) {
            AppImpactPeriod.HOURS_24
        } else {
            period
        }
        refreshAppsImpact(period = effectivePeriod, saveStorageSnapshots = false)
    }

    fun refreshAppsImpact(
        period: AppImpactPeriod = _appsImpactUiState.value.selectedPeriod,
        saveStorageSnapshots: Boolean = true
    ) {
        viewModelScope.launch {
            _appsImpactUiState.value = _appsImpactUiState.value.copy(
                isLoading = true,
                selectedPeriod = period,
                hasUsageStatsPermission = appImpactRepository.hasUsageStatsPermission()
            )
            val loaded = appImpactRepository.loadAppsImpact(
                period = period,
                saveStorageSnapshotsToRoom = saveStorageSnapshots
            )
            _appsImpactUiState.value = AppsImpactUiState(
                isLoading = false,
                selectedPeriod = period,
                hasUsageStatsPermission = appImpactRepository.hasUsageStatsPermission(),
                apps = loaded
            )
        }
    }

    fun onBatchUninstallFinished(
        requestedApps: List<AppImpactItem>,
        onResult: (Int, Long) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val (removedCount, freedBytes) = appImpactRepository.recordVerifiedBatchUninstalls(requestedApps)
            onResult(removedCount, freedBytes)
            refreshAppsImpact(saveStorageSnapshots = true)
            sensorsRepository.refreshSlowMetrics(force = true)
        }
    }

    fun requestOpenDeepCleanFromShortcut() {
        _lastCompletionResult.value = null
        _openDeepCleanEvent.value = true
        refreshCleanScan(force = true)
    }

    fun consumeOpenDeepCleanEvent() {
        _openDeepCleanEvent.value = false
    }

    fun dismissCompletionScreen() {
        _lastCompletionResult.value = null
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
            val result = cleanerRepository.deleteEmptyFoldersAndRecord(
                emptyFolders = emptyFoldersToDelete,
                trashedFiles = trashedFiles
            )
            _lastCompletionResult.value = result
            refreshCleanScan(force = true)
            sensorsRepository.refreshSlowMetrics(force = true)
            NexusWidgetUpdater.updateAllWidgets(appContext)
        }
    }

    fun buildRestoreFromCompletionIntentSender(result: DeepCleanCompletionResult): IntentSender? {
        val uris = result.trashedUrisJson
            .split("|")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
        return cleanerRepository.buildTrashIntentSender(uris, moveToTrash = false)
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
            val prefs = preferencesState.value
            if (prefs.autoHibernationEnabled && prefs.autoHibernateBeforeGameBoost) {
                hibernationEngine.runAutomaticHibernationNow(modeLabelPt = "Automático · Modo Jogo")
            }
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
        if (!entitlementsRepository.isPremiumFeatureEnabled(PremiumFeature.THEMES)) return
        viewModelScope.launch {
            preferencesRepository.setDarkThemeEnabled(enabled)
        }
    }

    fun setWeeklyScan(enabled: Boolean) {
        if (!entitlementsRepository.isPremiumFeatureEnabled(PremiumFeature.SCHEDULED_CLEAN)) return
        viewModelScope.launch {
            preferencesRepository.setWeeklyScanEnabled(enabled)
            val prefs = preferencesState.value
            SystemMonitorWorker.scheduleOrUpdateWeeklyMonitor(
                context = appContext,
                enabled = enabled,
                frequencyDays = prefs.scheduledCleanFrequencyDays,
                targetHourOfDay = prefs.scheduledCleanHourOfDay
            )
        }
    }

    fun setScheduledCleanSchedule(frequencyDays: Int, hourOfDay: Int) {
        viewModelScope.launch {
            preferencesRepository.setScheduledCleanSchedule(frequencyDays, hourOfDay)
            val enabled = preferencesState.value.weeklyScanEnabled
            SystemMonitorWorker.scheduleOrUpdateWeeklyMonitor(
                context = appContext,
                enabled = enabled,
                frequencyDays = frequencyDays,
                targetHourOfDay = hourOfDay
            )
        }
    }

    fun setScheduledAutoCleanOptions(emptyFolders: Boolean, expiredTrash: Boolean, ownCache: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setScheduledAutoCleanOptions(emptyFolders, expiredTrash, ownCache)
        }
    }

    fun setJunkOver1GbAlert(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setJunkOver1GbAlertEnabled(enabled)
        }
    }

    fun setNewAppBatteryAlert(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNewAppBatteryAlertEnabled(enabled)
        }
    }

    fun buildMonthlyReportSummary(): MonthlyReportSummary {
        if (!entitlementsRepository.isPremiumFeatureEnabled(PremiumFeature.MONTHLY_REPORT)) {
            return MonthlyReportSummary()
        }
        val now = System.currentTimeMillis()
        val cutoff30d = now - (30L * 24L * 3600L * 1000L)
        val entries30d = allHistory.value.filter { it.timestampEpochMillis >= cutoff30d }
        val totalFreed = entries30d.filter { !it.isRestored }.sumOf { it.bytesFreed }
        val healthRegex = Regex("""Saúde (\d+)/100""")
        val healthPoints = entries30d
            .reversed()
            .mapNotNull { entry ->
                healthRegex.find(entry.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            }
            .toMutableList()
            .apply {
                add(liveSnapshot.value.healthScore)
            }
        val measuredBattery = batteryUiState.value.measuredConsumption
        val topApps = _appsImpactUiState.value.apps
            .sortedByDescending { it.totalStorageBytes + (it.batteryImpactScoreMillis * 10L) }
            .take(5)

        return MonthlyReportSummary(
            periodLabelPt = "Últimos 30 dias",
            totalBytesFreed30d = totalFreed,
            actionsCount30d = entries30d.size,
            currentHealthScore = liveSnapshot.value.healthScore,
            healthEvolutionPoints = healthPoints,
            topImpactApps = topApps,
            avgScreenOnDrainPerHour = measuredBattery.screenOnDrainPercentPerHour,
            avgIdleDrainPerHour = measuredBattery.screenOffIdleDrainPercentPerHour
        )
    }

    fun exportMonthlyReportPdf(targetUri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val summary = buildMonthlyReportSummary()
            val ok = PdfReportExporter.exportMonthlyReportToPdf(appContext, targetUri, summary)
            onResult(ok)
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
        if (!entitlementsRepository.isPremiumFeatureEnabled(PremiumFeature.AUTO_BATTERY_SAVER)) return
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

    // Fase 11: Hibernação de apps
    fun requestOpenHibernationFromShortcut() {
        _openHibernationEvent.value = true
    }

    fun consumeOpenHibernationEvent() {
        _openHibernationEvent.value = false
    }

    fun refreshHibernationState() {
        viewModelScope.launch {
            val prefs = preferencesState.value
            _hibernationState.value = hibernationEngine.loadHibernationState(prefs)
        }
    }

    fun executeHibernationManual() {
        if (_isExecutingHibernation.value) return
        viewModelScope.launch {
            _isExecutingHibernation.value = true
            _lastHibernationSummary.value = null
            try {
                val prefs = preferencesState.value
                val currentCandidates = _hibernationState.value.recommendedCandidates
                val summary = hibernationEngine.executeHibernationBatch(
                    modeLabelPt = "Manual",
                    candidates = currentCandidates,
                    prefs = prefs,
                    onProgress = { items ->
                        _hibernationProgressItems.value = items.toImmutableList()
                    }
                )
                _lastHibernationSummary.value = summary
                _hibernationState.value = hibernationEngine.loadHibernationState(prefs)
            } finally {
                _isExecutingHibernation.value = false
            }
        }
    }

    fun cancelHibernationManual() {
        HibernationFlavorDelegate.cancelOngoingBatch()
    }

    fun dismissHibernationSummary() {
        _lastHibernationSummary.value = null
    }

    fun requestShizukuPermission() {
        HibernationFlavorDelegate.requestShizukuPermission()
        refreshHibernationState()
    }

    fun acceptAccessibilityDisclosure() {
        viewModelScope.launch {
            preferencesRepository.setAccessibilityDisclosureAccepted(true)
        }
    }

    fun setAutoHibernationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoHibernationEnabled(enabled)
            val prefs = preferencesState.value
            AutoHibernationWorker.syncPeriodicSchedule(
                context = appContext,
                enabled = enabled,
                intervalHours = prefs.autoHibernatePeriodicHours
            )
            refreshHibernationState()
        }
    }

    fun updateAutoHibernationConfig(
        onScreenOff: Boolean,
        periodicHours: Int,
        beforeGameBoost: Boolean,
        top10Only: Boolean
    ) {
        viewModelScope.launch {
            preferencesRepository.setAutoHibernationConfig(
                onScreenOff = onScreenOff,
                periodicHours = periodicHours,
                beforeGameBoost = beforeGameBoost,
                top10BatteryOnly = top10Only
            )
            AutoHibernationWorker.syncPeriodicSchedule(
                context = appContext,
                enabled = preferencesState.value.autoHibernationEnabled,
                intervalHours = periodicHours
            )
            refreshHibernationState()
        }
    }

    fun toggleNeverClosePackage(packageName: String) {
        viewModelScope.launch {
            preferencesRepository.toggleNeverClosePackage(packageName)
            delay(60L)
            refreshHibernationState()
        }
    }

    fun toggleAlwaysHibernatePackage(packageName: String) {
        viewModelScope.launch {
            preferencesRepository.toggleAlwaysHibernatePackage(packageName)
            delay(60L)
            refreshHibernationState()
        }
    }
}
