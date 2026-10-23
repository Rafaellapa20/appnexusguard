package com.nexusguard.android.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.preferences.UserPreferencesState
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

@Composable
fun NexusGuardMainScreen(
    preferences: UserPreferencesState,
    onCompleteOnboarding: () -> Unit,
    onReopenOnboarding: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit
) {
    if (!preferences.onboardingCompleted) {
        OnboardingScreen(onFinishOnboarding = onCompleteOnboarding)
        return
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NexusTab.HOME.route
    val currentTab = NexusTab.entries.find { it.route == currentRoute }
    val colors = NexusTheme.colors

    val showBottomBar = currentRoute in NexusTab.entries.map { it.route }

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
                    // O ecrã de sessão ativa tem o cartão de notificação no topo como na imagem
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
                HomeScreenPreview(
                    onOpenBattery = { navController.navigate(NexusTab.BATTERY.route) },
                    onOpenClean = { navController.navigate(NexusTab.CLEAN.route) },
                    onOpenSystem = { navController.navigate(NexusTab.SYSTEM.route) },
                    onOpenHistory = { navController.navigate(NexusRoutes.HISTORY) }
                )
            }
            composable(NexusTab.CLEAN.route) {
                CleanScreenPreview()
            }
            composable(NexusTab.GAMES.route) {
                GamesScreenPreview(
                    onStartBoost = {
                        navController.navigate(NexusRoutes.ACTIVE_BOOST)
                    }
                )
            }
            composable(NexusTab.BATTERY.route) {
                BatteryScreenPreview(
                    onViewBackgroundApps = {
                        navController.navigate(NexusTab.SYSTEM.route)
                    }
                )
            }
            composable(NexusTab.SYSTEM.route) {
                SystemScreenPreview(
                    onOpenSettings = {
                        navController.navigate(NexusRoutes.SETTINGS)
                    },
                    onOpenHistory = {
                        navController.navigate(NexusRoutes.HISTORY)
                    }
                )
            }
            composable(NexusRoutes.ACTIVE_BOOST) {
                ActiveBoostSessionScreenPreview(
                    onStopBoost = {
                        navController.popBackStack()
                    }
                )
            }
            composable(NexusRoutes.HISTORY) {
                HistoryScreenPreview()
            }
            composable(NexusRoutes.SETTINGS) {
                Phase1SettingsScreen(
                    darkThemeEnabled = preferences.darkThemeEnabled,
                    onToggleDarkTheme = onToggleDarkTheme,
                    onReopenOnboarding = onReopenOnboarding
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
// 1 · INÍCIO (Exatamente como no mockup)
// ============================================================================
@Composable
private fun HomeScreenPreview(
    onOpenBattery: () -> Unit,
    onOpenClean: () -> Unit,
    onOpenSystem: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Anel grande de Saúde (0-100) + legenda
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
                        sweepAngle = 360f * 0.82f,
                        useCenter = false,
                        style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "82",
                        style = NexusMonoTextStyles.metricHero.copy(fontSize = 42.sp),
                        color = colors.text
                    )
                    Text(
                        text = "Saúde boa",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = colors.ok
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Última análise há 2 h · 3 sugestões",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }

        // Botão principal «Analisar agora» em gradiente
        NexusPrimaryGradientButton(
            text = "Analisar agora",
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        )

        // 4 Cartões: Armazenamento, Bateria, Térmica, Apps sem uso
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeStatusCard(
                modifier = Modifier.weight(1f),
                dotColor = colors.warn,
                label = "Armazenamento",
                value = "38 GB",
                subtitle = "livres de 128",
                useMonoFont = true,
                onClick = onOpenClean
            )
            HomeStatusCard(
                modifier = Modifier.weight(1f),
                dotColor = colors.ok,
                label = "Bateria",
                value = "91%",
                subtitle = "saúde · 31 °C",
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
                dotColor = colors.ok,
                label = "Térmica",
                value = "Normal",
                subtitle = "margem 0,42",
                useMonoFont = false,
                onClick = onOpenSystem
            )
            HomeStatusCard(
                modifier = Modifier.weight(1f),
                dotColor = colors.warn,
                label = "Apps sem uso",
                value = "7",
                subtitle = "> 30 dias",
                useMonoFont = true,
                onClick = onOpenSystem
            )
        }

        // Secção Recente (as últimas 3 ações)
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
            RecentActionRow(
                title = "Limpeza · 1,4 GB para o Lixo",
                timeLabel = "ontem",
                onClick = onOpenHistory
            )
            HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
            RecentActionRow(
                title = "Boost · Genshin Impact 1 h 05",
                timeLabel = "ontem",
                onClick = onOpenHistory
            )
            HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
            RecentActionRow(
                title = "Análise semanal",
                timeLabel = "seg.",
                onClick = onOpenHistory
            )
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
            color = colors.text
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = JetBrainsMonoFamily,
                fontSize = 11.sp
            ),
            color = colors.textMuted
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
            color = colors.text
        )
        Text(
            text = timeLabel,
            style = NexusMonoTextStyles.metricSmall.copy(fontSize = 12.sp),
            color = colors.textMuted
        )
    }
}

