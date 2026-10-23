package com.nexusguard.android.ui.system

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.ui.SystemUiState
import com.nexusguard.android.ui.components.AppIconOrMonogram
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusChip
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.components.NexusTextButton
import com.nexusguard.android.ui.components.Thermal60sCanvasChart
import com.nexusguard.android.ui.theme.NexusButtonShape
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import java.util.Locale

@Composable
fun SystemScreen(
    uiState: SystemUiState,
    onStopPermittedBackground: ((Int) -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val isLoading = uiState.isLoading
    val tempLabel = uiState.batteryTempCelsius?.let {
        String.format(Locale("pt", "PT"), "%.1f °C", it)
    } ?: "Indisponível"
    val availRamGb = if (isLoading) "—" else NexusFormatters.formatBytes(uiState.availableRamBytes)
    val totalRamGb = if (isLoading) "—" else NexusFormatters.formatBytes(uiState.totalRamBytes)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "thermal_60s_card") {
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
                        text = if (isLoading) "A ler..." else "$tempLabel · ${uiState.thermal.level.labelPt}",
                        style = NexusMonoTextStyles.metricSmall,
                        color = colors.ok
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Margem térmica: ${uiState.thermal.headroomText} · CPU: bloqueado pelo Android sem root",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
                Thermal60sCanvasChart(tempHistory = uiState.tempHistory60s)
            }
        }

        item(key = "ram_card") {
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
                        Spacer(modifier = Modifier.width(8.dp))
                        NexusChip(
                            text = "Limpar fundo",
                            color = colors.accent2,
                            onClick = {
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
        }

        if (!uiState.hasUsageStatsPermission) {
            item(key = "usage_stats_optional_card") {
                NexusCard(
                    backgroundColor = colors.surfaceAlt,
                    borderColor = colors.borderStrong,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text(
                        text = "Opcional: Detalhe de Cache por App (Android 13+)",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "As tuas ${uiState.installedApps.size} apps e tamanhos reais já estão carregados abaixo sem precisares desta permissão! Se quiseres desbloquear as «Definições restritas» do Android em 2 cliques:",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { NexusSystemIntents.openAppDetailsSettings(context, context.packageName) },
                        shape = NexusButtonShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = "1.º Abrir Info da App (tocar em ⋮ Mais → Permitir)",
                            color = colors.accent2
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    NexusPrimaryGradientButton(
                        text = "2.º Ativar Dados de Utilização",
                        onClick = { NexusSystemIntents.openUsageAccessSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item(key = "apps_header") {
            Text(
                text = if (isLoading) {
                    "A ler aplicações instaladas..."
                } else {
                    "Apps instaladas (${uiState.installedApps.size}) · ordenadas por tamanho"
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.textDim
            )
        }

        items(
            items = uiState.installedApps,
            key = { it.packageName }
        ) { app ->
            NexusCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
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
                            val usageNote = if (uiState.hasUsageStatsPermission) {
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
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NexusTextButton(
                        text = "Parar / Bateria",
                        color = colors.accent2,
                        onClick = {
                            NexusSystemIntents.openAppDetailsSettings(context, app.packageName)
                        }
                    )
                    NexusTextButton(
                        text = "Desinstalar",
                        color = colors.danger,
                        onClick = {
                            NexusSystemIntents.requestUninstallPackage(context, app.packageName)
                        }
                    )
                }
            }
        }

        item(key = "footer_buttons") {
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
}
