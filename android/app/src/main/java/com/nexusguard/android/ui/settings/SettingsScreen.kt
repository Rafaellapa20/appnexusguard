package com.nexusguard.android.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.preferences.UserPreferencesState
import com.nexusguard.android.data.repository.AppUpdateUiState
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ScheduledCleanFrequency
import com.nexusguard.android.ui.components.GaugeShieldLogo
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusChip
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.components.NexusTextButton
import com.nexusguard.android.ui.components.NexusToggleRow
import com.nexusguard.android.ui.theme.NexusButtonShape
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferencesState,
    hasUsageStatsPermission: Boolean,
    hasMediaPermission: Boolean,
    hasManageStoragePermission: Boolean,
    hasDndPermission: Boolean,
    updateState: AppUpdateUiState,
    onCheckUpdates: () -> Unit,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleWeeklyScan: (Boolean) -> Unit,
    onSetScheduledCleanSchedule: (Int, Int) -> Unit = { _, _ -> },
    onSetScheduledAutoCleanOptions: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onToggleStorageAlert: (Boolean) -> Unit,
    onToggleJunkOver1GbAlert: (Boolean) -> Unit = {},
    onToggleNewAppBatteryAlert: (Boolean) -> Unit = {},
    onToggleThermalAlert: (Boolean) -> Unit,
    onToggleBatteryAlert: (Boolean) -> Unit,
    onToggleCharge80Alert: (Boolean) -> Unit = {},
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
            borderColor = colors.accent.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Atualizações da Aplicação",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Text(
                        text = "Instalada: v${BuildConfig.VERSION_NAME} (código ${BuildConfig.VERSION_CODE})",
                        style = NexusMonoTextStyles.metricSmall,
                        color = colors.accent2
                    )
                }
                if (updateState.isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent2
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            NexusPrimaryGradientButton(
                text = "Procurar atualizações agora",
                onClick = onCheckUpdates
            )
        }

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
                title = "Limpeza agendada (WorkManager)",
                subtitle = "Analisa e limpa apenas itens autorizados sem confirmação do sistema; notifica com botão «Rever» para o resto",
                checked = preferences.weeklyScanEnabled,
                onCheckedChange = onToggleWeeklyScan
            )
            if (preferences.weeklyScanEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Frequência e hora preferida:",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textDim
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScheduledCleanFrequency.entries.forEach { freq ->
                        NexusChip(
                            text = freq.labelPt,
                            color = if (preferences.scheduledCleanFrequencyDays == freq.intervalDays) colors.accent2 else colors.textDim,
                            onClick = {
                                onSetScheduledCleanSchedule(freq.intervalDays, preferences.scheduledCleanHourOfDay)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(3, 9, 14, 21).forEach { hour ->
                        val label = String.format("%02d:00", hour)
                        NexusChip(
                            text = label,
                            color = if (preferences.scheduledCleanHourOfDay == hour) colors.accent2 else colors.textDim,
                            onClick = {
                                onSetScheduledCleanSchedule(preferences.scheduledCleanFrequencyDays, hour)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                NexusToggleRow(
                    title = "Auto-limpar pastas vazias",
                    subtitle = "Sem confirmação do sistema (ignora .nomedia)",
                    checked = preferences.autoCleanEmptyFolders,
                    onCheckedChange = {
                        onSetScheduledAutoCleanOptions(
                            it,
                            preferences.autoPurgeExpiredTrash,
                            preferences.autoClearOwnCache
                        )
                    }
                )
                NexusToggleRow(
                    title = "Auto-limpar Lixo Nexus expirado (> 30 dias)",
                    subtitle = "Remove definitivamente itens com mais de 30 dias no Lixo Nexus",
                    checked = preferences.autoPurgeExpiredTrash,
                    onCheckedChange = {
                        onSetScheduledAutoCleanOptions(
                            preferences.autoCleanEmptyFolders,
                            it,
                            preferences.autoClearOwnCache
                        )
                    }
                )
                NexusToggleRow(
                    title = "Auto-limpar cache própria da app",
                    subtitle = "Limpa apenas a cache temporária interna do Nexus Guard",
                    checked = preferences.autoClearOwnCache,
                    onCheckedChange = {
                        onSetScheduledAutoCleanOptions(
                            preferences.autoCleanEmptyFolders,
                            preferences.autoPurgeExpiredTrash,
                            it
                        )
                    }
                )
            }
            HorizontalDivider(color = colors.border)
            Text(
                text = "Avisos inteligentes (máx. 1 notificação agrupada/dia)",
                style = MaterialTheme.typography.labelLarge,
                color = colors.accent2
            )
            NexusToggleRow(
                title = "Aviso: Armazenamento < 10 %",
                subtitle = "Avisa quando o espaço livre baixa de 10 %",
                checked = preferences.storageAlertEnabled,
                onCheckedChange = onToggleStorageAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Aviso: Mais de 1 GB de lixo acumulado",
                subtitle = "Avisa quando há mais de 1 GB pronto a limpar",
                checked = preferences.junkOver1GbAlertEnabled,
                onCheckedChange = onToggleJunkOver1GbAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Aviso: App nova a gastar muita bateria (24 h)",
                subtitle = "Deteta apps recentes com consumo elevado nas últimas 24 h",
                checked = preferences.newAppBatteryAlertEnabled,
                onCheckedChange = onToggleNewAppBatteryAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Aviso: Telemóvel quente",
                subtitle = "Avisa quando o estado térmico chega a Grave ou Crítico",
                checked = preferences.thermalAlertEnabled,
                onCheckedChange = onToggleThermalAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Aviso: Carga a ${preferences.chargeLimitTargetPercent} %",
                subtitle = "Avisa quando a bateria atinge o limite configurado",
                checked = preferences.chargeLimitAlarmEnabled,
                onCheckedChange = onToggleCharge80Alert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Aviso: Bateria degradada",
                subtitle = "Avisa se a saúde da bateria deixar de ser Boa",
                checked = preferences.batteryAlertEnabled,
                onCheckedChange = onToggleBatteryAlert
            )
        }

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
                granted = hasUsageStatsPermission,
                onAction = { NexusSystemIntents.openUsageAccessSettings(context) }
            )
            PermissionStatusRow(
                label = "Leitura de Media (MediaStore)",
                granted = hasMediaPermission,
                onAction = { NexusSystemIntents.openAppDetailsSettings(context, context.packageName) }
            )
            PermissionStatusRow(
                label = "Política Não Incomodar (Modo Jogo)",
                granted = hasDndPermission,
                onAction = { NexusSystemIntents.openDndPolicySettings(context) }
            )
            if (BuildConfig.IS_FULL_FLAVOR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                PermissionStatusRow(
                    label = "Todos os ficheiros (MANAGE_EXTERNAL_STORAGE)",
                    granted = hasManageStoragePermission,
                    onAction = { NexusSystemIntents.openManageAllFilesSettings(context) }
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
                text = "100 % local: sem analytics, sem anúncios e sem telemetria. Fontes: JetBrains Mono (OFL) e Roboto Flex.",
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
                imageVector = NexusIcons.Info,
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
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDim,
            modifier = Modifier.weight(1f)
        )
        NexusTextButton(
            text = if (granted) "Concedida" else "Autorizar",
            color = if (granted) colors.ok else colors.accent2,
            onClick = onAction
        )
    }
}

@Composable
fun AppUpdatePromptDialog(
    updateState: AppUpdateUiState,
    onDownloadAndInstall: () -> Unit,
    onDownloadViaSystemManager: () -> Unit,
    onInstallReadyApk: () -> Unit,
    onOpenBrowser: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NexusTheme.colors
    val remote = updateState.remoteInfo

    AlertDialog(
        onDismissRequest = {
            if (!updateState.isDownloading) {
                onDismiss()
            }
        },
        containerColor = colors.surface,
        titleContentColor = colors.text,
        textContentColor = colors.textDim,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GaugeShieldLogo(size = 32.dp)
                Column {
                    Text(
                        text = if (updateState.isNewerThanInstalled) {
                            "Nova atualização disponível"
                        } else {
                            "Atualização do Nexus Guard"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Text(
                        text = "Repositório oficial · GitHub",
                        style = NexusMonoTextStyles.badge,
                        color = colors.accent2
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.bgRaised)
                        .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Instalada",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )
                        Text(
                            text = "v${updateState.currentVersionName} (${updateState.currentVersionCode})",
                            style = NexusMonoTextStyles.metricSmall,
                            color = colors.textDim
                        )
                    }
                    Text(
                        text = "→",
                        style = NexusMonoTextStyles.metricMedium,
                        color = colors.accent2
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Disponível",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )
                        Text(
                            text = "v${remote?.versionName ?: updateState.currentVersionName} (${remote?.versionCode ?: updateState.currentVersionCode})",
                            style = NexusMonoTextStyles.metricSmall,
                            color = colors.ok
                        )
                    }
                }

                if (!remote?.releaseNotes.isNullOrBlank()) {
                    Text(
                        text = "Novidades desta versão:",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.text
                    )
                    Text(
                        text = remote?.releaseNotes.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                }

                if (updateState.isDownloading) {
                    val pct = (updateState.downloadProgress * 100f).toInt()
                    val dlStr = NexusFormatters.formatBytes(updateState.downloadedBytes)
                    val totalStr = if (updateState.totalBytes > 0L) {
                        NexusFormatters.formatBytes(updateState.totalBytes)
                    } else {
                        "..."
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "A transferir APK...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.accent2
                            )
                            Text(
                                text = "$pct % ($dlStr / $totalStr)",
                                style = NexusMonoTextStyles.badge,
                                color = colors.text
                            )
                        }
                        LinearProgressIndicator(
                            progress = { updateState.downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = colors.accent2,
                            trackColor = colors.surfaceAlt
                        )
                    }
                }

                updateState.statusMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent2
                    )
                }

                updateState.errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.danger
                    )
                }

                if (updateState.downloadedApkFile != null && !updateState.isDownloading) {
                    NexusPrimaryGradientButton(
                        text = "Instalar atualização transferida",
                        onClick = onInstallReadyApk
                    )
                } else if (!updateState.isDownloading) {
                    NexusPrimaryGradientButton(
                        text = "Atualizar agora",
                        onClick = onDownloadAndInstall
                    )
                    OutlinedButton(
                        onClick = onDownloadViaSystemManager,
                        shape = NexusButtonShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Transferir pelo Gestor do Android",
                            color = colors.accent2
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!updateState.isDownloading) {
                TextButton(onClick = onOpenBrowser) {
                    Text("Abrir no navegador", color = colors.accent2)
                }
            }
        },
        dismissButton = {
            if (!updateState.isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Mais tarde", color = colors.textMuted)
                }
            }
        }
    )
}
