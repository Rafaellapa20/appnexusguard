package com.nexusguard.android.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexusguard.android.ui.battery.BatteryScreen
import com.nexusguard.android.ui.clean.CleanScreen
import com.nexusguard.android.ui.components.NexusTopBar
import com.nexusguard.android.ui.components.rememberScaledAnimationDuration
import com.nexusguard.android.ui.games.ActiveBoostScreen
import com.nexusguard.android.ui.games.GamesScreen
import com.nexusguard.android.ui.history.HistoryScreen
import com.nexusguard.android.ui.home.HomeScreen
import com.nexusguard.android.ui.navigation.NexusRoutes
import com.nexusguard.android.ui.navigation.NexusTab
import com.nexusguard.android.ui.onboarding.OnboardingScreen
import com.nexusguard.android.ui.settings.AppUpdatePromptDialog
import com.nexusguard.android.ui.settings.SettingsScreen
import com.nexusguard.android.ui.system.SystemScreen
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusTheme
import java.util.Locale

@Composable
fun NexusGuardMainScreen(
    viewModel: MainViewModel
) {
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val boostSession by viewModel.boostSession.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val openDeepCleanEvent by viewModel.openDeepCleanEvent.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (updateState.showUpdateDialog) {
        AppUpdatePromptDialog(
            updateState = updateState,
            onDownloadAndInstall = viewModel::startAppUpdateDownload,
            onDownloadViaSystemManager = viewModel::downloadUpdateViaSystemManager,
            onInstallReadyApk = viewModel::installDownloadedUpdate,
            onOpenBrowser = viewModel::openUpdateInBrowser,
            onDismiss = viewModel::dismissUpdateDialog
        )
    }

    if (!preferences.onboardingCompleted) {
        OnboardingScreen(onFinishOnboarding = viewModel::completeOnboarding)
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NexusTab.HOME.route
    val currentTab = NexusTab.entries.find { it.route == currentRoute }
    val colors = NexusTheme.colors

    LaunchedEffect(openDeepCleanEvent) {
        if (openDeepCleanEvent) {
            viewModel.consumeOpenDeepCleanEvent()
            if (currentRoute != NexusTab.CLEAN.route) {
                navController.navigate(NexusTab.CLEAN.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

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
        val navFadeDuration = rememberScaledAnimationDuration(175)
        NavHost(
            navController = navController,
            startDestination = NexusTab.HOME.route,
            enterTransition = { fadeIn(animationSpec = tween(navFadeDuration)) },
            exitTransition = { fadeOut(animationSpec = tween(navFadeDuration)) },
            popEnterTransition = { fadeIn(animationSpec = tween(navFadeDuration)) },
            popExitTransition = { fadeOut(animationSpec = tween(navFadeDuration)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(NexusTab.HOME.route) {
                val homeUiState by viewModel.homeUiState.collectAsStateWithLifecycle()
                HomeScreen(
                    uiState = homeUiState,
                    onStartDeepClean = {
                        viewModel.requestOpenDeepCleanFromShortcut()
                    },
                    onRunAnalysis = viewModel::runOneClickAnalysis,
                    onApplySafeFixes = viewModel::applyAutomaticFixes,
                    onOpenBattery = { navController.navigate(NexusTab.BATTERY.route) },
                    onOpenClean = { navController.navigate(NexusTab.CLEAN.route) },
                    onOpenSystem = { navController.navigate(NexusTab.SYSTEM.route) },
                    onOpenHistory = { navController.navigate(NexusRoutes.HISTORY) }
                )
            }
            composable(NexusTab.CLEAN.route) {
                val cleanUiState by viewModel.cleanUiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    viewModel.onEnterCleanTab()
                }
                CleanScreen(
                    uiState = cleanUiState,
                    onRefreshScan = { viewModel.refreshCleanScan(force = true) },
                    onCancelScan = viewModel::cancelCleanScan,
                    onAppCacheCleared = viewModel::onAppCacheClearFinished,
                    buildTrashIntent = viewModel::buildMoveToTrashIntentSender,
                    onTrashConfirmed = viewModel::onTrashRequestConfirmed,
                    onDismissCompletion = viewModel::dismissCompletionScreen,
                    buildRestoreCompletionIntent = viewModel::buildRestoreFromCompletionIntentSender,
                    onRestoreConfirmed = viewModel::onRestoreConfirmed
                )
            }
            composable(NexusTab.GAMES.route) {
                val gamesUiState by viewModel.gamesUiState.collectAsStateWithLifecycle()
                GamesScreen(
                    uiState = gamesUiState,
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
                val batteryUiState by viewModel.batteryUiState.collectAsStateWithLifecycle()
                BatteryScreen(
                    uiState = batteryUiState,
                    onToggleStorageAlert = viewModel::setStorageAlert,
                    onToggleThermalAlert = viewModel::setThermalAlert,
                    onToggleBatteryAlert = viewModel::setBatteryAlert,
                    onActivateNexusSaver = viewModel::activateNexusBatterySaver,
                    onRestoreNexusSaver = viewModel::restoreNexusBatterySaver,
                    onSetAutoBatterySaver = viewModel::setAutoBatterySaver,
                    onSetChargeLimitAlarm = viewModel::setChargeLimitAlarm,
                    onToggleDarkTheme = viewModel::setDarkTheme,
                    onViewBackgroundApps = {
                        navController.navigate(NexusTab.SYSTEM.route)
                    }
                )
            }
            composable(NexusTab.SYSTEM.route) {
                val systemUiState by viewModel.systemUiState.collectAsStateWithLifecycle()
                SystemScreen(
                    uiState = systemUiState,
                    onStopPermittedBackground = viewModel::stopBackgroundAppsManual,
                    onOpenSettings = { navController.navigate(NexusRoutes.SETTINGS) },
                    onOpenHistory = { navController.navigate(NexusRoutes.HISTORY) }
                )
            }
            composable(NexusRoutes.ACTIVE_BOOST) {
                val gamesUiState by viewModel.gamesUiState.collectAsStateWithLifecycle()
                ActiveBoostScreen(
                    uiState = gamesUiState,
                    onStopBoost = {
                        viewModel.stopBoost()
                        navController.popBackStack()
                    }
                )
            }
            composable(NexusRoutes.HISTORY) {
                val allHistory by viewModel.allHistory.collectAsStateWithLifecycle()
                HistoryScreen(
                    entries = allHistory,
                    buildRestoreIntent = viewModel::buildRestoreFromTrashIntentSender,
                    onRestoreConfirmed = viewModel::onRestoreConfirmed,
                    onExportPdf = viewModel::exportHistoryPdf
                )
            }
            composable(NexusRoutes.SETTINGS) {
                val systemUiState by viewModel.systemUiState.collectAsStateWithLifecycle()
                val cleanUiState by viewModel.cleanUiState.collectAsStateWithLifecycle()
                val hasDndPermission by viewModel.hasDndPermission.collectAsStateWithLifecycle()
                SettingsScreen(
                    preferences = preferences,
                    hasUsageStatsPermission = systemUiState.hasUsageStatsPermission,
                    hasMediaPermission = cleanUiState.cleanReport.hasMediaPermission,
                    hasManageStoragePermission = cleanUiState.cleanReport.hasManageStoragePermission,
                    hasDndPermission = hasDndPermission,
                    updateState = updateState,
                    onCheckUpdates = viewModel::checkForUpdatesManual,
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
                        imageVector = NexusIcons.ArrowBack,
                        contentDescription = "Voltar",
                        tint = colors.text
                    )
                }
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
                        imageVector = NexusIcons.Settings,
                        contentDescription = "Definições",
                        tint = colors.textDim
                    )
                }
            }
        }
        HorizontalDivider(color = colors.border)
    }
}
