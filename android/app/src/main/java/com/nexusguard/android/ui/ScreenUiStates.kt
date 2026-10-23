package com.nexusguard.android.ui

import androidx.compose.runtime.Immutable
import com.nexusguard.android.data.local.BatterySampleEntity
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.preferences.UserPreferencesState
import com.nexusguard.android.data.repository.CleanScanReport
import com.nexusguard.android.data.repository.InstalledAppUsageInfo
import com.nexusguard.android.data.repository.LiveBatteryMetrics
import com.nexusguard.android.data.repository.LiveStorageMetrics
import com.nexusguard.android.data.repository.LiveThermalMetrics
import com.nexusguard.android.data.repository.RealSystemSnapshot
import com.nexusguard.android.domain.ThermalLevelPt
import com.nexusguard.android.service.BoostSessionState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Immutable
data class ProposedFixItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val requiresSystemConfirmation: Boolean,
    val actionRouteOrType: String,
    val isResolved: Boolean = false
)

@Immutable
data class OneClickAnalysisState(
    val isRunning: Boolean = false,
    val progressFraction: Float = 0f,
    val stepText: String = "",
    val completedOnce: Boolean = false,
    val proposedFixes: ImmutableList<ProposedFixItem> = persistentListOf(),
    val statusMessage: String? = null
)

@Immutable
data class HomeUiState(
    val isLoading: Boolean = true,
    val healthScore: Int = 0,
    val healthLabel: String = "A ler sensores...",
    val storage: LiveStorageMetrics = LiveStorageMetrics(0, 0, 0, 0, 0, 0, 0, false),
    val battery: LiveBatteryMetrics = LiveBatteryMetrics(
        levelPercent = 0,
        temperatureCelsius = null,
        healthLabelPt = "A ler...",
        isHealthy = true,
        voltageMv = 0,
        isCharging = false,
        cycleCountText = "N/D",
        cycleCountReason = "A ler...",
        statusLineText = "A ler...",
        detailLineText = ""
    ),
    val thermal: LiveThermalMetrics = LiveThermalMetrics(
        level = ThermalLevelPt.NORMAL,
        headroomValue = null,
        headroomText = "A ler..."
    ),
    val unusedAppsCount: Int = 0,
    val installedAppsCount: Int = 0,
    val hasUsageStatsPermission: Boolean = false,
    val recentHistory: ImmutableList<HistoryEntity> = persistentListOf(),
    val analysisState: OneClickAnalysisState = OneClickAnalysisState()
)

@Immutable
data class CleanUiState(
    val isLoading: Boolean = true,
    val storage: LiveStorageMetrics = LiveStorageMetrics(0, 0, 0, 0, 0, 0, 0, false),
    val cleanReport: CleanScanReport = CleanScanReport(
        hasMediaPermission = false,
        hasManageStoragePermission = false,
        categories = persistentListOf(),
        totalCleanableBytes = 0L,
        scannedFilesCount = 0
    ),
    val isScanning: Boolean = false,
    val scanStepText: String = ""
)

@Immutable
data class GamesUiState(
    val isLoading: Boolean = true,
    val installedApps: ImmutableList<InstalledAppUsageInfo> = persistentListOf(),
    val thermal: LiveThermalMetrics = LiveThermalMetrics(
        level = ThermalLevelPt.NORMAL,
        headroomValue = null,
        headroomText = "A ler..."
    ),
    val batteryTempCelsius: Float? = null,
    val boostSession: BoostSessionState = BoostSessionState(),
    val hasDndPermission: Boolean = false
)

@Immutable
data class BatteryUiState(
    val isLoading: Boolean = true,
    val battery: LiveBatteryMetrics = LiveBatteryMetrics(
        levelPercent = 0,
        temperatureCelsius = null,
        healthLabelPt = "A ler...",
        isHealthy = true,
        voltageMv = 0,
        isCharging = false,
        cycleCountText = "N/D",
        cycleCountReason = "A ler...",
        statusLineText = "A ler...",
        detailLineText = ""
    ),
    val batterySamples: ImmutableList<BatterySampleEntity> = persistentListOf(),
    val highBackgroundAppsCount: Int = 0,
    val unusedAppsCount: Int = 0,
    val hasUsageStatsPermission: Boolean = false,
    val storageAlertEnabled: Boolean = true,
    val thermalAlertEnabled: Boolean = true,
    val batteryAlertEnabled: Boolean = true
)

@Immutable
data class SystemUiState(
    val isLoading: Boolean = true,
    val batteryTempCelsius: Float? = null,
    val thermal: LiveThermalMetrics = LiveThermalMetrics(
        level = ThermalLevelPt.NORMAL,
        headroomValue = null,
        headroomText = "A ler..."
    ),
    val tempHistory60s: ImmutableList<Float> = persistentListOf(),
    val availableRamBytes: Long = 0L,
    val totalRamBytes: Long = 0L,
    val hasUsageStatsPermission: Boolean = false,
    val installedApps: ImmutableList<InstalledAppUsageInfo> = persistentListOf()
)

