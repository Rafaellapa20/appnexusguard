package com.nexusguard.android.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.nexusguard.android.ui.components.GaugeShieldLogo
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.components.NexusTextButton
import com.nexusguard.android.ui.components.NexusToggleRow
import com.nexusguard.android.ui.theme.NexusButtonShape
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme

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
    onToggleStorageAlert: (Boolean) -> Unit,
    onToggleThermalAlert: (Boolean) -> Unit,
    onToggleBatteryAlert: (Boolean) -> Unit,
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
                title = "Análise semanal agendada (WorkManager)",
                subtitle = "Executa verificação em segundo plano 1x por semana",
                checked = preferences.weeklyScanEnabled,
                onCheckedChange = onToggleWeeklyScan
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Notificação: Armazenamento < 10 %",
                subtitle = "Canal dedicado de alerta de armazenamento",
                checked = preferences.storageAlertEnabled,
                onCheckedChange = onToggleStorageAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Notificação: Telemóvel quente",
                subtitle = "Avisa quando o estado térmico chega a Grave ou Crítico",
                checked = preferences.thermalAlertEnabled,
                onCheckedChange = onToggleThermalAlert
            )
            HorizontalDivider(color = colors.border)
            NexusToggleRow(
                title = "Notificação: Bateria degradada",
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
