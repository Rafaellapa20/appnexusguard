package com.nexusguard.android.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.local.BatterySampleEntity
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.preferences.UserPreferencesState
import com.nexusguard.android.data.repository.CleanCategoryGroup
import com.nexusguard.android.data.repository.CleanCategoryType
import com.nexusguard.android.data.repository.CleanScanReport
import com.nexusguard.android.data.repository.InstalledAppUsageInfo
import com.nexusguard.android.data.repository.RealSystemSnapshot
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ScannedFileCandidate
import com.nexusguard.android.domain.ThermalLevelPt
import com.nexusguard.android.service.BoostSessionState
import com.nexusguard.android.ui.components.GaugeShieldLogo
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusTopBar
import com.nexusguard.android.ui.navigation.NexusRoutes
import com.nexusguard.android.ui.navigation.NexusTab
import com.nexusguard.android.ui.onboarding.OnboardingScreen
import com.nexusguard.android.ui.theme.JetBrainsMonoFamily
import com.nexusguard.android.ui.theme.NexusButtonShape
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import java.util.Locale

@Composable
fun NexusGuardMainScreen(
    viewModel: MainViewModel
) {
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val snapshot by viewModel.liveSnapshot.collectAsStateWithLifecycle()
    val recentHistory by viewModel.recentHistory.collectAsStateWithLifecycle()
    val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
    val batterySamples by viewModel.batterySamples24h.collectAsStateWithLifecycle()
    val cleanReport by viewModel.cleanReport.collectAsStateWithLifecycle()
    val isScanningClean by viewModel.isScanningClean.collectAsStateWithLifecycle()
    val analysisState by viewModel.analysisState.collectAsStateWithLifecycle()
    val boostSession by viewModel.boostSession.collectAsStateWithLifecycle()

    if (!preferences.onboardingCompleted) {
        OnboardingScreen(onFinishOnboarding = viewModel::completeOnboarding)
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NexusTab.HOME.route
    val currentTab = NexusTab.entries.find { it.route == currentRoute }
    val colors = NexusTheme.colors

    val showBottomBar = currentRoute in NexusTab.entries.map { it.route }

    // Diálogo de resumo quando termina o Boost
    boostSession.lastSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = viewModel::dismissBoostSummary,
            containerColor = colors.surface,
            titleContentColor = colors.text,
            textContentColor = colors.textDim,
            title = {
                Text("Resumo do Boost · ${summary.gameName}")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val tempStr = summary.maxBatteryTempCelsius?.let {
                        String.format(Locale("pt", "PT"), "%.1f °C", it)
                    } ?: "N/D"
                    Text("• Tempo jogado: ${summary.durationFormatted}")
                    Text("• Temperatura máxima da bateria: $tempStr")
                    Text("• Pior estado térmico: ${summary.worstThermalLevel.labelPt}")
                    Text("• Bateria gasta na sessão: ${summary.batteryUsedPercent}%")
                    Text("• Não incomodar reposto para o estado anterior.")
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissBoostSummary) {
                    Text("Fechar", color = colors.accent2)
                }
            }
        )
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            when (currentRoute) {
                NexusRoutes.SETTINGS -> {
                    SecondaryTopBar(
                        title = "Definições",
                        onBack = { navController.popBackStack() }
                    )
                }
                NexusRoutes.HISTORY -> {
                    SecondaryTopBar(
                        title = "Histórico",
                        onBack = { navController.popBackStack() },
                        showSettings = true,
                        onOpenSettings = {
                            navController.navigate(NexusRoutes.SETTINGS) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                NexusRoutes.ACTIVE_BOOST -> {
                    // O ecrã de sessão ativa mostra o cartão da notificação no topo
                }
                else -> {
                    NexusTopBar(
                        title = currentTab?.label,
                        showBrandHeader = currentRoute == NexusTab.HOME.route,
                        showHistoryAction = currentRoute == NexusTab.HOME.route,
                        showSettingsButton = currentRoute == NexusTab.SYSTEM.route,
                        onHistoryClick = {
                            navController.navigate(NexusRoutes.HISTORY) {
                                launchSingleTop = true
                            }
                        },
                        onSettingsClick = {
                            navController.navigate(NexusRoutes.SETTINGS) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        },
        bottomBar = {
            if (showBottomBar) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.border)
                    )
                    NavigationBar(
                        containerColor = colors.bg,
                        contentColor = colors.text,
                        tonalElevation = 0.dp
                    ) {
                        NexusTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != tab.route) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = colors.accent,
                                    selectedTextColor = colors.text,
                                    indicatorColor = colors.accent.copy(alpha = 0.22f),
                                    unselectedIconColor = colors.textMuted,
                                    unselectedTextColor = colors.textMuted
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NexusTab.HOME.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(NexusTab.HOME.route) {
                RealHomeScreen(
                    snapshot = snapshot,
                    recentHistory = recentHistory,
                    analysisState = analysisState,
                    onRunAnalysis = viewModel::runOneClickAnalysis,
                    onApplySafeFixes = viewModel::applyAutomaticFixes,
                    onOpenBattery = { navController.navigate(NexusTab.BATTERY.route) },
                    onOpenClean = { navController.navigate(NexusTab.CLEAN.route) },
                    onOpenSystem = { navController.navigate(NexusTab.SYSTEM.route) },
                    onOpenHistory = { navController.navigate(NexusRoutes.HISTORY) }
                )
            }
            composable(NexusTab.CLEAN.route) {
                RealCleanScreen(
                    snapshot = snapshot,
                    cleanReport = cleanReport,
                    isScanning = isScanningClean,
                    onRefreshScan = viewModel::refreshCleanScan,
                    buildTrashIntent = viewModel::buildMoveToTrashIntentSender,
                    onTrashConfirmed = viewModel::onTrashRequestConfirmed
                )
            }
            composable(NexusTab.GAMES.route) {
                RealGamesScreen(
                    snapshot = snapshot,
                    boostSession = boostSession,
                    hasDndPermission = viewModel.hasDndPermission(),
                    onStartBoost = { pkg, name, monogram, dnd, stopBg, launchGame ->
                        viewModel.startBoost(pkg, name, monogram, dnd, stopBg, launchGame)
                        navController.navigate(NexusRoutes.ACTIVE_BOOST)
                    },
                    onOpenActiveSession = {
                        navController.navigate(NexusRoutes.ACTIVE_BOOST)
                    }
                )
            }
            composable(NexusTab.BATTERY.route) {
                RealBatteryScreen(
                    snapshot = snapshot,
                    batterySamples = batterySamples,
                    preferences = preferences,
                    onToggleStorageAlert = viewModel::setStorageAlert,
                    onToggleThermalAlert = viewModel::setThermalAlert,
                    onToggleBatteryAlert = viewModel::setBatteryAlert,
                    onViewBackgroundApps = {
                        navController.navigate(NexusTab.SYSTEM.route)
                    }
                )
            }
            composable(NexusTab.SYSTEM.route) {
                RealSystemScreen(
                    snapshot = snapshot,
                    onRefresh = viewModel::refreshAllRealData,
                    onStopPermittedBackground = viewModel::stopBackgroundAppsManual,
                    onOpenSettings = { navController.navigate(NexusRoutes.SETTINGS) },
                    onOpenHistory = { navController.navigate(NexusRoutes.HISTORY) }
                )
            }
            composable(NexusRoutes.ACTIVE_BOOST) {
                RealActiveBoostScreen(
                    snapshot = snapshot,
                    boostSession = boostSession,
                    onStopBoost = {
                        viewModel.stopBoost()
                        navController.popBackStack()
                    }
                )
            }
            composable(NexusRoutes.HISTORY) {
                RealHistoryScreen(
                    entries = allHistory,
                    buildRestoreIntent = viewModel::buildRestoreFromTrashIntentSender,
                    onRestoreConfirmed = viewModel::onRestoreConfirmed,
                    onExportPdf = viewModel::exportHistoryPdf
                )
            }
            composable(NexusRoutes.SETTINGS) {
                RealSettingsScreen(
                    preferences = preferences,
                    snapshot = snapshot,
                    cleanReport = cleanReport,
                    hasDndPermission = viewModel.hasDndPermission(),
                    onToggleDarkTheme = viewModel::setDarkTheme,
                    onToggleWeeklyScan = viewModel::setWeeklyScan,
                    onToggleStorageAlert = viewModel::setStorageAlert,
                    onToggleThermalAlert = viewModel::setThermalAlert,
                    onToggleBatteryAlert = viewModel::setBatteryAlert,
                    onReopenOnboarding = viewModel::reopenOnboarding
                )
            }
        }
    }
}

@Composable
private fun SecondaryTopBar(
    title: String,
    onBack: () -> Unit,
    showSettings: Boolean = false,
    onOpenSettings: () -> Unit = {}
) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .background(colors.bg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Voltar",
                        tint = colors.text
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text
                )
            }
            if (showSettings) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Definições",
                        tint = colors.text
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.border)
        )
    }
}