object ScreenUiStateMappers {

    fun mapToHomeCore(snapshot: RealSystemSnapshot): HomeUiState {
        return HomeUiState(
            isLoading = snapshot.isLoading,
            healthScore = snapshot.healthScore,
            healthLabel = snapshot.healthLabel,
            storage = snapshot.storage,
            battery = snapshot.battery,
            thermal = snapshot.thermal,
            unusedAppsCount = snapshot.unusedAppsCount,
            installedAppsCount = snapshot.installedApps.size,
            hasUsageStatsPermission = snapshot.hasUsageStatsPermission
        )
    }

    fun observeHomeUiState(
        snapshotFlow: Flow<RealSystemSnapshot>,
        recentHistoryFlow: Flow<List<HistoryEntity>>,
        analysisStateFlow: Flow<OneClickAnalysisState>
    ): Flow<HomeUiState> {
        return combine(
            snapshotFlow.map(::mapToHomeCore).distinctUntilChanged(),
            recentHistoryFlow,
            analysisStateFlow
        ) { core, history, analysis ->
            core.copy(
                recentHistory = history.toImmutableList(),
                analysisState = analysis
            )
        }.distinctUntilChanged()
    }

    fun observeCleanUiState(
        snapshotFlow: Flow<RealSystemSnapshot>,
        cleanReportFlow: Flow<CleanScanReport>,
        isScanningFlow: Flow<Boolean>,
        scanStepFlow: Flow<String>
    ): Flow<CleanUiState> {
        val storageOnlyFlow = snapshotFlow
            .map { it.isLoading to it.storage }
            .distinctUntilChanged()

        return combine(
            storageOnlyFlow,
            cleanReportFlow,
            isScanningFlow,
            scanStepFlow
        ) { (isLoading, storage), report, scanning, stepText ->
            CleanUiState(
                isLoading = isLoading,
                storage = storage,
                cleanReport = report,
                isScanning = scanning,
                scanStepText = stepText
            )
        }.distinctUntilChanged()
    }

    fun observeGamesUiState(
        snapshotFlow: Flow<RealSystemSnapshot>,
        boostSessionFlow: Flow<BoostSessionState>,
        hasDndFlow: Flow<Boolean>
    ): Flow<GamesUiState> {
        return combine(
            snapshotFlow,
            boostSessionFlow,
            hasDndFlow
        ) { snapshot, boost, hasDnd ->
            GamesUiState(
                isLoading = snapshot.isLoading,
                installedApps = snapshot.installedApps,
                thermal = snapshot.thermal,
                batteryTempCelsius = snapshot.battery.temperatureCelsius,
                boostSession = boost,
                hasDndPermission = hasDnd
            )
        }.distinctUntilChanged()
    }

    fun observeBatteryUiState(
        snapshotFlow: Flow<RealSystemSnapshot>,
        samplesFlow: Flow<List<BatterySampleEntity>>,
        prefsFlow: Flow<UserPreferencesState>
    ): Flow<BatteryUiState> {
        return combine(
            snapshotFlow,
            samplesFlow,
            prefsFlow
        ) { snapshot, samples, prefs ->
            BatteryUiState(
                isLoading = snapshot.isLoading,
                battery = snapshot.battery,
                batterySamples = samples.toImmutableList(),
                highBackgroundAppsCount = snapshot.highBackgroundAppsCount,
                unusedAppsCount = snapshot.unusedAppsCount,
                hasUsageStatsPermission = snapshot.hasUsageStatsPermission,
                storageAlertEnabled = prefs.storageAlertEnabled,
                thermalAlertEnabled = prefs.thermalAlertEnabled,
                batteryAlertEnabled = prefs.batteryAlertEnabled
            )
        }.distinctUntilChanged()
    }

    fun observeSystemUiState(
        snapshotFlow: Flow<RealSystemSnapshot>
    ): Flow<SystemUiState> {
        return snapshotFlow.map { snapshot ->
            SystemUiState(
                isLoading = snapshot.isLoading,
                batteryTempCelsius = snapshot.battery.temperatureCelsius,
                thermal = snapshot.thermal,
                tempHistory60s = snapshot.tempHistory60s,
                availableRamBytes = snapshot.availableRamBytes,
                totalRamBytes = snapshot.totalRamBytes,
                hasUsageStatsPermission = snapshot.hasUsageStatsPermission,
                installedApps = snapshot.installedApps
            )
        }.distinctUntilChanged()
    }
}