// ============================================================================
// 2 · BATERIA (Exatamente como no mockup)
// ============================================================================
@Composable
private fun BatteryScreenPreview(
    onViewBackgroundApps: () -> Unit
) {
    val colors = NexusTheme.colors
    var chargeWarning80 by remember { mutableStateOf(true) }
    var heatAlert40 by remember { mutableStateOf(true) }
    var nightSaver by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cartão principal com ícone vertical de bateria em gradiente + 82%
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                VerticalBatteryGraphic(level = 0.82f)
                Spacer(modifier = Modifier.width(18.dp))
                Column {
                    Text(
                        text = "82%",
                        style = NexusMonoTextStyles.metricHero.copy(fontSize = 36.sp),
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "~9 h 40 min restantes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textDim
                    )
                    Text(
                        text = "estimativa ao ritmo atual",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }
        }

        // 3 mini cartões: Saúde (91%), Ciclos (214), Temp. (31 °C)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Saúde",
                value = "91%",
                valueColor = colors.ok
            )
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Ciclos",
                value = "214",
                valueColor = colors.text
            )
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Temp.",
                value = "31 °C",
                valueColor = colors.text
            )
        }

        // Gráfico Últimas 24 h / nível %
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Últimas 24 h",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
                Text(
                    text = "nível %",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Battery24hBarsChart()
        }

        // 3 Toggles de proteção da bateria
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            NexusToggleRow(
                title = "Aviso de carga a 80 %",
                subtitle = "Carregar menos poupa a bateria a longo prazo",
                checked = chargeWarning80,
                onCheckedChange = { chargeWarning80 = it }
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Alerta de aquecimento",
                subtitle = "Avisa acima de 40 °C a carregar",
                checked = heatAlert40,
                onCheckedChange = { heatAlert40 = it }
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Modo noturno de poupança",
                subtitle = "Abre a Poupança de bateria às 23:00",
                checked = nightSaver,
                onCheckedChange = { nightSaver = it }
            )
        }

        // Banner inferior: 3 apps gastam muito em fundo
        NexusCard(
            backgroundColor = Color(0xFF1F1B16),
            borderColor = colors.warn.copy(alpha = 0.45f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            onClick = onViewBackgroundApps
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.warn)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "3 apps gastam muito em fundo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text
                    )
                }
                Text(
                    text = "Ver",
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
        // Pino superior da bateria
        drawRoundRect(
            color = colors.borderStrong,
            topLeft = Offset((size.width - capWidth) / 2f, 0f),
            size = Size(capWidth, capHeight),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        // Corpo exterior
        drawRoundRect(
            color = colors.borderStrong,
            topLeft = Offset(0f, bodyTop),
            size = Size(size.width, bodyHeight),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
        // Preenchimento em gradiente
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
            style = NexusMonoTextStyles.metricMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}

@Composable
private fun Battery24hBarsChart() {
    val colors = NexusTheme.colors
    val levels = listOf(
        0.92f, 0.88f, 0.84f, 0.78f, 0.72f, 0.68f, 0.62f, 0.56f, 0.48f, 0.42f,
        0.58f, 0.74f, 0.88f, 0.94f, 0.91f, 0.88f, 0.86f, 0.84f, 0.83f, 0.82f
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        levels.forEachIndexed { index, ratio ->
            val isChargingSlice = index in 10..14
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(ratio)
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(if (isChargingSlice) colors.accent2 else colors.accent)
            )
        }
    }
}

// ============================================================================
// 3 · LIMPAR (Exatamente como no mockup)
// ============================================================================
data class CleanCategoryPreviewItem(
    val title: String,
    val subtitle: String,
    val sizeLabel: String,
    val defaultSelected: Boolean
)

@Composable
private fun CleanScreenPreview() {
    val colors = NexusTheme.colors
    val categories = remember {
        listOf(
            CleanCategoryPreviewItem("APK já instalados", "Transferências · 4 ficheiros", "612 MB", true),
            CleanCategoryPreviewItem("Ficheiros grandes", "> 100 MB · 3 ficheiros", "1,1 GB", true),
            CleanCategoryPreviewItem("Transferências antigas", "> 90 dias", "380 MB", true),
            CleanCategoryPreviewItem("Duplicados", "23 fotos iguais", "214 MB", true),
            CleanCategoryPreviewItem("Capturas de ecrã antigas", "> 6 meses", "96 MB", false),
            CleanCategoryPreviewItem("Pastas vazias", "41 pastas", "0 B", false)
        )
    }
    val checkedStates = remember {
        mutableStateListOf(*categories.map { it.defaultSelected }.toTypedArray())
    }

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
            Text(
                text = "Tudo vai para o Lixo do sistema. Podes restaurar durante 30 dias.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )

            // Cartão de Armazenamento com barra segmentada
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
                        text = "Armazenamento",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                    Text(
                        text = "90 / 128 GB",
                        style = NexusMonoTextStyles.metricSmall,
                        color = colors.text
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Barra segmentada (Apps, Fotos, Vídeos, Lixo, Livre)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surfaceAlt)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(0.32f)
                            .fillMaxHeight()
                            .background(colors.accent)
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.20f)
                            .fillMaxHeight()
                            .background(colors.accent2)
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.12f)
                            .fillMaxHeight()
                            .background(colors.ok)
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.06f)
                            .fillMaxHeight()
                            .background(colors.warn)
                    )
                    Spacer(modifier = Modifier.weight(0.30f))
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StorageLegendDot("Apps", colors.accent)
                    StorageLegendDot("Fotos", colors.accent2)
                    StorageLegendDot("Vídeos", colors.ok)
                    StorageLegendDot("Lixo", colors.warn)
                }
            }

            // Lista agrupada de categorias
            NexusCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                contentPadding = PaddingValues(0.dp)
            ) {
                categories.forEachIndexed { index, item ->
                    val isChecked = checkedStates[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checkedStates[index] = !isChecked }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            NexusCyanCheckbox(checked = isChecked)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                    color = if (isChecked) colors.text else colors.textDim
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                        }
                        Text(
                            text = item.sizeLabel,
                            style = NexusMonoTextStyles.metricSmall,
                            color = if (isChecked) colors.text else colors.textMuted
                        )
                    }
                    if (index < categories.lastIndex) {
                        HorizontalDivider(color = colors.border)
                    }
                }
            }
        }

        // Barra fixa inferior «Mover para o Lixo · 2,3 GB»
        Box(modifier = Modifier.padding(vertical = 10.dp)) {
            NexusPrimaryGradientButton(
                text = "Mover para o Lixo · 2,3 GB",
                onClick = {},
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
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
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
// 4 · JOGOS + «PREPARAR BOOST» (Exatamente como no mockup)
// ============================================================================
data class GameTilePreview(
    val code: String,
    val name: String,
    val boxTint: Color
)

@Composable
private fun GamesScreenPreview(
    onStartBoost: () -> Unit
) {
    val colors = NexusTheme.colors
    val games = remember {
        listOf(
            GameTilePreview("GI", "Genshin Impact", Color(0xFF231F3A)),
            GameTilePreview("PUBG", "PUBG Mobile", Color(0xFF2B261C)),
            GameTilePreview("CR", "Clash Royale", Color(0xFF192738)),
            GameTilePreview("BS", "Brawl Stars", Color(0xFF2C1F26)),
            GameTilePreview("FF", "Free Fire", Color(0xFF2A1D21)),
            GameTilePreview("FC", "EA FC Mobile", Color(0xFF182B29))
        )
    }
    var selectedGame by remember { mutableStateOf(games.first()) }
    var dndEnabled by remember { mutableStateOf(true) }
    var stopBackgroundApps by remember { mutableStateOf(true) }
    var thermalWarning by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Grelha 3x2 de jogos
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            games.chunked(3).forEach { rowGames ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowGames.forEach { game ->
                        val isSelected = game.code == selectedGame.code
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedGame = game },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(game.boxTint)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) colors.accent else colors.border,
                                        shape = RoundedCornerShape(18.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = game.code,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.textDim
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = game.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Folha inferior «Preparar boost»
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.borderStrong,
            contentPadding = PaddingValues(16.dp)
        ) {
            // Barra de arrasto da folha inferior
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
                        .background(selectedGame.boxTint)
                        .border(1.dp, colors.accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = selectedGame.code,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.text
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "PREPARAR BOOST",
                        style = NexusMonoTextStyles.badge,
                        color = colors.accent
                    )
                    Text(
                        text = selectedGame.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            NexusToggleRow(
                title = "Não incomodar",
                subtitle = "Silencia notificações durante o jogo",
                checked = dndEnabled,
                onCheckedChange = { dndEnabled = it }
            )
            NexusToggleRow(
                title = "Parar apps em fundo",
                subtitle = "Só as que o Android permite",
                checked = stopBackgroundApps,
                onCheckedChange = { stopBackgroundApps = it }
            )
            NexusToggleRow(
                title = "Aviso de aquecimento",
                subtitle = "Notifica se chegar a Grave",
                checked = thermalWarning,
                onCheckedChange = { thermalWarning = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Barra de estado térmico atual
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
                            .background(colors.ok)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Estado térmico: Normal",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text
                    )
                }
                Text(
                    text = "bateria 31 °C",
                    style = NexusMonoTextStyles.metricSmall.copy(fontSize = 12.sp),
                    color = colors.textDim
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            NexusPrimaryGradientButton(
                text = "Boost e jogar",
                onClick = onStartBoost,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ============================================================================
// 5 · SESSÃO ATIVA + NOTIFICAÇÃO (Exatamente como no mockup)
// ============================================================================
@Composable
private fun ActiveBoostSessionScreenPreview(
    onStopBoost: () -> Unit
) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cartão da notificação contínua em primeiro plano
        NexusCard(
            backgroundColor = colors.surfaceAlt,
            borderColor = colors.borderStrong,
            contentPadding = PaddingValues(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GaugeShieldLogo(size = 18.dp, withContainer = false)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Nexus Guard · agora",
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
                    text = "Boost ativo · Genshin Impact",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                Text(
                    text = "00:42:10",
                    style = NexusMonoTextStyles.metricSmall,
                    color = colors.textDim
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Bateria 38 °C · Térmico: Ligeiro · Não incomodar ligado",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "TERMINAR",
                style = NexusMonoTextStyles.badge.copy(fontSize = 12.sp),
                color = colors.accent2,
                modifier = Modifier.clickable(onClick = onStopBoost)
            )
        }

        // Cronómetro central da sessão ativa
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
                text = "Genshin Impact",
                style = MaterialTheme.typography.titleLarge,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "00:42:10",
                style = NexusMonoTextStyles.metricHero.copy(fontSize = 44.sp),
                color = colors.text
            )
        }

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
                    text = "Temperatura da bateria · 60 s",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
                Text(
                    text = "38 °C",
                    style = NexusMonoTextStyles.metricMedium,
                    color = colors.warn
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LiveThermal60sBars()
        }

        // Grelha 2x2 de estatísticas da sessão
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Pior estado térmico",
                value = "Ligeiro"
            )
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Bateria gasta",
                value = "11 %"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Não incomodar",
                value = "Ligado"
            )
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Apps paradas",
                value = "4"
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Botão Terminar boost
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
private fun LiveThermal60sBars() {
    val colors = NexusTheme.colors
    val heights = listOf(
        0.55f, 0.56f, 0.58f, 0.60f, 0.58f, 0.54f, 0.52f, 0.55f, 0.62f, 0.68f,
        0.74f, 0.78f, 0.75f, 0.70f, 0.65f, 0.64f, 0.66f, 0.72f, 0.76f, 0.80f,
        0.82f, 0.79f, 0.74f, 0.68f, 0.66f, 0.64f, 0.62f, 0.60f
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(colors.warn.copy(alpha = 0.85f))
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
// SEPARADOR SISTEMA + HISTÓRICO + DEFINIÇÕES
// ============================================================================
@Composable
private fun SystemScreenPreview(
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    text = "31 °C · Normal",
                    style = NexusMonoTextStyles.metricSmall,
                    color = colors.ok
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LiveThermal60sBars()
        }

        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border
        ) {
            Text(
                text = "Apps em fundo e sem uso",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Monitorização via UsageStatsManager e ActivityManager.MemoryInfo com ações Parar, Desinstalar e Restringir bateria.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )
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
                Text("Ver Histórico", color = colors.text)
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

@Composable
private fun HistoryScreenPreview() {
    val colors = NexusTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border
        ) {
            Text(
                text = "Limpeza · 1,4 GB para o Lixo",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ontem · Quarentena ativa no Lixo do sistema (restaurável)",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )
        }
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border
        ) {
            Text(
                text = "Boost · Genshin Impact 1 h 05",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ontem · Temp. máx. 38 °C · Pior estado: Ligeiro",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )
        }
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

@Composable
private fun Phase1SettingsScreen(
    darkThemeEnabled: Boolean,
    onToggleDarkTheme: (Boolean) -> Unit,
    onReopenOnboarding: () -> Unit
) {
    val colors = NexusTheme.colors
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DarkMode,
                        contentDescription = null,
                        tint = colors.accent2
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tema escuro (NexusTheme)",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text
                        )
                        Text(
                            text = "Ativo por omissão (#0C0F16). O tema claro é opcional.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDim
                        )
                    }
                }
                Switch(
                    checked = darkThemeEnabled,
                    onCheckedChange = onToggleDarkTheme,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = colors.accent
                    )
                )
            }
        }

        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border
        ) {
            Text(
                text = "Distribuição e Flavor",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Variante ativa: ${BuildConfig.FLAVOR_LABEL} (${BuildConfig.VERSION_NAME})",
                style = NexusMonoTextStyles.metricSmall,
                color = colors.accent2
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Sem analytics, sem anúncios e sem permissão de INTERNET.",
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
