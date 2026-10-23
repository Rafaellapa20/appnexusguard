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
import com.nexusguard.android.data.repository.CleanCategoryType
import com.nexusguard.android.data.repository.CleanScanReport
import com.nexusguard.android.data.repository.CleanerRepository
import com.nexusguard.android.data.repository.RealSystemSnapshot
import com.nexusguard.android.data.repository.SystemSensorsRepository
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ScannedFileCandidate
import com.nexusguard.android.domain.ThermalLevelPt
import com.nexusguard.android.service.BoostSessionState
import com.nexusguard.android.service.GameBoostManager
import com.nexusguard.android.util.PdfReportExporter
import com.nexusguard.android.worker.SystemMonitorWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProposedFixItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val requiresSystemConfirmation: Boolean,
    val actionRouteOrType: String, // "AUTO_BG", "OPEN_CLEAN", "OPEN_USAGE_PERM", "OPEN_MEDIA_PERM", "OPEN_SYSTEM"
    val isResolved: Boolean = false
)

data class OneClickAnalysisState(
    val isRunning: Boolean = false,
    val progressFraction: Float = 0f,
    val stepText: String = "",
    val completedOnce: Boolean = false,
    val proposedFixes: List<ProposedFixItem> = emptyList(),
    val statusMessage: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val sensorsRepository: SystemSensorsRepository,
    private val cleanerRepository: CleanerRepository,
    private val boostManager: GameBoostManager,
    private val appUpdaterRepository: AppUpdaterRepository,
    private val dao: NexusGuardDao
) : ViewModel() {

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

    private val _analysisState = MutableStateFlow(OneClickAnalysisState())
    val analysisState: StateFlow<OneClickAnalysisState> = _analysisState.asStateFlow()

    init {
        viewModelScope.launch {
            SystemMonitorWorker.scheduleOrUpdateWeeklyMonitor(appContext, true)
            refreshCleanScan()
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
            sensorsRepository.readCurrentSnapshot()
            refreshCleanScan()
        }
    }

    fun refreshCleanScan() {
        viewModelScope.launch {
            _isScanningClean.value = true
            _cleanReport.value = cleanerRepository.scanRealDeviceStorage()
            _isScanningClean.value = false
        }
    }

    fun runOneClickAnalysis() {
        if (_analysisState.value.isRunning) return
        viewModelScope.launch {
            _analysisState.value = OneClickAnalysisState(
                isRunning = true,
                progressFraction = 0.20f,
                stepText = "A verificar armazenamento real (StatFs / MediaStore)..."
            )
            delay(250L)
            val snapshot = sensorsRepository.readCurrentSnapshot()

            _analysisState.value = _analysisState.value.copy(
                progressFraction = 0.50f,
                stepText = "A ler estado térmico (PowerManager) e saúde da bateria..."
            )
            delay(250L)

            _analysisState.value = _analysisState.value.copy(
                progressFraction = 0.80f,
                stepText = "A analisar ficheiros desnecessários e apps sem uso..."
            )
            val clean = cleanerRepository.scanRealDeviceStorage()
            _cleanReport.value = clean

            val fixes = mutableListOf<ProposedFixItem>()

            if (BuildConfig.IS_FULL_FLAVOR && snapshot.installedApps.isNotEmpty()) {
                fixes.add(
                    ProposedFixItem(
                        id = "bg_processes",
                        title = "Terminar processos em segundo plano permitidos",
                        subtitle = "Executa killBackgroundProcesses nas apps em fundo sem fechar apps abertas",
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
                proposedFixes = fixes,
                statusMessage = if (fixes.isEmpty()) "Tudo ótimo! Nenhuma ação necessária." else null
            )
        }
    }

    fun applyAutomaticFixes() {
        viewModelScope.launch {
            val stopped = sensorsRepository.stopPermittedBackgroundApps(recordInHistory = true)
            val updated = _analysisState.value.proposedFixes.map { fix ->
                if (!fix.requiresSystemConfirmation) fix.copy(isResolved = true) else fix
            }
            _analysisState.value = _analysisState.value.copy(
                proposedFixes = updated,
                statusMessage = if (stopped > 0) {
                    "Corrigido automaticamente: $stopped processo(s) em fundo terminado(s). As restantes ações requerem confirmação."
                } else {
                    "Ações automáticas aplicadas. As ações que exigem confirmação têm atalho direto abaixo."
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
            refreshCleanScan()
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
            refreshCleanScan()
            sensorsRepository.refreshSlowMetrics(force = true)
        }
    }

    fun hasDndPermission(): Boolean = boostManager.hasDndPermission()

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
}
