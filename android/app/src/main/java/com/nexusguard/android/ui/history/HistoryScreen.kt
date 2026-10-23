package com.nexusguard.android.ui.history

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusEmptyStateCard
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.theme.NexusButtonShape
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    entries: List<HistoryEntity>,
    buildRestoreIntent: (HistoryEntity) -> IntentSender?,
    onRestoreConfirmed: (Long) -> Unit,
    onExportPdf: (Uri, (Boolean) -> Unit) -> Unit,
    onOpenMonthlyReport: () -> Unit = {}
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "monthly_report_button") {
            OutlinedButton(
                onClick = onOpenMonthlyReport,
                shape = NexusButtonShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ver Relatório Mensal (30 dias + PDF)",
                    color = colors.accent2
                )
            }
        }

        item(key = "export_pdf_button") {
            NexusPrimaryGradientButton(
                text = "Exportar Histórico em PDF",
                leadingIcon = NexusIcons.PictureAsPdf,
                onClick = {
                    pdfExportLauncher.launch("NexusGuard-Historico.pdf")
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (entries.isEmpty()) {
            item(key = "empty_history") {
                NexusEmptyStateCard(
                    title = "Histórico vazio",
                    subtitle = "Todas as análises, limpezas e sessões executadas no telemóvel ficam registadas aqui na base de dados local (Room).",
                    icon = NexusIcons.History
                )
            }
        } else {
            items(
                items = entries,
                key = { it.id }
            ) { entry ->
                val restoreUntilDate = remember(entry.timestampEpochMillis) {
                    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-PT"))
                    fmt.format(Date(entry.timestampEpochMillis + 30L * 24L * 3600_000L))
                }
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Pode ser restaurado até $restoreUntilDate",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent2
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val sender = buildRestoreIntent(entry)
                                if (sender != null) {
                                    pendingRestoreId = entry.id
                                    restoreLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                } else {
                                    onRestoreConfirmed(entry.id)
                                    Toast.makeText(context, "Ficheiros restaurados do Lixo Nexus!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = NexusButtonShape
                        ) {
                            Icon(
                                imageVector = NexusIcons.RestoreFromTrash,
                                contentDescription = null,
                                tint = colors.accent2,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restaurar", color = colors.text)
                        }
                    } else if (entry.isRestored) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("✓ Libertado e restaurado do Lixo", style = NexusMonoTextStyles.badge, color = colors.ok)
                    }
                }
            }
        }
    }
}
