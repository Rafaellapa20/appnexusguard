package com.nexusguard.android.ui.history

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusguard.android.domain.MonthlyReportSummary
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.ui.components.AppIconOrMonogram
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import java.util.Locale

@Composable
fun MonthlyReportScreen(
    summary: MonthlyReportSummary,
    onExportMonthlyPdf: (Uri, (Boolean) -> Unit) -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            onExportMonthlyPdf(uri) { ok ->
                Toast.makeText(
                    context,
                    if (ok) "Relatório mensal exportado em PDF." else "Não foi possível exportar o PDF.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Espaço libertado
        NexusCard {
            Text(
                text = "Espaço libertado (${summary.periodLabelPt})",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textDim
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = NexusFormatters.formatBytes(summary.totalBytesFreed30d),
                style = NexusMonoTextStyles.metricHero,
                color = colors.accent2
            )
            Text(
                text = "${summary.actionsCount30d} ações registadas no Histórico nos últimos 30 dias",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }

        // 2. Evolução da saúde
        NexusCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Evolução da saúde",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                Text(
                    text = "Atual: ${summary.currentHealthScore}/100",
                    style = NexusMonoTextStyles.metricMedium,
                    color = when {
                        summary.currentHealthScore >= 80 -> colors.ok
                        summary.currentHealthScore >= 60 -> colors.warn
                        else -> colors.danger
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val points = if (summary.healthEvolutionPoints.size >= 2) {
                summary.healthEvolutionPoints
            } else {
                listOf(summary.currentHealthScore, summary.currentHealthScore)
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceAlt)
                    .padding(12.dp)
            ) {
                val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
                val path = Path()
                points.forEachIndexed { idx, score ->
                    val x = idx * stepX
                    val y = size.height - ((score.coerceIn(0, 100) / 100f) * size.height)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    drawCircle(
                        color = colors.accent2,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
                drawPath(
                    path = path,
                    color = colors.accent,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // 3. Consumo médio %/h
        NexusCard {
            Text(
                text = "Consumo médio de bateria (%/h)",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Ecrã ligado", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    Text(
                        text = summary.avgScreenOnDrainPerHour?.let {
                            String.format(Locale("pt", "PT"), "%.1f %%/h", it)
                        } ?: "A recolher (< 2 h)",
                        style = NexusMonoTextStyles.metricMedium,
                        color = colors.text
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Em repouso", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                    Text(
                        text = summary.avgIdleDrainPerHour?.let {
                            String.format(Locale("pt", "PT"), "%.1f %%/h", it)
                        } ?: "A recolher (< 2 h)",
                        style = NexusMonoTextStyles.metricMedium,
                        color = colors.accent2
                    )
                }
            }
        }

        // 4. Apps com mais impacto
        NexusCard {
            Text(
                text = "Apps com maior impacto",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (summary.topImpactApps.isEmpty()) {
                Text(
                    text = "Abre o separador Apps para atualizar o ranking detalhado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            } else {
                summary.topImpactApps.take(5).forEachIndexed { idx, app ->
                    if (idx > 0) {
                        HorizontalDivider(
                            color = colors.border,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconOrMonogram(
                            packageName = app.packageName,
                            monogram = app.monogram
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = app.batteryReasonTextPt,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = NexusFormatters.formatBytes(app.totalStorageBytes),
                            style = NexusMonoTextStyles.metricSmall,
                            color = colors.accent2
                        )
                    }
                }
            }
        }

        NexusPrimaryGradientButton(
            text = "Exportar Relatório Mensal em PDF",
            onClick = {
                pdfLauncher.launch("nexus_guard_relatorio_mensal.pdf")
            }
        )
    }
}
