package com.nexusguard.android.ui.battery

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.ui.BatteryUiState
import com.nexusguard.android.ui.components.AppIconOrMonogram
import com.nexusguard.android.ui.components.Battery24hCanvasChart
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusChip
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.components.NexusTextButton
import com.nexusguard.android.ui.components.NexusToggleRow
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BatteryScreen(
    uiState: BatteryUiState,
    onToggleStorageAlert: (Boolean) -> Unit,
    onToggleThermalAlert: (Boolean) -> Unit,
    onToggleBatteryAlert: (Boolean) -> Unit,
    onActivateNexusSaver: ((Boolean) -> Unit) -> Unit = {},
    onRestoreNexusSaver: ((Boolean) -> Unit) -> Unit = {},
    onSetAutoBatterySaver: (Boolean, Int) -> Unit = { _, _ -> },
    onSetChargeLimitAlarm: (Boolean, Int) -> Unit = { _, _ -> },
    onToggleDarkTheme: (Boolean) -> Unit = {},
    onViewBackgroundApps: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val bat = uiState.battery
    val measured = uiState.measuredConsumption
    val isLoading = uiState.isLoading
    val ptLocale = Locale.forLanguageTag("pt-PT")
    val tempStr = bat.temperatureCelsius?.let {
        String.format(ptLocale, "%.1f °C", it)
    } ?: "Indisponível"

    val heroRemainingText = when {
        isLoading -> "A ler estado da bateria..."
        bat.isCharging -> bat.statusLineText
        measured.hasEnoughSamples2h -> measured.estimatedRemainingTextPt
        else -> "${bat.statusLineText} · ${measured.collectingStatusText}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Cartão principal com nível (1 só número grande) e tempo restante
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                VerticalBatteryGraphic(level = if (isLoading) 0f else bat.levelPercent / 100f)
                Spacer(modifier = Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLoading) "—" else "${bat.levelPercent}%",
                        style = NexusMonoTextStyles.metricHero.copy(fontSize = 36.sp),
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = heroRemainingText,
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

        // 2. Gráfico de 24 h com as zonas de carga coloridas (sem amostras sintéticas)
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            val chartSamples = remember(uiState.batterySamples, isLoading) {
                if (isLoading) {
                    persistentListOf()
                } else {
                    uiState.batterySamples.reversed().toImmutableList()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Histórico medido (24 h · zonas de carga a verde)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
                Text(
                    text = when {
                        isLoading -> "A ler..."
                        chartSamples.isEmpty() -> "a recolher amostras"
                        else -> "atual ${bat.levelPercent}%"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (!isLoading && chartSamples.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "a recolher amostras",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            } else {
                Battery24hCanvasChart(samples = chartSamples)
            }
        }

        // 3. Cartões de consumo medido (%/h com ecrã ligado e em repouso; ou «a recolher dados (x/2 h)»)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val screenOnValue = when {
                isLoading -> "—"
                !measured.hasEnoughSamples2h -> measured.collectingStatusText
                measured.screenOnDrainPercentPerHour != null ->
                    String.format(ptLocale, "%.1f %%/h", measured.screenOnDrainPercentPerHour)
                else -> "Sem descarga"
            }
            val screenOffValue = when {
                isLoading -> "—"
                !measured.hasEnoughSamples2h -> measured.collectingStatusText
                measured.screenOffIdleDrainPercentPerHour != null ->
                    String.format(ptLocale, "%.1f %%/h", measured.screenOffIdleDrainPercentPerHour)
                else -> "Sem repouso"
            }

            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Consumo ecrã ligado",
                value = screenOnValue,
                subtitle = if (measured.hasEnoughSamples2h) "Medido em descarga" else "Requer 2 h de amostras",
                valueColor = colors.text,
                useMonoFont = measured.hasEnoughSamples2h
            )
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Consumo em repouso",
                value = screenOffValue,
                subtitle = if (measured.hasEnoughSamples2h) "Ecrã desligado / noite" else "Requer 2 h de amostras",
                valueColor = colors.text,
                useMonoFont = measured.hasEnoughSamples2h
            )
        }

        // 4. Apps que mais gastam (últimas 24 h + serviços em fundo + botão «Restringir em fundo»)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Apps que mais gastam (últimas 24 h)",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Text(
                        text = "Ordenadas por tempo em primeiro plano e serviços em fundo. Ao tocar em «Restringir em fundo», confirmas nas definições do Android (opção «Restrita»).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            if (!uiState.hasUsageStatsPermission) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Requer acesso a utilização",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.warn,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    NexusChip(
                        text = "Autorizar",
                        color = colors.accent2,
                        onClick = { NexusSystemIntents.openUsageAccessSettings(context) }
                    )
                }
            } else if (uiState.topConsumingApps24h.isEmpty()) {
                Text(
                    text = "Sem atividade relevante registada nas últimas 24 horas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.topConsumingApps24h.take(5).forEach { app ->
                        val fgMinutes = ((app.foregroundTime24hMillis ?: 0L) / 60_000L).toInt()
                        val fgLabel = if (fgMinutes >= 60) {
                            "${fgMinutes / 60} h ${fgMinutes % 60} min em 1.º plano"
                        } else {
                            "$fgMinutes min em 1.º plano"
                        }
                        val fgsBadge = if (app.hasForegroundService24h) " · Serviço em fundo" else ""

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
                                    iconSizeDp = 34
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = colors.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$fgLabel$fgsBadge",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (app.hasForegroundService24h) colors.warn else colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            NexusTextButton(
                                text = "Restringir em fundo",
                                color = colors.accent2,
                                onClick = {
                                    NexusSystemIntents.openAppDetailsSettings(context, app.packageName)
                                }
                            )
                        }
                    }
                }
            }
        }

        // 5. Perfil «Poupança Nexus» (requer WRITE_SETTINGS, guarda valores anteriores em DataStore)
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = if (uiState.nexusBatterySaverActive) colors.accent2 else colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Text(
                text = "Perfil «Poupança Nexus»",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Aplica tempo limite do ecrã de 30 s e brilho automático. Os valores anteriores ficam guardados para poderes repor a qualquer momento.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (!uiState.hasWriteSettingsPermission) {
                Text(
                    text = "Explicação da permissão: o Android exige a permissão «Modificar definições do sistema» (WRITE_SETTINGS) para ajustar o tempo limite do ecrã e o modo de brilho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warn
                )
                Spacer(modifier = Modifier.height(8.dp))
                NexusPrimaryGradientButton(
                    text = "Autorizar modificação de definições",
                    onClick = { NexusSystemIntents.openWriteSettingsPermission(context) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NexusPrimaryGradientButton(
                        text = if (uiState.nexusBatterySaverActive) "Poupança Nexus ativa" else "Ativar Poupança Nexus",
                        enabled = !uiState.nexusBatterySaverActive,
                        onClick = {
                            onActivateNexusSaver { ok ->
                                Toast.makeText(
                                    context,
                                    if (ok) "Perfil Poupança Nexus aplicado (ecrã 30 s e brilho automático)." else "Não foi possível aplicar o perfil.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.nexusBatterySaverActive) {
                        NexusChip(
                            text = "Repor definições anteriores",
                            color = colors.warn,
                            onClick = {
                                onRestoreNexusSaver { ok ->
                                    Toast.makeText(
                                        context,
                                        if (ok) "Definições anteriores de ecrã e brilho repostas." else "Erro ao repor definições.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Ativar automaticamente abaixo de ${uiState.autoBatterySaverThresholdPercent}%",
                subtitle = "O Worker de 15 min verifica, notifica e aplica se autorizado",
                checked = uiState.autoBatterySaverEnabled,
                onCheckedChange = { enabled ->
                    onSetAutoBatterySaver(enabled, uiState.autoBatterySaverThresholdPercent)
                }
            )
            if (uiState.autoBatterySaverEnabled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Limiar:",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                    listOf(15, 20, 25).forEach { pct ->
                        NexusChip(
                            text = "$pct%",
                            color = if (uiState.autoBatterySaverThresholdPercent == pct) colors.accent2 else colors.textMuted,
                            onClick = { onSetAutoBatterySaver(true, pct) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            HorizontalDivider(color = colors.border)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Atalhos diretos do sistema",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NexusChip(
                    text = "Poupança de bateria",
                    color = colors.accent2,
                    onClick = { NexusSystemIntents.openBatterySaverSettings(context) }
                )
                NexusChip(
                    text = "Taxa de atualização / Ecrã",
                    color = colors.accent2,
                    onClick = { NexusSystemIntents.openDisplaySettings(context) }
                )
                NexusChip(
                    text = "Tema escuro",
                    color = colors.accent2,
                    onClick = { onToggleDarkTheme(true) }
                )
            }
        }

        // 6. Proteção da saúde da bateria (limite 80/85/90 %, atalho OEM nativo, sessões de carga)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Saúde",
                value = if (isLoading) "—" else bat.healthLabelPt,
                subtitle = "BatteryManager",
                valueColor = if (bat.isHealthy) colors.ok else colors.warn,
                useMonoFont = false
            )
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Ciclos",
                value = if (isLoading) "—" else bat.cycleCountText,
                subtitle = bat.cycleCountReason,
                valueColor = colors.text,
                useMonoFont = true
            )
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Temp.",
                value = if (isLoading) "—" else tempStr,
                subtitle = if (isLoading) "A ler..." else "${bat.voltageMv} mV",
                valueColor = if ((bat.temperatureCelsius ?: 0f) > 40f) colors.danger else colors.text,
                useMonoFont = true
            )
        }

        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Text(
                text = "Proteção da saúde da bateria",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.hasOemNativeChargeLimit) {
                Text(
                    text = "O fabricante deste dispositivo (Samsung / Pixel Android 15+) disponibiliza limite de carga nativo nas definições do sistema:",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
                Spacer(modifier = Modifier.height(8.dp))
                NexusChip(
                    text = "Abrir proteção de bateria do sistema →",
                    color = colors.accent2,
                    onClick = { NexusSystemIntents.openOemBatteryProtectionSettings(context) }
                )
            } else {
                NexusToggleRow(
                    title = "Alarme de limite de carga (${uiState.chargeLimitTargetPercent}%)",
                    subtitle = "Monitoriza com um serviço leve apenas enquanto carrega e notifica ao chegar à meta",
                    checked = uiState.chargeLimitAlarmEnabled,
                    onCheckedChange = { enabled ->
                        onSetChargeLimitAlarm(enabled, uiState.chargeLimitTargetPercent)
                    }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Meta de carga:",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                    listOf(80, 85, 90).forEach { target ->
                        NexusChip(
                            text = "$target%",
                            color = if (uiState.chargeLimitTargetPercent == target) colors.accent2 else colors.textMuted,
                            onClick = { onSetChargeLimitAlarm(uiState.chargeLimitAlarmEnabled, target) }
                        )
                    }
                }
            }

            if (uiState.chargingSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.border)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Histórico de sessões de carga",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                uiState.chargingSessions.take(5).forEach { session ->
                    val maxTempLabel = String.format(ptLocale, "%.1f °C", session.maxTempCelsius)
                    val isHot = session.maxTempCelsius > 40.0f
                    val durationMin = ((session.endEpochMillis - session.startEpochMillis) / 60_000L).coerceAtLeast(1L)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${session.startPercent}% → ${session.endPercent}% (${durationMin} min)",
                            style = NexusMonoTextStyles.metricSmall,
                            color = colors.text
                        )
                        Text(
                            text = if (isHot) "Máx. $maxTempLabel (> 40 °C)" else "Máx. $maxTempLabel",
                            style = NexusMonoTextStyles.metricSmall,
                            color = if (isHot) colors.danger else colors.textMuted
                        )
                        Text(
                            text = NexusFormatters.formatRelativeTimePt(session.endEpochMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        // Alertas gerais
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            NexusToggleRow(
                title = "Alerta de armazenamento (< 10 %)",
                subtitle = "Notifica quando o espaço livre desce abaixo de 10 %",
                checked = uiState.storageAlertEnabled,
                onCheckedChange = onToggleStorageAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Alerta de aquecimento (> 40 °C a carregar / Grave)",
                subtitle = "Notifica se o estado térmico chegar a Grave ou se passar os 40 °C em carga",
                checked = uiState.thermalAlertEnabled,
                onCheckedChange = onToggleThermalAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Alerta de bateria degradada",
                subtitle = "Notifica se o BatteryManager reportar degradação ou sobretensão",
                checked = uiState.batteryAlertEnabled,
                onCheckedChange = onToggleBatteryAlert
            )
        }

        NexusCard(
            backgroundColor = colors.warnSurface,
            borderColor = colors.warn.copy(alpha = 0.45f),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            onClick = onViewBackgroundApps
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
                        text = when {
                            isLoading -> "A verificar atividade de apps..."
                            !uiState.hasUsageStatsPermission -> "Requer acesso a utilização"
                            else -> "${uiState.highBackgroundAppsCount} app(s) ativas · ${uiState.unusedAppsCount} sem uso (>30d)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                NexusTextButton(
                    text = "Ver",
                    color = colors.warn,
                    onClick = onViewBackgroundApps
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
    valueColor: Color,
    useMonoFont: Boolean
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
            style = if (useMonoFont) {
                NexusMonoTextStyles.metricMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
            } else {
                MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)
            },
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
