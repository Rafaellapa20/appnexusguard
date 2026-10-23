package com.nexusguard.android.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ThermalLevelPt
import com.nexusguard.android.ui.HomeUiState
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import java.util.Locale

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onRunAnalysis: () -> Unit,
    onApplySafeFixes: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenClean: () -> Unit,
    onOpenSystem: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val isLoading = uiState.isLoading
    val score = uiState.healthScore
    val ringStatusColor = when {
        isLoading -> colors.textDim
        score >= 80 -> colors.ok
        score >= 60 -> colors.warn
        else -> colors.danger
    }

    val freeGbText = if (isLoading) "—" else NexusFormatters.formatBytes(uiState.storage.freeBytes)
    val totalGbText = if (isLoading) "—" else NexusFormatters.formatBytes(uiState.storage.totalBytes)
    val ringCaption = if (isLoading) {
        "A medir armazenamento, bateria e estado térmico..."
    } else {
        "$freeGbText livres de $totalGbText · Térmico ${uiState.thermal.level.labelPt}"
    }

    val freeRatio = if (uiState.storage.totalBytes > 0L) {
        uiState.storage.freeBytes.toFloat() / uiState.storage.totalBytes.toFloat()
    } else {
        1f
    }
    val storageDotColor = when {
        freeRatio < 0.10f -> colors.danger
        freeRatio < 0.20f -> colors.warn
        else -> colors.ok
    }

    val tempText = uiState.battery.temperatureCelsius?.let {
        String.format(Locale("pt", "PT"), "%.1f °C", it)
    } ?: "N/D"
    val batteryDotColor = if (uiState.battery.isHealthy && (uiState.battery.temperatureCelsius ?: 30f) < 40f) {
        colors.ok
    } else {
        colors.warn
    }

    val thermalDotColor = when (uiState.thermal.level) {
        ThermalLevelPt.NORMAL, ThermalLevelPt.INDISPONIVEL -> colors.ok
        ThermalLevelPt.LIGEIRO, ThermalLevelPt.MODERADO -> colors.warn
        ThermalLevelPt.GRAVE, ThermalLevelPt.CRITICO -> colors.danger
    }

    val unusedValueText = if (isLoading) "—" else uiState.unusedAppsCount.toString()
    val unusedSubtitleText = if (isLoading) "A ler apps..." else "> 30 dias (${uiState.installedAppsCount} apps)"
    val analysisState = uiState.analysisState

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
                    if (!isLoading) {
                        drawArc(
                            brush = ringBrush,
                            startAngle = -90f,
                            sweepAngle = 360f * (score / 100f),
                            useCenter = false,
                            style = Stroke(width = 13.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isLoading) "—" else score.toString(),
                        style = NexusMonoTextStyles.metricHero.copy(fontSize = 42.sp),
                        color = colors.text
                    )
                    Text(
                        text = if (isLoading) "A ler sensores..." else uiState.healthLabel,
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
                            TextButton(onClick = onApplySafeFixes) {
                                Text(
                                    text = "Corrigir tudo ($autoFixes)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.accent2
                                )
                            }
                        }
                    }

                    analysisState.statusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(4.dp))
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
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fix.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = if (fix.isResolved) colors.textMuted else colors.text
                                )
                                Text(
                                    text = fix.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textDim
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            when {
                                fix.isResolved -> {
                                    Text(
                                        text = "Resolvido",
                                        style = NexusMonoTextStyles.badge,
                                        color = colors.ok
                                    )
                                }
                                !fix.requiresSystemConfirmation -> {
                                    TextButton(onClick = onApplySafeFixes) {
                                        Text(
                                            text = "Corrigir",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.accent2
                                        )
                                    }
                                }
                                else -> {
                                    TextButton(
                                        onClick = {
                                            when (fix.actionRouteOrType) {
                                                "OPEN_CLEAN" -> onOpenClean()
                                                "OPEN_SYSTEM" -> onOpenSystem()
                                                "OPEN_USAGE_PERM" -> NexusSystemIntents.openUsageAccessSettings(context)
                                                "OPEN_MEDIA_PERM" -> onOpenClean()
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "Ação necessária →",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = colors.warn
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Grelha 2x2 de 4 cartões com métricas 100% reais
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeStatusCard(
                modifier = Modifier.weight(1f),
                dotColor = storageDotColor,
                label = "Armazenamento",
                value = freeGbText,
                subtitle = if (isLoading) "A calcular..." else "livres de $totalGbText",
                useMonoFont = true,
                onClick = onOpenClean
            )
            HomeStatusCard(
                modifier = Modifier.weight(1f),
                dotColor = batteryDotColor,
                label = "Bateria",
                value = if (isLoading) "—" else "${uiState.battery.levelPercent}%",
                subtitle = if (isLoading) "A ler..." else "${uiState.battery.healthLabelPt.lowercase()} · $tempText",
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
                value = if (isLoading) "—" else uiState.thermal.level.labelPt,
                subtitle = uiState.thermal.headroomText,
                useMonoFont = false,
                onClick = onOpenSystem
            )
            HomeStatusCard(
                modifier = Modifier.weight(1f),
                dotColor = if (uiState.unusedAppsCount > 0) colors.warn else colors.ok,
                label = "Apps sem uso",
                value = unusedValueText,
                subtitle = unusedSubtitleText,
                useMonoFont = true,
                onClick = onOpenSystem
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
            if (uiState.recentHistory.isEmpty()) {
                Text(
                    text = "Sem ações registadas ainda. Ao analisar, limpar ou usar o Boost, tudo fica registado aqui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                uiState.recentHistory.forEachIndexed { idx, entry ->
                    RecentActionRow(
                        title = entry.title,
                        timeLabel = NexusFormatters.formatRelativeTimePt(entry.timestampEpochMillis),
                        onClick = onOpenHistory
                    )
                    if (idx < uiState.recentHistory.lastIndex) {
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
                    .size(7.dp)
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
                MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)
            },
            color = colors.text
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
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
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )
    }
}
