package com.nexusguard.android.ui.games

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.repository.InstalledAppUsageInfo
import com.nexusguard.android.ui.GamesUiState
import com.nexusguard.android.ui.components.AppIconOrMonogram
import com.nexusguard.android.ui.components.GaugeShieldLogo
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusChip
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.components.NexusTextButton
import com.nexusguard.android.ui.components.NexusToggleRow
import com.nexusguard.android.ui.components.Thermal60sCanvasChart
import com.nexusguard.android.ui.theme.NexusButtonShape
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    uiState: GamesUiState,
    onStartBoost: (String, String, String, Boolean, Boolean, Boolean) -> Unit,
    onOpenActiveSession: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val boostSession = uiState.boostSession

    val detectedGames = remember(uiState.installedApps) {
        uiState.installedApps.filter { it.isGame }
    }
    val otherApps = remember(uiState.installedApps, detectedGames) {
        if (detectedGames.size < 3) {
            uiState.installedApps.filter { !it.isGame }.take(6)
        } else {
            emptyList()
        }
    }

    var sheetTargetApp by remember { mutableStateOf<InstalledAppUsageInfo?>(null) }
    var dndEnabled by remember { mutableStateOf(true) }
    var stopBackgroundApps by remember { mutableStateOf(true) }
    var thermalWarning by remember { mutableStateOf(true) }

    val tempText = uiState.batteryTempCelsius?.let {
        String.format(Locale("pt", "PT"), "bateria %.1f °C", it)
    } ?: "bateria indisponível"

    // Fase 6: «Preparar boost» numa ModalBottomSheet real aberta ao tocar num jogo
    sheetTargetApp?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { sheetTargetApp = null },
            containerColor = colors.surface,
            contentColor = colors.text
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconOrMonogram(
                        packageName = target.packageName,
                        monogram = target.monogram,
                        iconSizeDp = 42
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PREPARAR BOOST",
                            style = NexusMonoTextStyles.badge,
                            color = colors.accent
                        )
                        Text(
                            text = target.appName,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!target.isGame) {
                            Text(
                                text = "não detetada como jogo",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textMuted
                            )
                        }
                    }
                }

                NexusToggleRow(
                    title = "Não incomodar",
                    subtitle = if (uiState.hasDndPermission) {
                        "Silencia notificações durante o jogo"
                    } else {
                        "Toque para autorizar acesso ao Não incomodar"
                    },
                    checked = dndEnabled && uiState.hasDndPermission,
                    onCheckedChange = { want ->
                        if (!uiState.hasDndPermission) {
                            NexusSystemIntents.openDndPolicySettings(context)
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
                                    if (uiState.thermal.level.severityRank <= 0) colors.ok else colors.warn
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Estado térmico: ${uiState.thermal.level.labelPt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text
                        )
                    }
                    Text(
                        text = tempText,
                        style = NexusMonoTextStyles.metricSmall,
                        color = colors.textDim
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                NexusPrimaryGradientButton(
                    text = "Boost e jogar",
                    onClick = {
                        val pkg = target.packageName
                        val name = target.appName
                        val monogram = target.monogram
                        sheetTargetApp = null
                        onStartBoost(
                            pkg,
                            name,
                            monogram,
                            dndEnabled && uiState.hasDndPermission,
                            stopBackgroundApps,
                            false
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                        color = colors.ok,
                        modifier = Modifier.weight(1f)
                    )
                    NexusTextButton(
                        text = "Abrir",
                        color = colors.accent2,
                        onClick = onOpenActiveSession
                    )
                }
            }
        }

        // Secção de jogos detetados (ApplicationInfo.CATEGORY_GAME ou lista conhecida)
        Text(
            text = "Jogos detetados (${detectedGames.size})",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textDim
        )

        if (uiState.isLoading) {
            NexusCard(backgroundColor = colors.surface, borderColor = colors.border) {
                Text(
                    text = "A procurar jogos instalados no telemóvel...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textDim
                )
            }
        } else if (detectedGames.isEmpty()) {
            NexusCard(backgroundColor = colors.surface, borderColor = colors.border) {
                Text(
                    text = "Nenhum jogo detetado com CATEGORY_GAME neste telemóvel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Podes escolher qualquer aplicação abaixo na secção «Outras apps» para preparar o Boost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
            }
        } else {
            AppTilesGrid(
                apps = detectedGames,
                showNonGameLabel = false,
                onSelectApp = { sheetTargetApp = it }
            )
        }

        // Fase 6: Se houver menos de 3 jogos detetados, mostra uma secção à parte «Outras apps» com o rótulo «não detetada como jogo»
        if (!uiState.isLoading && detectedGames.size < 3 && otherApps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Outras apps (não detetada como jogo)",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textDim
            )
            Text(
                text = "Toque numa aplicação para abrir a folha «Preparar boost»:",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
            AppTilesGrid(
                apps = otherApps,
                showNonGameLabel = true,
                onSelectApp = { sheetTargetApp = it }
            )
        }
    }
}

@Composable
private fun AppTilesGrid(
    apps: List<InstalledAppUsageInfo>,
    showNonGameLabel: Boolean,
    onSelectApp: (InstalledAppUsageInfo) -> Unit
) {
    val colors = NexusTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        apps.chunked(3).forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowApps.forEach { app ->
                    NexusCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = colors.surface,
                        borderColor = colors.border,
                        contentPadding = PaddingValues(10.dp),
                        onClick = { onSelectApp(app) }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppIconOrMonogram(
                                packageName = app.packageName,
                                monogram = app.monogram,
                                iconSizeDp = 44
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            if (showNonGameLabel) {
                                Text(
                                    text = "não detetada como jogo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "Boost e jogar",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.accent2,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                repeat(3 - rowApps.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ActiveBoostScreen(
    uiState: GamesUiState,
    onStopBoost: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val boostSession = uiState.boostSession
    val liveTemp = boostSession.currentTempCelsius ?: uiState.batteryTempCelsius
    val tempLabel = liveTemp?.let {
        String.format(Locale("pt", "PT"), "%.1f °C", it)
    } ?: "Indisponível"
    val dndStatusText = if (boostSession.dndEnabled) "Não incomodar ligado" else "Não incomodar desligado"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NexusChip(
                    text = "TERMINAR",
                    color = colors.accent2,
                    onClick = onStopBoost
                )
                if (boostSession.gamePackage.isNotBlank()) {
                    NexusChip(
                        text = "ABRIR JOGO",
                        color = colors.ok,
                        onClick = {
                            val launch = context.packageManager.getLaunchIntentForPackage(boostSession.gamePackage)
                            if (launch != null) {
                                context.startActivity(launch)
                            }
                        }
                    )
                }
            }
        }

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

        // Gráfico térmico de 60 s num único Canvas
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
            Thermal60sCanvasChart(tempHistory = uiState.tempHistory60s)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Pior estado térmico",
                value = boostSession.worstThermalLevel.labelPt,
                useMono = false
            )
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Bateria gasta",
                value = "${boostSession.batteryUsedPercent} %",
                useMono = true
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Não incomodar",
                value = if (boostSession.dndEnabled) "Ligado" else "Desligado",
                useMono = false
            )
            SessionMetricBox(
                modifier = Modifier.weight(1f),
                label = "Apps paradas",
                value = boostSession.appsStoppedCount.toString(),
                useMono = true
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
private fun SessionMetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    useMono: Boolean
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
            style = if (useMono) {
                NexusMonoTextStyles.metricLarge
            } else {
                MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            },
            color = colors.text
        )
    }
}
