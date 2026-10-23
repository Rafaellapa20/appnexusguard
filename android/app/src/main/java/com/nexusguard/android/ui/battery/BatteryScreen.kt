package com.nexusguard.android.ui.battery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusguard.android.data.local.BatterySampleEntity
import com.nexusguard.android.ui.BatteryUiState
import com.nexusguard.android.ui.components.Battery24hCanvasChart
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusToggleRow
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.util.Locale

@Composable
fun BatteryScreen(
    uiState: BatteryUiState,
    onToggleStorageAlert: (Boolean) -> Unit,
    onToggleThermalAlert: (Boolean) -> Unit,
    onToggleBatteryAlert: (Boolean) -> Unit,
    onViewBackgroundApps: () -> Unit
) {
    val colors = NexusTheme.colors
    val bat = uiState.battery
    val isLoading = uiState.isLoading
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
                Column {
                    Text(
                        text = if (isLoading) "—" else "${bat.levelPercent}%",
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Saúde",
                value = if (isLoading) "—" else bat.healthLabelPt,
                subtitle = "BatteryManager",
                valueColor = if (bat.isHealthy) colors.ok else colors.warn
            )
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Ciclos",
                value = if (isLoading) "—" else bat.cycleCountText,
                subtitle = bat.cycleCountReason,
                valueColor = colors.text
            )
            MiniBatteryStatCard(
                modifier = Modifier.weight(1f),
                label = "Temp.",
                value = if (isLoading) "—" else tempStr,
                subtitle = if (isLoading) "A ler..." else "${bat.voltageMv} mV",
                valueColor = colors.text
            )
        }

        // Gráfico de amostras reais num único Canvas
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            val chartSamples = remember(uiState.batterySamples, bat.levelPercent, bat.isCharging, isLoading) {
                if (isLoading) {
                    persistentListOf()
                } else {
                    val ordered = uiState.batterySamples.reversed()
                    if (ordered.isEmpty()) {
                        persistentListOf(
                            BatterySampleEntity(
                                timestampEpochMillis = System.currentTimeMillis(),
                                levelPercent = bat.levelPercent,
                                temperatureCelsius = bat.temperatureCelsius ?: 30f,
                                isCharging = bat.isCharging
                            )
                        )
                    } else {
                        ordered.toImmutableList()
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Histórico medido (24 h)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
                Text(
                    text = if (isLoading) "A ler..." else "nível % (atual ${bat.levelPercent}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Battery24hCanvasChart(samples = chartSamples)
        }

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
                title = "Alerta de aquecimento",
                subtitle = "Notifica se o estado térmico chegar a Grave ou Crítico",
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
                        text = if (isLoading) {
                            "A verificar atividade de apps..."
                        } else {
                            "${uiState.highBackgroundAppsCount} app(s) ativas · ${uiState.unusedAppsCount} sem uso (>30d)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
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
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
