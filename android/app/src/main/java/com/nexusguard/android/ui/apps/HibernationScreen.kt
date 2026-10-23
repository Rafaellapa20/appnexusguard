package com.nexusguard.android.ui.apps

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusguard.android.data.preferences.UserPreferencesState
import com.nexusguard.android.data.repository.HibernationStateSnapshot
import com.nexusguard.android.domain.ForceStopBackendType
import com.nexusguard.android.domain.HibernationAppProgressItem
import com.nexusguard.android.domain.HibernationAppStepStatus
import com.nexusguard.android.domain.HibernationCandidateApp
import com.nexusguard.android.domain.HibernationExecutionSummary
import com.nexusguard.android.ui.components.AppIconOrMonogram
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusChip
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusTextButton
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HibernationScreen(
    state: HibernationStateSnapshot,
    prefs: UserPreferencesState,
    isExecuting: Boolean,
    progressItems: ImmutableList<HibernationAppProgressItem>,
    lastSummary: HibernationExecutionSummary?,
    onBack: () -> Unit,
    onExecuteHibernationNow: () -> Unit,
    onCancelHibernation: () -> Unit,
    onDismissSummary: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onAcceptAccessibilityDisclosureAndOpenSettings: () -> Unit,
    onToggleAutoHibernation: (Boolean) -> Unit,
    onUpdateAutoConfig: (onScreenOff: Boolean, periodicHours: Int, beforeGameBoost: Boolean, top10Only: Boolean) -> Unit,
    onToggleNeverClosePackage: (String) -> Unit,
    onToggleAlwaysHibernatePackage: (String) -> Unit,
    onRefreshState: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val view = LocalView.current

    var selectedListTab by remember { mutableIntStateOf(0) } // 0 = Candidatas, 1 = Protegidas / Nunca fechar
    var showUpgradeBackendDialog by remember { mutableStateOf(false) }
    var showAccessibilityDisclosureDialog by remember { mutableStateOf(false) }
    var showAutoModeWarningDialog by remember { mutableStateOf(false) }

    val badgeColor = when (state.activeBackend) {
        ForceStopBackendType.SHIZUKU -> colors.ok
        ForceStopBackendType.ACCESSIBILITY -> colors.accent2
        ForceStopBackendType.BASIC -> colors.warn
        ForceStopBackendType.NONE_PLAY -> colors.textDim
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cabeçalho com botão Voltar e estado do backend
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = colors.surfaceAlt,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { onBack() }
                        ) {
                            Text(
                                text = "← Apps",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent2,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Text(
                            text = if (state.isFullFlavor) "Hibernação de apps" else "Restringir em fundo",
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.text
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (state.isFullFlavor) {
                            "Para aplicações em segundo plano sem root, preservando alarmes, teclado e sistema."
                        } else {
                            "Atalhos diretos para restringir bateria ou forçar paragem nas Definições do Android."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                }
            }
        }

        // Cartão de Estado do Motor (No flavor full mostra Shizuku / Acessibilidade / Básico + botão Melhorar)
        item {
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MOTOR ATIVO",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        NexusChip(
                            text = state.activeBackend.badgeLabelPt,
                            color = badgeColor,
                            onClick = {}
                        )
                    }
                    if (state.isFullFlavor && state.activeBackend != ForceStopBackendType.SHIZUKU) {
                        NexusTextButton(
                            text = "Melhorar",
                            color = colors.accent2,
                            onClick = { showUpgradeBackendDialog = true }
                        )
                    } else {
                        NexusTextButton(
                            text = "Atualizar",
                            color = colors.textDim,
                            onClick = onRefreshState
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when (state.activeBackend) {
                        ForceStopBackendType.SHIZUKU ->
                            "Execução silenciosa via ADB local (Shizuku): fecha ~20 apps em menos de 1 segundo sem abrir janelas."
                        ForceStopBackendType.ACCESSIBILITY ->
                            "Automação visível nas Definições: abre cada app, toca em «Forçar paragem» e confirma (~0,6 s por app)."
                        ForceStopBackendType.BASIC ->
                            "Modo Básico (killBackgroundProcesses): liberta processos em cache, mas o Android pode reiniciar serviços ativos."
                        ForceStopBackendType.NONE_PLAY ->
                            "Edição Google Play: sem serviços de acessibilidade nem fecho automático em lote. Usa os atalhos individuais abaixo."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
            }
        }

        // Cartão Principal de Execução («Fechar apps agora (N)») no flavor full
        if (state.isFullFlavor) {
            item {
                NexusCard(borderColor = colors.accent.copy(alpha = 0.55f)) {
                    Text(
                        text = "Fecho manual em 1 toque",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.recommendedCandidates.size} apps candidatas prontas a hibernar (${state.protectedApps.size} protegidas ignoradas).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    if (isExecuting) {
                        val completedCount = progressItems.count {
                            it.status == HibernationAppStepStatus.STOPPED ||
                                it.status == HibernationAppStepStatus.ALREADY_STOPPED ||
                                it.status == HibernationAppStepStatus.FAILED
                        }
                        val totalCount = progressItems.size.coerceAtLeast(1)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "A hibernar $completedCount de ${progressItems.size}…",
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.accent2
                            )
                            TextButton(onClick = onCancelHibernation) {
                                Text("Cancelar", color = colors.danger)
                            }
                        }
                        LinearProgressIndicator(
                            progress = { (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.accent2,
                            trackColor = colors.border
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        progressItems.take(8).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.appName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                val statusColor = when (item.status) {
                                    HibernationAppStepStatus.STOPPED,
                                    HibernationAppStepStatus.ALREADY_STOPPED -> colors.ok
                                    HibernationAppStepStatus.FAILED -> colors.danger
                                    HibernationAppStepStatus.RUNNING -> colors.accent2
                                    HibernationAppStepStatus.PENDING -> colors.textMuted
                                }
                                Text(
                                    text = item.status.symbolPt,
                                    style = NexusMonoTextStyles.metricSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }
                        }
                    } else {
                        NexusPrimaryGradientButton(
                            text = "Fechar apps agora (${state.recommendedCandidates.size})",
                            enabled = state.recommendedCandidates.isNotEmpty(),
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onExecuteHibernationNow()
                            }
                        )
                    }
                }
            }

            // Resumo Honesto da Última Execução
            if (lastSummary != null) {
                item {
                    NexusCard(borderColor = colors.ok.copy(alpha = 0.6f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NexusChip(
                                text = "Concluído · ${lastSummary.backendType.shortNamePt}",
                                color = colors.ok,
                                onClick = {}
                            )
                            TextButton(onClick = onDismissSummary) {
                                Text("Fechar", color = colors.textDim)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lastSummary.honestSummaryCopyPt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Paradas: ${lastSummary.stoppedCount} · Já paradas: ${lastSummary.alreadyStoppedCount} · Falhadas: ${lastSummary.failedCount}",
                            style = NexusMonoTextStyles.metricSmall,
                            color = colors.textDim
                        )
                    }
                }
            }

            // Cartão de Modo Automático (Secção 6)
            item {
                NexusCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo automático",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.text
                            )
                            Text(
                                text = if (prefs.autoHibernationEnabled) {
                                    "Ativo · hiberna apenas candidatas autorizadas"
                                } else {
                                    "Desligado por omissão"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textDim
                            )
                        }
                        Switch(
                            checked = prefs.autoHibernationEnabled,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable && !prefs.autoHibernationEnabled) {
                                    showAutoModeWarningDialog = true
                                } else {
                                    onToggleAutoHibernation(false)
                                }
                            }
                        )
                    }

                    // Comparação real 3 dias antes vs 3 dias depois (%/h em repouso)
                    if (state.idleDrainComparisonTextPt != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        NexusChip(
                            text = state.idleDrainComparisonTextPt,
                            color = if (state.idleDrainComparisonTextPt.startsWith("−")) colors.ok else colors.warn,
                            onClick = {}
                        )
                    }

                    if (prefs.autoHibernationEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Gatilhos automáticos:",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textDim
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateAutoConfig(
                                        !prefs.autoHibernateOnScreenOff,
                                        prefs.autoHibernatePeriodicHours,
                                        prefs.autoHibernateBeforeGameBoost,
                                        prefs.autoHibernateTop10BatteryOnly
                                    )
                                }
                        ) {
                            Checkbox(
                                checked = prefs.autoHibernateOnScreenOff,
                                onCheckedChange = { checked ->
                                    onUpdateAutoConfig(
                                        checked,
                                        prefs.autoHibernatePeriodicHours,
                                        prefs.autoHibernateBeforeGameBoost,
                                        prefs.autoHibernateTop10BatteryOnly
                                    )
                                }
                            )
                            Text(
                                text = "1 min depois de o ecrã desligar (Shizuku / Básico)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.text
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateAutoConfig(
                                        prefs.autoHibernateOnScreenOff,
                                        prefs.autoHibernatePeriodicHours,
                                        !prefs.autoHibernateBeforeGameBoost,
                                        prefs.autoHibernateTop10BatteryOnly
                                    )
                                }
                        ) {
                            Checkbox(
                                checked = prefs.autoHibernateBeforeGameBoost,
                                onCheckedChange = { checked ->
                                    onUpdateAutoConfig(
                                        prefs.autoHibernateOnScreenOff,
                                        prefs.autoHibernatePeriodicHours,
                                        checked,
                                        prefs.autoHibernateTop10BatteryOnly
                                    )
                                }
                            )
                            Text(
                                text = "Antes de ativar o Modo Jogo",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.text
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdateAutoConfig(
                                        prefs.autoHibernateOnScreenOff,
                                        prefs.autoHibernatePeriodicHours,
                                        prefs.autoHibernateBeforeGameBoost,
                                        !prefs.autoHibernateTop10BatteryOnly
                                    )
                                }
                        ) {
                            Checkbox(
                                checked = prefs.autoHibernateTop10BatteryOnly,
                                onCheckedChange = { checked ->
                                    onUpdateAutoConfig(
                                        prefs.autoHibernateOnScreenOff,
                                        prefs.autoHibernatePeriodicHours,
                                        prefs.autoHibernateBeforeGameBoost,
                                        checked
                                    )
                                }
                            )
                            Text(
                                text = "Só as apps com mais impacto (top 10 bateria 24 h)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.text
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Intervalo periódico:",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textDim
                            )
                            listOf(6, 12, 24).forEach { hours ->
                                NexusChip(
                                    text = "A cada $hours h",
                                    color = if (prefs.autoHibernatePeriodicHours == hours) colors.accent2 else colors.textDim,
                                    onClick = {
                                        onUpdateAutoConfig(
                                            prefs.autoHibernateOnScreenOff,
                                            hours,
                                            prefs.autoHibernateBeforeGameBoost,
                                            prefs.autoHibernateTop10BatteryOnly
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Separadores das Listas: Candidatas vs Protegidas («Nunca fechar»)
        item {
            Text(
                text = "Aplicações e regras de proteção",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NexusChip(
                    text = "Candidatas (${state.recommendedCandidates.size})",
                    color = if (selectedListTab == 0) colors.accent2 else colors.textDim,
                    onClick = { selectedListTab = 0 }
                )
                NexusChip(
                    text = "Nunca fechar / Protegidas (${state.protectedApps.size})",
                    color = if (selectedListTab == 1) colors.accent2 else colors.textDim,
                    onClick = { selectedListTab = 1 }
                )
            }
        }

        val currentList = if (selectedListTab == 0) state.recommendedCandidates else state.protectedApps

        items(currentList, key = { it.packageName }) { app ->
            HibernationAppItemCard(
                app = app,
                isFullFlavor = state.isFullFlavor,
                onToggleNeverClose = { onToggleNeverClosePackage(app.packageName) },
                onToggleAlwaysHibernate = { onToggleAlwaysHibernatePackage(app.packageName) },
                onOpenSystemAppSettings = {
                    runCatching {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            )
        }
    }

    // Diálogo guiado «Melhorar» (Shizuku + Serviço de Acessibilidade)
    if (showUpgradeBackendDialog) {
        AlertDialog(
            onDismissRequest = { showUpgradeBackendDialog = false },
            containerColor = colors.surface,
            title = { Text("Melhorar motor de hibernação", color = colors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "1. Shizuku (Recomendado · Instantâneo e silencioso)",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.accent2
                    )
                    Text(
                        text = "Usa o serviço ADB local sem root (Depuração por Wi-Fi no Android 11+) para correr «am force-stop» em menos de 1 segundo para 20 apps, sem tocar no ecrã.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                    if (state.shizukuWaitingPermission) {
                        NexusPrimaryGradientButton(
                            text = "Autorizar Shizuku agora",
                            onClick = {
                                showUpgradeBackendDialog = false
                                onRequestShizukuPermission()
                            }
                        )
                    } else if (!state.shizukuInstalled) {
                        NexusTextButton(
                            text = "Instalar Shizuku (GitHub / Play)",
                            color = colors.accent2,
                            onClick = {
                                showUpgradeBackendDialog = false
                                runCatching {
                                    val uri = Uri.parse("https://shizuku.rikka.app/download/")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "O Shizuku está instalado mas o serviço não está iniciado. Abre a app Shizuku e toca em «Iniciar via Depuração por Wi-Fi».",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.warn
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "2. Serviço de Acessibilidade (Alternativa sem Shizuku)",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.accent
                    )
                    Text(
                        text = "Abre automaticamente as Definições de cada app e toca em «Forçar paragem» (~0,6 s por app, com barra «Cancelar»).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                    NexusTextButton(
                        text = if (state.accessibilityServiceEnabled) {
                            "Serviço de Acessibilidade já ativo"
                        } else {
                            "Ativar Serviço de Acessibilidade"
                        },
                        color = colors.accent2,
                        onClick = {
                            showUpgradeBackendDialog = false
                            if (prefs.accessibilityDisclosureAccepted) {
                                onAcceptAccessibilityDisclosureAndOpenSettings()
                            } else {
                                showAccessibilityDisclosureDialog = true
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showUpgradeBackendDialog = false }) {
                    Text("Fechar", color = colors.accent2)
                }
            }
        )
    }

    // Ecrã / Diálogo de Divulgação Explícita Obrigatória antes das Definições de Acessibilidade
    if (showAccessibilityDisclosureDialog) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosureDialog = false },
            containerColor = colors.surface,
            title = { Text("Divulgação do Serviço de Acessibilidade", color = colors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "O que este serviço faz:",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.ok
                    )
                    Text(
                        text = "• Toca no botão «Forçar paragem» nas Definições do sistema apenas quando pedes para hibernar apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "O que este serviço NÃO faz:",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.danger
                    )
                    Text(
                        text = "• Não lê mensagens, palavras-passe, notificações nem conteúdo de outras apps.\n• Escuta exclusivamente o pacote das Definições do sistema durante a hibernação.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.text
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccessibilityDisclosureDialog = false
                        onAcceptAccessibilityDisclosureAndOpenSettings()
                    }
                ) {
                    Text("Aceito", color = colors.ok, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosureDialog = false }) {
                    Text("Cancelar", color = colors.textDim)
                }
            }
        )
    }

    // Aviso honesto único ao ligar o Modo Automático (Secção 6)
    if (showAutoModeWarningDialog) {
        AlertDialog(
            onDismissRequest = { showAutoModeWarningDialog = false },
            containerColor = colors.surface,
            title = { Text("Ativar hibernação automática?", color = colors.text) },
            text = {
                Text(
                    text = "Fechar apps com frequência pode aumentar o consumo se as voltares a abrir logo a seguir. Recomendamos aplicar apenas às apps que não usas mas correm em fundo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textDim
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAutoModeWarningDialog = false
                        onToggleAutoHibernation(true)
                    }
                ) {
                    Text("Ativar modo automático", color = colors.accent2, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoModeWarningDialog = false }) {
                    Text("Cancelar", color = colors.textDim)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HibernationAppItemCard(
    app: HibernationCandidateApp,
    isFullFlavor: Boolean,
    onToggleNeverClose: () -> Unit,
    onToggleAlwaysHibernate: () -> Unit,
    onOpenSystemAppSettings: () -> Unit
) {
    val colors = NexusTheme.colors
    NexusCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppIconOrMonogram(
                packageName = app.packageName,
                monogram = app.monogram,
                iconSizeDp = 40
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = NexusMonoTextStyles.metricSmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (app.protectionReasonPt != null) {
                        NexusChip(text = app.protectionReasonPt, color = colors.ok, onClick = {})
                    }
                    if (app.hasUnstoppedForegroundService) {
                        NexusChip(text = "Serviço em 1.º plano", color = colors.danger, onClick = {})
                    }
                    if (app.isTop10Battery24h) {
                        NexusChip(text = "Top 10 bateria (24 h)", color = colors.warn, onClick = {})
                    }
                    if (app.hasActivityInLast3Hours) {
                        NexusChip(text = "Ativa nas últimas 3 h", color = colors.accent2, onClick = {})
                    }
                    if (app.isInAlwaysHibernateList) {
                        NexusChip(text = "Hibernar sempre", color = colors.accent, onClick = {})
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isFullFlavor) {
                NexusTextButton(
                    text = "Abrir nas Definições",
                    color = colors.accent2,
                    onClick = onOpenSystemAppSettings
                )
            } else {
                val isUserNeverClose = app.protectionReasonPt == "Marcada como «Nunca fechar»"
                if (!app.isProtected) {
                    TextButton(
                        onClick = onToggleAlwaysHibernate,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text(
                            text = if (app.isInAlwaysHibernateList) "Remover de «Sempre»" else "Hibernar sempre",
                            color = colors.accent2
                        )
                    }
                    TextButton(
                        onClick = onToggleNeverClose,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Nunca fechar", color = colors.textDim)
                    }
                } else if (isUserNeverClose) {
                    TextButton(
                        onClick = onToggleNeverClose,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Remover de «Nunca fechar»", color = colors.warn)
                    }
                }
                TextButton(
                    onClick = onOpenSystemAppSettings,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Definições", color = colors.textMuted)
                }
            }
        }
    }
}