// ============================================================================
// 1 · INÍCIO (100% Dados Reais do Telemóvel)
// ============================================================================
@Composable
private fun RealHomeScreen(
    snapshot: RealSystemSnapshot,
    recentHistory: List<HistoryEntity>,
    analysisState: OneClickAnalysisState,
    onRunAnalysis: () -> Unit,
    onApplySafeFixes: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenClean: () -> Unit,
    onOpenSystem: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val score = snapshot.healthScore
    val ringStatusColor = when {
        score >= 80 -> colors.ok
        score >= 60 -> colors.warn
        else -> colors.danger
    }

    val lastAnalysisEntry = recentHistory.firstOrNull { it.actionType == "ANALYSIS" }
    val ringCaption = when {
        analysisState.completedOnce -> {
            "Análise concluída · ${analysisState.proposedFixes.size} sugestão(ões)"
        }
        lastAnalysisEntry != null -> {
            "Última análise ${NexusFormatters.formatRelativeTimePt(lastAnalysisEntry.timestampEpochMillis)}"
        }
        else -> {
            "Sensores medidos ao vivo · Toque em Analisar agora"
        }
    }

    val freeStorageGb = NexusFormatters.formatGigabytesInt(snapshot.storage.freeBytes)
    val totalStorageGb = NexusFormatters.formatGigabytesInt(snapshot.storage.totalBytes)
    val freeRatio = if (snapshot.storage.totalBytes > 0) {
        snapshot.storage.freeBytes.toFloat() / snapshot.storage.totalBytes.toFloat()
    } else 1f
    val storageDotColor = when {
        freeRatio < 0.10f -> colors.danger
        freeRatio < 0.20f -> colors.warn
        else -> colors.ok
    }

    val tempText = snapshot.battery.temperatureCelsius?.let {
        String.format(Locale("pt", "PT"), "%.1f °C", it)
    } ?: "N/D"
    val batteryDotColor = if (snapshot.battery.isHealthy && (snapshot.battery.temperatureCelsius ?: 30f) < 40f) {
        colors.ok
    } else {
        colors.warn
    }

    val thermalDotColor = when (snapshot.thermal.level) {
        ThermalLevelPt.NORMAL, ThermalLevelPt.INDISPONIVEL -> colors.ok
        ThermalLevelPt.LIGEIRO, ThermalLevelPt.MODERADO -> colors.warn
        ThermalLevelPt.GRAVE, ThermalLevelPt.CRITICO -> colors.danger
    }

    val unusedValueText = if (snapshot.hasUsageStatsPermission) {
        snapshot.unusedAppsCount.toString()
    } else {
        "Ativar"
    }
    val unusedSubtitleText = if (snapshot.hasUsageStatsPermission) {
        "> 30 dias (${snapshot.installedApps.size} apps lidas)"
    } else {
        "toque para UsageStats"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Anel grande de Saúde (0-100) medido de verdade
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(164.dp)
            ) {
                val trackColor = colors.surfaceAlt
                val ringBrush = Brush.linearGradient(
                    colors = listOf(colors.accent2, colors.accent)
                )
                Canvas(modifier = Modifier.size(150.dp)) {
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = ringBrush,
                        startAngle = -90f,
                        sweepAngle = 360f * (score / 100f),
                        useCenter = false,
                        style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = score.toString(),
                        style = NexusMonoTextStyles.metricHero.copy(fontSize = 42.sp),
                        color = colors.text
                    )
                    Text(
                        text = snapshot.healthLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = ringStatusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ringCaption,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }

        // Botão principal «Analisar agora»
        NexusPrimaryGradientButton(
            text = if (analysisState.isRunning) "A analisar..." else "Analisar agora",
            enabled = !analysisState.isRunning,
            onClick = onRunAnalysis,
            modifier = Modifier.fillMaxWidth()
        )

        // Progresso e resultados do fluxo «Analisar e corrigir com 1 clique»
        if (analysisState.isRunning || analysisState.completedOnce) {
            NexusCard(
                backgroundColor = colors.surface,
                borderColor = colors.borderStrong,
                contentPadding = PaddingValues(14.dp)
            ) {
                if (analysisState.isRunning) {
                    Text(
                        text = analysisState.stepText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { analysisState.progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.accent2,
                        trackColor = colors.surfaceAlt
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Diagnóstico e Correções",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text
                        )
                        val autoFixes = analysisState.proposedFixes.count { !it.requiresSystemConfirmation && !it.isResolved }
                        if (autoFixes > 0) {
                            Text(
                                text = "Corrigir tudo",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.accent2,
                                modifier = Modifier.clickable(onClick = onApplySafeFixes)
                            )
                        }
                    }

                    analysisState.statusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ok
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    analysisState.proposedFixes.forEach { fix ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fix.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (fix.isResolved) colors.ok else colors.text
                                )
                                Text(
                                    text = fix.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (fix.isResolved) {
                                Text("Concluído", style = NexusMonoTextStyles.badge, color = colors.ok)
                            } else {
                                Text(
                                    text = if (fix.requiresSystemConfirmation) "Ação necessária" else "Corrigir",
                                    style = NexusMonoTextStyles.badge,
                                    color = if (fix.requiresSystemConfirmation) colors.warn else colors.accent2,
                                    modifier = Modifier.clickable {
                                        when (fix.actionRouteOrType) {
                                            "AUTO_BG" -> onApplySafeFixes()
                                            "OPEN_CLEAN" -> onOpenClean()
                                            "OPEN_SYSTEM" -> onOpenSystem()
                                            "OPEN_USAGE_PERM" -> openUsageAccessSettings(context)
                                            "OPEN_MEDIA_PERM" -> onOpenClean()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4 Cartões Reais: Armazenamento, Bateria, Térmica, Apps sem uso
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeStatusCard(
                modifier = Modifier.weight(1f),
                dotColor = storageDotColor,
                label = "Armazenamento",
                value = freeStorageGb,
                subtitle = "livres de $totalStorageGb",
                useMonoFont = true,
                onClick = onOpenClean
            )
            HomeStatusCard(
                modifier = Modifier.weight(1f),
                dotColor = batteryDotColor,
                label = "Bateria",
                value = "${snapshot.battery.levelPercent}%",
                subtitle = "${snapshot.battery.healthLabelPt.lowercase()} · $tempText",
                useMonoFont = true,
                onClick = onOpenBattery
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeStatusCard(
                modifier = Modifier.weight(1f),
                dotColor = thermalDotColor,
                label = "Térmica",
                value = snapshot.thermal.level.labelPt,
                subtitle = snapshot.thermal.headroomText,
                useMonoFont = false,
                onClick = onOpenSystem
            )
            HomeStatusCard(
                modifier = Modifier.weight(1f),
                dotColor = if (snapshot.unusedAppsCount > 0 || !snapshot.hasUsageStatsPermission) colors.warn else colors.ok,
                label = "Apps sem uso",
                value = unusedValueText,
                subtitle = unusedSubtitleText,
                useMonoFont = snapshot.hasUsageStatsPermission,
                onClick = {
                    if (!snapshot.hasUsageStatsPermission) {
                        openUsageAccessSettings(context)
                    } else {
                        onOpenSystem()
                    }
                }
            )
        }

        // Secção Recente (as últimas 3 ações reais da base de dados Room)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            Text(
                text = "Recente",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (recentHistory.isEmpty()) {
                Text(
                    text = "Sem ações registadas ainda. Ao analisar, limpar ou usar o Boost, tudo fica registado aqui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                recentHistory.forEachIndexed { idx, entry ->
                    RecentActionRow(
                        title = entry.title,
                        timeLabel = NexusFormatters.formatRelativeTimePt(entry.timestampEpochMillis),
                        onClick = onOpenHistory
                    )
                    if (idx < recentHistory.lastIndex) {
                        HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStatusCard(
    modifier: Modifier = Modifier,
    dotColor: Color,
    label: String,
    value: String,
    subtitle: String,
    useMonoFont: Boolean,
    onClick: () -> Unit
) {
    val colors = NexusTheme.colors
    NexusCard(
        modifier = modifier,
        backgroundColor = colors.surface,
        borderColor = colors.border,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = value,
            style = if (useMonoFont) {
                NexusMonoTextStyles.metricLarge
            } else {
                MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            },
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            ),
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentActionRow(
    title: String,
    timeLabel: String,
    onClick: () -> Unit
) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = timeLabel,
            style = NexusMonoTextStyles.metricSmall.copy(fontSize = 12.sp),
            color = colors.textMuted
        )
    }
}

// ============================================================================
// 2 · BATERIA (100% Dados Reais do BatteryManager)
// ============================================================================
@Composable
private fun RealBatteryScreen(
    snapshot: RealSystemSnapshot,
    batterySamples: List<BatterySampleEntity>,
    preferences: UserPreferencesState,
    onToggleStorageAlert: (Boolean) -> Unit,
    onToggleThermalAlert: (Boolean) -> Unit,
    onToggleBatteryAlert: (Boolean) -> Unit,
    onViewBackgroundApps: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val bat = snapshot.battery
    val tempStr = bat.temperatureCelsius?.let {
        String.format(Locale("pt", "PT"), "%.1f °C", it)
    } ?: "N/D"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cartão principal com nível real da bateria + corrente/tensão medidas
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                VerticalBatteryGraphic(level = bat.levelPercent / 100f)
                Spacer(modifier = Modifier.width(18.dp))
                Column {
                    Text(
                        text = "${bat.levelPercent}%",
                        style = NexusMonoTextStyles.metricHero.copy(fontSize = 36.sp),
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = bat.statusLineText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textDim
                    )
                    Text(
                        text = bat.detailLineText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }
        }

        // 3 mini cartões reais: Saúde, Ciclos (ou explicação N/D), Temp. real
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Saúde",
                value = bat.healthLabelPt,
                subtitle = "BatteryManager",
                valueColor = if (bat.isHealthy) colors.ok else colors.warn
            )
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Ciclos",
                value = bat.cycleCountText,
                subtitle = bat.cycleCountReason,
                valueColor = colors.text
            )
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Temp.",
                value = tempStr,
                subtitle = "${bat.voltageMv} mV",
                valueColor = colors.text
            )
        }

        // Gráfico de amostras reais de nível % registadas no dispositivo
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            val chartSamples = remember(batterySamples, bat.levelPercent, bat.isCharging) {
                val ordered = batterySamples.reversed()
                if (ordered.isEmpty()) {
                    listOf(
                        BatterySampleEntity(
                            timestampEpochMillis = System.currentTimeMillis(),
                            levelPercent = bat.levelPercent,
                            temperatureCelsius = bat.temperatureCelsius ?: 30f,
                            isCharging = bat.isCharging
                        )
                    )
                } else {
                    ordered
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Histórico medido (${chartSamples.size} amostra(s))",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
                Text(
                    text = "nível % (atual ${bat.levelPercent}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            RealBatteryBarsChart(samples = chartSamples)
        }

        // 3 Toggles reais persistidos em DataStore
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            NexusToggleRow(
                title = "Alerta de armazenamento (< 10 %)",
                subtitle = "Notifica quando o espaço livre desce abaixo de 10 %",
                checked = preferences.storageAlertEnabled,
                onCheckedChange = onToggleStorageAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Alerta de aquecimento",
                subtitle = "Notifica se o estado térmico chegar a Grave ou Crítico",
                checked = preferences.thermalAlertEnabled,
                onCheckedChange = onToggleThermalAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Alerta de bateria degradada",
                subtitle = "Notifica se o BatteryManager reportar degradação ou sobretensão",
                checked = preferences.batteryAlertEnabled,
                onCheckedChange = onToggleBatteryAlert
            )
        }

        // Banner inferior real: apps em fundo / UsageStats
        NexusCard(
            backgroundColor = Color(0xFF1F1B16),
            borderColor = colors.warn.copy(alpha = 0.45f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            onClick = {
                if (!snapshot.hasUsageStatsPermission) {
                    openUsageAccessSettings(context)
                } else {
                    onViewBackgroundApps()
                }
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.warn)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (snapshot.hasUsageStatsPermission) {
                            "${snapshot.highBackgroundAppsCount} app(s) com atividade relevante · ${snapshot.unusedAppsCount} sem uso (>30d)"
                        } else {
                            "Autoriza o acesso ao uso para ver as apps que mais gastam bateria"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (snapshot.hasUsageStatsPermission) "Ver" else "Autorizar",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.warn
                )
            }
        }
    }
}

@Composable
private fun VerticalBatteryGraphic(level: Float) {
    val colors = NexusTheme.colors
    val fillBrush = Brush.verticalGradient(
        colors = listOf(colors.accent2, colors.accent)
    )
    Canvas(modifier = Modifier.size(width = 48.dp, height = 82.dp)) {
        val capWidth = size.width * 0.42f
        val capHeight = 5.dp.toPx()
        val bodyTop = capHeight + 3.dp.toPx()
        val bodyHeight = size.height - bodyTop
        drawRoundRect(
            color = colors.borderStrong,
            topLeft = Offset((size.width - capWidth) / 2f, 0f),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawRoundRect(
            color = colors.borderStrong,
            topLeft = Offset(0f, bodyTop),
            size = Size(size.width, bodyHeight),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
        val inset = 4.dp.toPx()
        val maxFillHeight = bodyHeight - inset * 2
        val currentFillHeight = maxFillHeight * level.coerceIn(0f, 1f)
        drawRoundRect(
            brush = fillBrush,
            topLeft = Offset(inset, bodyTop + inset + (maxFillHeight - currentFillHeight)),
            size = Size(size.width - inset * 2, currentFillHeight),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
        )
    }
}

@Composable
private fun MiniBatteryStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String,
    valueColor: Color
) {
    val colors = NexusTheme.colors
    NexusCard(
        modifier = modifier,
        backgroundColor = colors.surface,
        borderColor = colors.border,
        contentPadding = PaddingValues(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = NexusMonoTextStyles.metricMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RealBatteryBarsChart(samples: List<BatterySampleEntity>) {
    val colors = NexusTheme.colors
    val padded = remember(samples) {
        val list = samples.takeLast(20)
        if (list.size < 20) {
            List(20 - list.size) { null } + list
        } else {
            list
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        padded.forEach { sample ->
            if (sample == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(0.12f)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(colors.surfaceAlt)
                )
            } else {
                val ratio = (sample.levelPercent / 100f).coerceIn(0.08f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(ratio)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (sample.isCharging) colors.accent2 else colors.accent)
                )
            }
        }
    }
}

// ============================================================================
// 3 · LIMPAR (100% Ficheiros Reais via MediaStore + Lixo do Sistema)
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RealCleanScreen(
    snapshot: RealSystemSnapshot,
    cleanReport: CleanScanReport,
    isScanning: Boolean,
    onRefreshScan: () -> Unit,
    buildTrashIntent: (List<ScannedFileCandidate>) -> android.content.IntentSender?,
    onTrashConfirmed: (List<ScannedFileCandidate>, List<String>) -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current

    val selectedCategories = remember {
        mutableStateListOf(
            CleanCategoryType.INSTALLED_APKS,
            CleanCategoryType.LARGE_FILES,
            CleanCategoryType.OLD_DOWNLOADS,
            CleanCategoryType.DUPLICATES
        )
    }

    var previewingCategory by remember { mutableStateOf<CleanCategoryGroup?>(null) }
    var showConfirmTrashSheet by remember { mutableStateOf(false) }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshScan()
    }

    val pendingFilesToTrash = remember(cleanReport, selectedCategories.toList()) {
        cleanReport.categories
            .filter { it.type in selectedCategories }
            .flatMap { it.files }
            .distinctBy { it.contentUri }
    }
    val pendingEmptyFolders = remember(cleanReport, selectedCategories.toList()) {
        cleanReport.categories
            .filter { it.type == CleanCategoryType.EMPTY_FOLDERS && it.type in selectedCategories }
            .flatMap { it.emptyFoldersPaths }
    }
    val totalSelectedBytes = pendingFilesToTrash.sumOf { it.sizeBytes }

    val trashIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onTrashConfirmed(pendingFilesToTrash, pendingEmptyFolders)
            Toast.makeText(
                context,
                "Movido para o Lixo do sistema com sucesso!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Folha inferior de pré-visualização obrigatória de uma categoria ou antes de mover para o Lixo
    if (previewingCategory != null || showConfirmTrashSheet) {
        val group = previewingCategory
        val filesList = group?.files ?: pendingFilesToTrash
        val titleText = group?.type?.titlePt ?: "Pré-visualização antes de mover para o Lixo"

        ModalBottomSheet(
            onDismissRequest = {
                previewingCategory = null
                showConfirmTrashSheet = false
            },
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text
                )
                Text(
                    text = "Verifica os ficheiros reais encontrados no teu telemóvel antes de confirmar:",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filesList.isEmpty() && (group?.emptyFoldersPaths.isNullOrEmpty() && pendingEmptyFolders.isEmpty())) {
                        Text(
                            text = "Nenhum ficheiro encontrado nesta categoria.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted
                        )
                    } else {
                        filesList.forEach { file ->
                            NexusCard(
                                backgroundColor = colors.surfaceAlt,
                                borderColor = colors.border,
                                contentPadding = PaddingValues(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = colors.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = file.relativePath.ifBlank { "Armazenamento partilhado" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = NexusFormatters.formatBytes(file.sizeBytes),
                                        style = NexusMonoTextStyles.metricSmall,
                                        color = colors.accent2
                                    )
                                }
                            }
                        }
                        val folders = group?.emptyFoldersPaths ?: pendingEmptyFolders
                        folders.forEach { folderPath ->
                            NexusCard(
                                backgroundColor = colors.surfaceAlt,
                                borderColor = colors.border,
                                contentPadding = PaddingValues(10.dp)
                            ) {
                                Text(
                                    text = "Pasta vazia: $folderPath",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textDim
                                )
                            }
                        }
                    }
                }

                if (showConfirmTrashSheet && (pendingFilesToTrash.isNotEmpty() || pendingEmptyFolders.isNotEmpty())) {
                    NexusPrimaryGradientButton(
                        text = "Confirmar e Mover para o Lixo (${NexusFormatters.formatBytes(totalSelectedBytes)})",
                        onClick = {
                            showConfirmTrashSheet = false
                            if (pendingFilesToTrash.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val sender = buildTrashIntent(pendingFilesToTrash)
                                if (sender != null) {
                                    trashIntentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                }
                            } else {
                                onTrashConfirmed(pendingFilesToTrash, pendingEmptyFolders)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val st = snapshot.storage
    val usedGb = NexusFormatters.formatGigabytesInt(st.usedBytes)
    val totalGb = NexusFormatters.formatGigabytesInt(st.totalBytes)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tudo vai para o Lixo do sistema. Podes restaurar durante 30 dias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefreshScan) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent2
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Atualizar análise",
                            tint = colors.accent2
                        )
                    }
                }
            }

            // Aviso e pedido contextual de permissão se ainda não concedida
            if (!cleanReport.hasMediaPermission) {
                NexusCard(
                    backgroundColor = colors.surfaceAlt,
                    borderColor = colors.accent2,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text(
                        text = "Permissão necessária para analisar ficheiros",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Para procurar ficheiros grandes, duplicados e capturas antigas no teu telemóvel, concede acesso de leitura.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    NexusPrimaryGradientButton(
                        text = "Permitir leitura de ficheiros",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                mediaPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_MEDIA_IMAGES,
                                        Manifest.permission.READ_MEDIA_VIDEO,
                                        Manifest.permission.READ_MEDIA_AUDIO
                                    )
                                )
                            } else {
                                mediaPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Cartão de Armazenamento Real com barra segmentada proporcional
            NexusCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                contentPadding = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Armazenamento real",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                    Text(
                        text = "$usedGb / $totalGb",
                        style = NexusMonoTextStyles.metricSmall,
                        color = colors.text
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                val totalF = st.totalBytes.coerceAtLeast(1L).toFloat()
                val appsW = (st.appsAndSystemBytes / totalF).coerceIn(0.04f, 0.85f)
                val photosW = (st.photosBytes / totalF).coerceIn(0.01f, 0.50f)
                val videosW = (st.videosBytes / totalF).coerceIn(0.01f, 0.50f)
                val cleanW = (cleanReport.totalCleanableBytes / totalF).coerceIn(0.01f, 0.30f)
                val freeW = (st.freeBytes / totalF).coerceIn(0.05f, 0.90f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surfaceAlt)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(appsW)
                            .fillMaxHeight()
                            .background(colors.accent)
                    )
                    Box(
                        modifier = Modifier
                            .weight(photosW)
                            .fillMaxHeight()
                            .background(colors.accent2)
                    )
                    Box(
                        modifier = Modifier
                            .weight(videosW)
                            .fillMaxHeight()
                            .background(colors.ok)
                    )
                    Box(
                        modifier = Modifier
                            .weight(cleanW)
                            .fillMaxHeight()
                            .background(colors.warn)
                    )
                    Spacer(modifier = Modifier.weight(freeW))
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StorageLegendDot("Apps/Sist.", colors.accent)
                    StorageLegendDot("Fotos (${NexusFormatters.formatBytes(st.photosBytes)})", colors.accent2)
                    StorageLegendDot("Vídeos (${NexusFormatters.formatBytes(st.videosBytes)})", colors.ok)
                    StorageLegendDot("Limpar", colors.warn)
                }
            }

            // Lista agrupada das 6 categorias reais (tocar abre pré-visualização, tocar na checkbox seleciona)
            NexusCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                contentPadding = PaddingValues(0.dp)
            ) {
                cleanReport.categories.forEachIndexed { index, group ->
                    val isChecked = group.type in selectedCategories
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                previewingCategory = group
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.clickable {
                                    if (isChecked) {
                                        selectedCategories.remove(group.type)
                                    } else {
                                        selectedCategories.add(group.type)
                                    }
                                }
                            ) {
                                NexusCyanCheckbox(checked = isChecked)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = group.type.titlePt,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                    color = if (isChecked) colors.text else colors.textDim
                                )
                                Text(
                                    text = "${group.subtitlePt} · tocar p/ pré-visualizar",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                        }
                        Text(
                            text = NexusFormatters.formatBytes(group.totalSizeBytes),
                            style = NexusMonoTextStyles.metricSmall,
                            color = if (group.totalSizeBytes > 0) colors.text else colors.textMuted
                        )
                    }
                    if (index < cleanReport.categories.lastIndex) {
                        HorizontalDivider(color = colors.border)
                    }
                }
            }

            // Atalhos de sistema: Caches de outras apps + Modo Avançado (MANAGE_EXTERNAL_STORAGE no flavor full)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { openSystemClearCacheAction(context) },
                    shape = NexusButtonShape,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Caches de apps", color = colors.text, fontSize = 12.sp)
                }
                if (BuildConfig.IS_FULL_FLAVOR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !cleanReport.hasManageStoragePermission) {
                    OutlinedButton(
                        onClick = { openManageAllFilesSettings(context) },
                        shape = NexusButtonShape,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Modo avançado (APK/Pastas)", color = colors.accent2, fontSize = 12.sp)
                    }
                }
            }
        }

        // Barra fixa inferior «Mover para o Lixo · X MB/GB» (abre pré-visualização obrigatória antes de confirmar)
        Box(modifier = Modifier.padding(vertical = 10.dp)) {
            val hasSelection = pendingFilesToTrash.isNotEmpty() || pendingEmptyFolders.isNotEmpty()
            NexusPrimaryGradientButton(
                text = "Mover para o Lixo · ${NexusFormatters.formatBytes(totalSelectedBytes)}",
                enabled = hasSelection,
                onClick = {
                    showConfirmTrashSheet = true
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StorageLegendDot(label: String, color: Color) {
    val colors = NexusTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.textDim
        )
    }
}

@Composable
private fun NexusCyanCheckbox(checked: Boolean) {
    val colors = NexusTheme.colors
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (checked) colors.accent2 else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (checked) colors.accent2 else colors.borderStrong,
                shape = RoundedCornerShape(5.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color(0xFF0C0F16),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ============================================================================
// 4 · JOGOS + «PREPARAR BOOST» (Apps/Jogos Reais Instalados no Telemóvel)
// ============================================================================
@Composable
private fun RealGamesScreen(
    snapshot: RealSystemSnapshot,
    boostSession: BoostSessionState,
    hasDndPermission: Boolean,
    onStartBoost: (String, String, String, Boolean, Boolean, Boolean) -> Unit,
    onOpenActiveSession: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current

    // Mostra primeiro os jogos detetados (CATEGORY_GAME); se o telemóvel tiver menos de 6 jogos, completa com as apps instaladas reais!
    val realTiles = remember(snapshot.installedApps) {
        val detectedGames = snapshot.installedApps.filter { it.isGame }
        val others = snapshot.installedApps.filter { !it.isGame }
        (detectedGames + others).take(6)
    }

    var selectedApp by remember(realTiles) {
        mutableStateOf(realTiles.firstOrNull())
    }
    var dndEnabled by remember { mutableStateOf(true) }
    var stopBackgroundApps by remember { mutableStateOf(true) }
    var thermalWarning by remember { mutableStateOf(true) }

    val tempText = snapshot.battery.temperatureCelsius?.let {
        String.format(Locale("pt", "PT"), "bateria %.1f °C", it)
    } ?: "bateria N/D"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (boostSession.isActive) {
                NexusCard(
                    backgroundColor = colors.surfaceAlt,
                    borderColor = colors.ok,
                    onClick = onOpenActiveSession
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "● Boost ativo: ${boostSession.gameName} (${boostSession.formattedElapsed})",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ok
                        )
                        Text("Abrir", style = NexusMonoTextStyles.badge, color = colors.accent2)
                    }
                }
            }

            if (realTiles.isEmpty()) {
                NexusCard(backgroundColor = colors.surface, borderColor = colors.border) {
                    Text(
                        text = "A carregar aplicações instaladas no telemóvel...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textDim
                    )
                }
            } else {
                realTiles.chunked(3).forEach { rowApps ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowApps.forEach { app ->
                            val isSelected = app.packageName == selectedApp?.packageName
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedApp = app },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(colors.surface)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) colors.accent else colors.border,
                                            shape = RoundedCornerShape(18.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppIconOrMonogram(
                                        packageName = app.packageName,
                                        monogram = app.monogram,
                                        iconSizeDp = 42
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) colors.text else colors.textDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        repeat(3 - rowApps.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Folha inferior «Preparar boost» com dados térmicos reais
        val target = selectedApp
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.borderStrong,
            contentPadding = PaddingValues(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.borderStrong)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceAlt)
                        .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (target != null) {
                        AppIconOrMonogram(
                            packageName = target.packageName,
                            monogram = target.monogram,
                            iconSizeDp = 28
                        )
                    } else {
                        Text("JG", style = MaterialTheme.typography.labelLarge, color = colors.text)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "PREPARAR BOOST",
                        style = NexusMonoTextStyles.badge,
                        color = colors.accent
                    )
                    Text(
                        text = target?.appName ?: "Seleciona uma app",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            NexusToggleRow(
                title = "Não incomodar",
                subtitle = if (hasDndPermission) {
                    "Silencia notificações durante o jogo"
                } else {
                    "Toque para autorizar acesso ao Não incomodar"
                },
                checked = dndEnabled && hasDndPermission,
                onCheckedChange = { want ->
                    if (!hasDndPermission) {
                        openDndPolicySettings(context)
                    } else {
                        dndEnabled = want
                    }
                }
            )
            NexusToggleRow(
                title = "Parar apps em fundo",
                subtitle = if (BuildConfig.IS_FULL_FLAVOR) {
                    "Só as que o Android permite (killBackgroundProcesses)"
                } else {
                    "Disponível na variante Full"
                },
                checked = stopBackgroundApps && BuildConfig.IS_FULL_FLAVOR,
                onCheckedChange = { stopBackgroundApps = it }
            )
            NexusToggleRow(
                title = "Aviso de aquecimento",
                subtitle = "Notifica se chegar a Grave",
                checked = thermalWarning,
                onCheckedChange = { thermalWarning = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Barra de estado térmico real medido agora
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.bgRaised)
                    .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (snapshot.thermal.level.severityRank <= 0) colors.ok else colors.warn
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Estado térmico: ${snapshot.thermal.level.labelPt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text
                    )
                }
                Text(
                    text = tempText,
                    style = NexusMonoTextStyles.metricSmall.copy(fontSize = 12.sp),
                    color = colors.textDim
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            NexusPrimaryGradientButton(
                text = "Boost e jogar",
                enabled = target != null,
                onClick = {
                    if (target != null) {
                        onStartBoost(
                            target.packageName,
                            target.appName,
                            target.monogram,
                            dndEnabled && hasDndPermission,
                            stopBackgroundApps,
                            false // Abre primeiro o ecrã de sessão ativa com opção de abrir o jogo externo
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun AppIconOrMonogram(
    packageName: String,
    monogram: String,
    iconSizeDp: Int
) {
    val context = LocalContext.current
    val colors = NexusTheme.colors
    val bitmap = remember(packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToBitmap(drawable, 128)
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = monogram,
            modifier = Modifier
                .size(iconSizeDp.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Text(
            text = monogram,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.textDim
        )
    }
}

private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}

// ============================================================================
// 5 · SESSÃO ATIVA + NOTIFICAÇÃO (Cronómetro Real + Gráfico Térmico Real 60s)
// ============================================================================
@Composable
private fun RealActiveBoostScreen(
    snapshot: RealSystemSnapshot,
    boostSession: BoostSessionState,
    onStopBoost: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val liveTemp = boostSession.currentTempCelsius ?: snapshot.battery.temperatureCelsius
    val tempLabel = liveTemp?.let {
        String.format(Locale("pt", "PT"), "%.1f °C", it)
    } ?: "N/D"
    val dndStatusText = if (boostSession.dndEnabled) "Não incomodar ligado" else "Não incomodar desligado"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cartão da notificação em primeiro plano ativa
        NexusCard(
            backgroundColor = colors.surfaceAlt,
            borderColor = colors.borderStrong,
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GaugeShieldLogo(size = 18.dp, withContainer = false)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Nexus Guard · agora (Serviço ativo)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Boost ativo · ${boostSession.gameName.ifBlank { "Sessão" }}",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = boostSession.formattedElapsed,
                    style = NexusMonoTextStyles.metricSmall,
                    color = colors.textDim
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Bateria $tempLabel · Térmico: ${boostSession.currentThermalLevel.labelPt} · $dndStatusText",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "TERMINAR",
                    style = NexusMonoTextStyles.badge.copy(fontSize = 12.sp),
                    color = colors.accent2,
                    modifier = Modifier.clickable(onClick = onStopBoost)
                )
                if (boostSession.gamePackage.isNotBlank()) {
                    Text(
                        text = "ABRIR JOGO",
                        style = NexusMonoTextStyles.badge.copy(fontSize = 12.sp),
                        color = colors.ok,
                        modifier = Modifier.clickable {
                            val launch = context.packageManager.getLaunchIntentForPackage(boostSession.gamePackage)
                            if (launch != null) {
                                context.startActivity(launch)
                            }
                        }
                    )
                }
            }
        }

        // Cronómetro central ao vivo
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.ok)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "BOOST ATIVO",
                    style = NexusMonoTextStyles.badge,
                    color = colors.ok
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = boostSession.gameName.ifBlank { "Jogo em execução" },
                style = MaterialTheme.typography.titleLarge,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = boostSession.formattedElapsed,
                style = NexusMonoTextStyles.metricHero.copy(fontSize = 44.sp),
                color = colors.text
            )
        }

        // Gráfico térmico de 60 s ao vivo com dados reais da bateria
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Temperatura da bateria · 60 s",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
                Text(
                    text = tempLabel,
                    style = NexusMonoTextStyles.metricMedium,
                    color = if ((liveTemp ?: 30f) >= 38f) colors.warn else colors.ok
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            RealLiveThermal60sBars(tempHistory = snapshot.tempHistory60s)
        }

        // Grelha 2x2 real da sessão
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Pior estado térmico",
                value = boostSession.worstThermalLevel.labelPt
            )
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Bateria gasta",
                value = "${boostSession.batteryUsedPercent} %"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Não incomodar",
                value = if (boostSession.dndEnabled) "Ligado" else "Desligado"
            )
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Apps paradas",
                value = boostSession.appsStoppedCount.toString()
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 50.dp)
                .clip(NexusButtonShape)
                .background(colors.surface)
                .border(1.dp, colors.borderStrong, NexusButtonShape)
                .clickable(role = Role.Button, onClick = onStopBoost)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Terminar boost",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
        }
    }
}

@Composable
private fun RealLiveThermal60sBars(tempHistory: List<Float>) {
    val colors = NexusTheme.colors
    val bars = remember(tempHistory) {
        val list = tempHistory.takeLast(28)
        if (list.size < 28) {
            List(28 - list.size) { list.firstOrNull() ?: 30f } + list
        } else {
            list
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { tempC ->
            // Mapeia 20 °C..50 °C para 0.20..1.00
            val normalized = ((tempC - 18f) / 32f).coerceIn(0.20f, 1.0f)
            val barColor = when {
                tempC >= 42f -> colors.danger
                tempC >= 36f -> colors.warn
                else -> colors.ok
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(normalized)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(barColor.copy(alpha = 0.85f))
            )
        }
    }
}

@Composable
private fun SessionMetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    val colors = NexusTheme.colors
    NexusCard(
        modifier = modifier,
        backgroundColor = colors.surface,
        borderColor = colors.border,
        contentPadding = PaddingValues(14.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.text
        )
    }
}

// ============================================================================
// SEPARADOR SISTEMA (Temperaturas ao vivo 60s + RAM + Apps Instaladas Reais)
// ============================================================================
@Composable
private fun RealSystemScreen(
    snapshot: RealSystemSnapshot,
    onRefresh: () -> Unit,
    onStopPermittedBackground: ((Int) -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val tempLabel = snapshot.battery.temperatureCelsius?.let {
        String.format(Locale("pt", "PT"), "%.1f °C", it)
    } ?: "N/D"
    val availRamGb = NexusFormatters.formatBytes(snapshot.availableRamBytes)
    val totalRamGb = NexusFormatters.formatBytes(snapshot.totalRamBytes)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Gráfico térmico de 60 s ao vivo
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Temperatura da bateria · 60 s ao vivo",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
                Text(
                    text = "$tempLabel · ${snapshot.thermal.level.labelPt}",
                    style = NexusMonoTextStyles.metricSmall,
                    color = colors.ok
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Margem térmica: ${snapshot.thermal.headroomText} · CPU: bloqueado pelo Android sem root",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.height(10.dp))
            RealLiveThermal60sBars(tempHistory = snapshot.tempHistory60s)
        }

        // Memória RAM Real (ActivityManager.MemoryInfo)
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Memória RAM disponível",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Text(
                        text = "$availRamGb livres de $totalRamGb (ActivityManager.MemoryInfo)",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                }
                if (BuildConfig.IS_FULL_FLAVOR) {
                    Text(
                        text = "Limpar fundo",
                        style = NexusMonoTextStyles.badge,
                        color = colors.accent2,
                        modifier = Modifier.clickable {
                            onStopPermittedBackground { count ->
                                Toast.makeText(
                                    context,
                                    "killBackgroundProcesses executado em $count apps em fundo.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }

        // Permissão UsageStats se ainda não concedida
        if (!snapshot.hasUsageStatsPermission) {
            NexusCard(
                backgroundColor = colors.surfaceAlt,
                borderColor = colors.warn,
                contentPadding = PaddingValues(14.dp)
            ) {
                Text(
                    text = "Permissão de Acesso ao Uso (PACKAGE_USAGE_STATS)",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Concede acesso para medir há quantos dias não usas cada app e o espaço exato ocupado (StorageStatsManager).",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
                Spacer(modifier = Modifier.height(10.dp))
                NexusPrimaryGradientButton(
                    text = "Conceder Acesso ao Uso",
                    onClick = { openUsageAccessSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Lista de Apps instaladas reais com ações Parar, Desinstalar e Bateria
        Text(
            text = "Apps instaladas (${snapshot.installedApps.size}) · ordenadas por tamanho",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textDim
        )

        snapshot.installedApps.take(25).forEach { app ->
            NexusCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                contentPadding = PaddingValues(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        AppIconOrMonogram(
                            packageName = app.packageName,
                            monogram = app.monogram,
                            iconSizeDp = 36
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val usageNote = if (snapshot.hasUsageStatsPermission) {
                                if (app.isUnused30Days) "Sem uso há ${app.daysSinceUse} d" else "Usada há ${app.daysSinceUse} d"
                            } else {
                                app.packageName
                            }
                            Text(
                                text = "${NexusFormatters.formatBytes(app.totalSizeBytes)} · $usageNote",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (app.isUnused30Days) colors.warn else colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Parar / Bateria",
                        style = NexusMonoTextStyles.badge,
                        color = colors.accent2,
                        modifier = Modifier.clickable {
                            openAppDetailsSettings(context, app.packageName)
                        }
                    )
                    Text(
                        text = "Desinstalar",
                        style = NexusMonoTextStyles.badge,
                        color = colors.danger,
                        modifier = Modifier.clickable {
                            requestUninstallPackage(context, app.packageName)
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpenHistory,
                shape = NexusButtonShape,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Histórico", color = colors.text)
            }
            OutlinedButton(
                onClick = onOpenSettings,
                shape = NexusButtonShape,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
            ) {
                Text("Definições", color = colors.text)
            }
        }
    }
}

// ============================================================================
// HISTÓRICO REAL (Room + Restauro do Lixo + Exportação PDF)
// ============================================================================
@Composable
private fun RealHistoryScreen(
    entries: List<HistoryEntity>,
    buildRestoreIntent: (HistoryEntity) -> android.content.IntentSender?,
    onRestoreConfirmed: (Long) -> Unit,
    onExportPdf: (Uri, (Boolean) -> Unit) -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    var pendingRestoreId by remember { mutableStateOf<Long?>(null) }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        val id = pendingRestoreId
        if (res.resultCode == Activity.RESULT_OK && id != null) {
            onRestoreConfirmed(id)
            Toast.makeText(context, "Ficheiros restaurados do Lixo do sistema!", Toast.LENGTH_SHORT).show()
        }
        pendingRestoreId = null
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onExportPdf(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "Relatório PDF exportado com sucesso!" else "Erro ao exportar PDF.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NexusPrimaryGradientButton(
            text = "Exportar Histórico em PDF",
            leadingIcon = Icons.Outlined.PictureAsPdf,
            onClick = {
                pdfExportLauncher.launch("NexusGuard-Historico.pdf")
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (entries.isEmpty()) {
            NexusCard(
                backgroundColor = colors.surface,
                borderColor = colors.border
            ) {
                Text(
                    text = "Histórico vazio",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Todas as análises, limpezas e sessões de Boost que executares no telemóvel serão gravadas aqui na base de dados local (Room).",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
            }
        } else {
            entries.forEach { entry ->
                NexusCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = NexusFormatters.formatRelativeTimePt(entry.timestampEpochMillis),
                            style = NexusMonoTextStyles.metricSmall,
                            color = colors.textMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                    if (entry.canRestore && !entry.isRestored) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val sender = buildRestoreIntent(entry)
                                if (sender != null) {
                                    pendingRestoreId = entry.id
                                    restoreLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                }
                            },
                            shape = NexusButtonShape
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestoreFromTrash,
                                contentDescription = null,
                                tint = colors.accent2,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restaurar do Lixo do sistema", color = colors.text)
                        }
                    } else if (entry.isRestored) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("✓ Restaurado do Lixo do sistema", style = NexusMonoTextStyles.badge, color = colors.ok)
                    }
                }
            }
        }
    }
}

// ============================================================================
// DEFINIÇÕES REAIS (Tema, Notificações, Agendamento, Permissões e Licença)
// ============================================================================
@Composable
private fun RealSettingsScreen(
    preferences: UserPreferencesState,
    snapshot: RealSystemSnapshot,
    cleanReport: CleanScanReport,
    hasDndPermission: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleWeeklyScan: (Boolean) -> Unit,
    onToggleStorageAlert: (Boolean) -> Unit,
    onToggleThermalAlert: (Boolean) -> Unit,
    onToggleBatteryAlert: (Boolean) -> Unit,
    onReopenOnboarding: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border
        ) {
            NexusToggleRow(
                title = "Tema escuro (NexusTheme)",
                subtitle = "Ativo por omissão (#0C0F16). O tema claro é opcional.",
                checked = preferences.darkThemeEnabled,
                onCheckedChange = onToggleDarkTheme
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Análise semanal agendada (WorkManager)",
                subtitle = "Executa verificação em segundo plano 1x por semana",
                checked = preferences.weeklyScanEnabled,
                onCheckedChange = onToggleWeeklyScan
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Notificação: Armazenamento < 10 %",
                subtitle = "Canal dedicado de alerta de armazenamento",
                checked = preferences.storageAlertEnabled,
                onCheckedChange = onToggleStorageAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Notificação: Telemóvel quente",
                subtitle = "Avisa quando o estado térmico chega a Grave ou Crítico",
                checked = preferences.thermalAlertEnabled,
                onCheckedChange = onToggleThermalAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Notificação: Bateria degradada",
                subtitle = "Avisa se a saúde da bateria deixar de ser Boa",
                checked = preferences.batteryAlertEnabled,
                onCheckedChange = onToggleBatteryAlert
            )
        }

        // Painel de Permissões Concedidas em tempo real
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border
        ) {
            Text(
                text = "Estado das Permissões no Telemóvel",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(8.dp))
            PermissionStatusRow(
                label = "Acesso ao Uso (PACKAGE_USAGE_STATS)",
                granted = snapshot.hasUsageStatsPermission,
                onAction = { openUsageAccessSettings(context) }
            )
            PermissionStatusRow(
                label = "Leitura de Media (MediaStore)",
                granted = cleanReport.hasMediaPermission,
                onAction = { openAppDetailsSettings(context, context.packageName) }
            )
            PermissionStatusRow(
                label = "Política Não Incomodar (Modo Jogo)",
                granted = hasDndPermission,
                onAction = { openDndPolicySettings(context) }
            )
            if (BuildConfig.IS_FULL_FLAVOR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PermissionStatusRow(
                    label = "Todos os ficheiros (MANAGE_EXTERNAL_STORAGE)",
                    granted = cleanReport.hasManageStoragePermission,
                    onAction = { openManageAllFilesSettings(context) }
                )
            }
        }

        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border
        ) {
            Text(
                text = "Distribuição, Privacidade e Licença",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Variante ativa: ${BuildConfig.FLAVOR_LABEL} (${BuildConfig.VERSION_NAME})",
                style = NexusMonoTextStyles.metricSmall,
                color = colors.accent2
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "100 % local: sem analytics, sem anúncios e sem permissão de INTERNET. Fontes: JetBrains Mono (OFL) e Roboto Flex.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )
        }

        OutlinedButton(
            onClick = onReopenOnboarding,
            shape = NexusButtonShape,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = colors.accent2,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Abrir Onboarding com os Princípios",
                color = colors.text
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    label: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDim,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (granted) "Concedida" else "Conceder",
            style = NexusMonoTextStyles.badge,
            color = if (granted) colors.ok else colors.accent2,
            modifier = Modifier.clickable(onClick = onAction)
        )
    }
}

@Composable
private fun NexusToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = NexusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = colors.text
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textDim,
                uncheckedTrackColor = colors.surfaceAlt,
                uncheckedBorderColor = colors.borderStrong
            )
        )
    }
}

// ============================================================================
// INTENTS NATIVOS DO ANDROID
// ============================================================================
private fun openUsageAccessSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
    }
}

private fun openDndPolicySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
    }
}

private fun openManageAllFilesSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: Exception) {
            }
        }
    }
}

private fun openSystemClearCacheAction(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.startActivity(Intent(StorageManager.ACTION_CLEAR_APP_CACHE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } else {
            context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
        }
    }
}

private fun openAppDetailsSettings(context: Context, packageName: String) {
    try {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (_: Exception) {
    }
}

private fun requestUninstallPackage(context: Context, packageName: String) {
    try {
        context.startActivity(
            Intent(
                Intent.ACTION_DELETE,
                Uri.parse("package:$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (_: Exception) {
    }
}
