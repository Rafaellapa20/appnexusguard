package com.nexusguard.android.ui.apps

import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusguard.android.domain.AppImpactCalculator
import com.nexusguard.android.domain.AppImpactFilterView
import com.nexusguard.android.domain.AppImpactItem
import com.nexusguard.android.domain.AppImpactPeriod
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.UnusedThresholdDays
import com.nexusguard.android.ui.AppsImpactUiState
import com.nexusguard.android.ui.components.AppIconOrMonogram
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusCheckbox48dp
import com.nexusguard.android.ui.components.NexusChip
import com.nexusguard.android.ui.components.NexusEmptyStateCard
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusShimmerBox
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.components.NexusTextButton
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsImpactScreen(
    uiState: AppsImpactUiState,
    onSelectPeriod: (AppImpactPeriod) -> Unit,
    onRefresh: () -> Unit,
    onBatchUninstallFinished: (List<AppImpactItem>, (Int, Long) -> Unit) -> Unit,
    onOpenHibernation: (() -> Unit)? = null
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val view = LocalView.current

    var selectedView by remember { mutableStateOf(AppImpactFilterView.BATTERY) }
    var unusedThreshold by remember { mutableStateOf(UnusedThresholdDays.DAYS_30) }
    var detailApp by remember { mutableStateOf<AppImpactItem?>(null) }

    // Seleção múltipla para desinstalação em lote
    val selectedPackagesForBatch = remember { mutableStateListOf<String>() }
    var batchQueue by remember { mutableStateOf<List<AppImpactItem>>(emptyList()) }
    var batchCurrentIdx by remember { mutableIntStateOf(-1) }

    val batchUninstallLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val nextIdx = batchCurrentIdx + 1
        if (nextIdx < batchQueue.size) {
            batchCurrentIdx = nextIdx
            val nextApp = batchQueue[nextIdx]
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${nextApp.packageName}"))
            try {
                // Lança o próximo diálogo do sistema na sequência
                // (executado via LaunchedEffect abaixo para evitar recursão direta no callback)
            } catch (_: Exception) {
            }
        } else {
            val completedApps = batchQueue
            batchQueue = emptyList()
            batchCurrentIdx = -1
            selectedPackagesForBatch.clear()
            onBatchUninstallFinished(completedApps) { removedCount, freedBytes ->
                val msg = if (removedCount > 0) {
                    "$removedCount app(s) desinstalada(s) · ${NexusFormatters.formatBytes(freedBytes)} libertados."
                } else {
                    "Nenhuma aplicação foi desinstalada."
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Dispara sequencialmente o Intent.ACTION_DELETE quando batchCurrentIdx avança
    androidx.compose.runtime.LaunchedEffect(batchCurrentIdx, batchQueue) {
        if (batchCurrentIdx in batchQueue.indices) {
            val targetApp = batchQueue[batchCurrentIdx]
            val deleteIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${targetApp.packageName}"))
            batchUninstallLauncher.launch(deleteIntent)
        }
    }

    // Ecrã de detalhe da aplicação com as 4 métricas + gráfico 7 dias + 4 ações reais
    AnimatedContent(
        targetState = detailApp,
        transitionSpec = {
            fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)) togetherWith
                fadeOut(spring(dampingRatio = 0.8f, stiffness = 400f))
        },
        label = "appImpactDetailTransition"
    ) { activeDetail ->
        if (activeDetail != null) {
            AppImpactDetailView(
                app = activeDetail,
                hasUsageStatsPermission = uiState.hasUsageStatsPermission,
                onBack = { detailApp = null },
                onUninstall = {
                    NexusSystemIntents.requestUninstallPackage(context, activeDetail.packageName)
                }
            )
            return@AnimatedContent
        }

        val displayedApps = remember(uiState.apps, selectedView, unusedThreshold) {
            when (selectedView) {
                AppImpactFilterView.BATTERY -> {
                    uiState.apps
                        .filter { !it.isSystemApp || it.foregroundMillis > 0L || it.backgroundServiceMillis > 0L }
                        .sortedByDescending { it.batteryImpactScoreMillis }
                }
                AppImpactFilterView.NETWORK -> {
                    uiState.apps
                        .sortedByDescending { it.totalNetworkBytes }
                }
                AppImpactFilterView.STORAGE -> {
                    uiState.apps
                        .sortedByDescending { it.totalStorageBytes }
                }
                AppImpactFilterView.UNUSED -> {
                    AppImpactCalculator.filterUnusedApps(uiState.apps, unusedThreshold.days)
                }
                AppImpactFilterView.BLOATWARE -> {
                    uiState.apps
                        .filter { it.canBeDisabledBloatware }
                        .sortedByDescending { it.totalStorageBytes }
                }
            }
        }

        val selectedAppItems = remember(uiState.apps, selectedPackagesForBatch.toList()) {
            uiState.apps.filter { it.packageName in selectedPackagesForBatch && !it.isSystemApp }
        }
        val selectedBatchBytes = selectedAppItems.sumOf { it.totalStorageBytes }

        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cabeçalho com período (24 h / 7 dias / 30 dias) e atalho para Hibernação (quando disponível)
                item(key = "header_period_and_hibernation") {
                    NexusCard(
                        backgroundColor = colors.surface,
                        borderColor = colors.borderStrong,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Período de análise",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textDim
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AppImpactPeriod.entries.forEach { period ->
                                        val isCurrent = uiState.selectedPeriod == period
                                        NexusChip(
                                            text = period.labelPt,
                                            color = if (isCurrent) colors.accent2 else colors.textDim,
                                            onClick = { onSelectPeriod(period) }
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(
                                    imageVector = NexusIcons.Refresh,
                                    contentDescription = "Atualizar impacto das apps",
                                    tint = colors.accent2
                                )
                            }
                        }

                        if (onOpenHibernation != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            NexusPrimaryGradientButton(
                                text = "Hibernação de apps (Fechar em fundo)",
                                onClick = onOpenHibernation,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Chips das 4 vistas principais + Pré-instaladas (Bloatware)
                item(key = "filter_views_row") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppImpactFilterView.entries.forEach { filterView ->
                            val isSelected = selectedView == filterView
                            NexusChip(
                                text = filterView.labelPt,
                                color = if (isSelected) colors.accent2 else colors.textDim,
                                onClick = { selectedView = filterView }
                            )
                        }
                    }
                }

                // Se a vista for UNUSED, mostra sub-filtro 30 / 60 / 90 dias e o total recuperável
                if (selectedView == AppImpactFilterView.UNUSED) {
                    item(key = "unused_threshold_bar") {
                        val recoverableBytes = displayedApps.sumOf { it.totalStorageBytes }
                        NexusCard(
                            backgroundColor = colors.surfaceAlt,
                            borderColor = colors.border,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text(
                                text = "Recuperável: ${NexusFormatters.formatBytes(recoverableBytes)} (${displayedApps.size} apps)",
                                style = NexusMonoTextStyles.metricMedium,
                                color = colors.warn
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UnusedThresholdDays.entries.forEach { threshold ->
                                    NexusChip(
                                        text = threshold.labelPt,
                                        color = if (unusedThreshold == threshold) colors.warn else colors.textDim,
                                        onClick = { unusedThreshold = threshold }
                                    )
                                }
                            }
                        }
                    }
                }

                // Se a vista for BLOATWARE, mostra aviso honesto
                if (selectedView == AppImpactFilterView.BLOATWARE) {
                    item(key = "bloatware_honest_note") {
                        NexusCard(
                            backgroundColor = colors.surfaceAlt,
                            borderColor = colors.border,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text(
                                text = "Apps do sistema pré-instaladas que podem ser desativadas",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = colors.text
                            )
                            Text(
                                text = "Sem root, o Android não permite remover apps da partição do sistema, mas podes desativá-las nas definições para que não corram nem ocupem cache.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textDim
                            )
                        }
                    }
                }

                // Estado claro «sem permissão» se PACKAGE_USAGE_STATS não estiver concedido
                if (!uiState.hasUsageStatsPermission) {
                    item(key = "missing_usage_stats_permission") {
                        NexusCard(
                            backgroundColor = colors.surfaceAlt,
                            borderColor = colors.warn,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Text(
                                text = "Requer Acesso a Dados de Utilização",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.text
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Para ler o tempo em primeiro plano, serviços em fundo (UsageStatsManager), tráfego de rede por UID (NetworkStatsManager) e tamanho real de dados/cache (StorageStatsManager), concede esta permissão.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textDim
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            NexusPrimaryGradientButton(
                                text = "Conceder acesso a utilização",
                                onClick = { NexusSystemIntents.openUsageAccessSettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Cartão de progresso da desinstalação em lote («3 de 7»)
                if (batchCurrentIdx in batchQueue.indices) {
                    item(key = "batch_uninstall_progress") {
                        val stepText = AppImpactCalculator.formatBatchStepProgressPt(
                            oneBasedIndex = batchCurrentIdx + 1,
                            total = batchQueue.size
                        )
                        NexusCard(
                            backgroundColor = colors.surface,
                            borderColor = colors.accent2,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Text(
                                text = "Desinstalação em lote · $stepText",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.accent2
                            )
                            Text(
                                text = "Confirma no diálogo do sistema para ${batchQueue[batchCurrentIdx].appName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textDim
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (batchCurrentIdx + 1).toFloat() / batchQueue.size.coerceAtLeast(1).toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.accent2,
                                trackColor = colors.surfaceAlt
                            )
                        }
                    }
                }

                if (uiState.isLoading) {
                    items(4) { idx ->
                        NexusCard(
                            backgroundColor = colors.surface,
                            borderColor = colors.border,
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            NexusShimmerBox(width = 160.dp, height = 20.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            NexusShimmerBox(width = 220.dp, height = 14.dp)
                        }
                    }
                } else if (displayedApps.isEmpty()) {
                    item(key = "empty_apps_view") {
                        NexusEmptyStateCard(
                            title = "Nenhuma aplicação encontrada neste filtro",
                            subtitle = "Muda o período (24 h / 7 d / 30 d) ou seleciona outra vista de impacto.",
                            icon = NexusIcons.AppsGrid
                        )
                    }
                } else {
                    items(
                        items = displayedApps,
                        key = { it.packageName }
                    ) { app ->
                        val isChecked = app.packageName in selectedPackagesForBatch
                        AppImpactRowCard(
                            app = app,
                            activeView = selectedView,
                            hasUsageStatsPermission = uiState.hasUsageStatsPermission,
                            isCheckedForBatch = isChecked,
                            onToggleBatch = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                if (isChecked) {
                                    selectedPackagesForBatch.remove(app.packageName)
                                } else {
                                    selectedPackagesForBatch.add(app.packageName)
                                }
                            },
                            onOpenDetail = { detailApp = app },
                            onDisableSystemApp = {
                                NexusSystemIntents.openAppDetailsSettings(context, app.packageName)
                            }
                        )
                    }
                }
            }

            // Barra inferior de Desinstalação em lote quando há apps selecionadas
            if (selectedAppItems.isNotEmpty() && batchQueue.isEmpty()) {
                Surface(
                    color = colors.bgRaised,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        NexusPrimaryGradientButton(
                            text = "Desinstalar ${selectedAppItems.size} app(s) · ${NexusFormatters.formatBytes(selectedBatchBytes)}",
                            onClick = {
                                batchQueue = selectedAppItems
                                batchCurrentIdx = 0
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Abre a confirmação oficial do Android uma a uma e regista no Histórico apenas as removidas.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppImpactRowCard(
    app: AppImpactItem,
    activeView: AppImpactFilterView,
    hasUsageStatsPermission: Boolean,
    isCheckedForBatch: Boolean,
    onToggleBatch: () -> Unit,
    onOpenDetail: () -> Unit,
    onDisableSystemApp: () -> Unit
) {
    val colors = NexusTheme.colors
    val metricRightText = when (activeView) {
        AppImpactFilterView.BATTERY -> {
            if (!hasUsageStatsPermission) "Sem permissão"
            else AppImpactCalculator.formatDurationPt(app.foregroundMillis + app.backgroundServiceMillis)
        }
        AppImpactFilterView.NETWORK -> {
            if (!hasUsageStatsPermission) "Sem permissão"
            else NexusFormatters.formatBytes(app.totalNetworkBytes)
        }
        AppImpactFilterView.STORAGE, AppImpactFilterView.UNUSED, AppImpactFilterView.BLOATWARE -> {
            NexusFormatters.formatBytes(app.totalStorageBytes)
        }
    }

    val subtitleText = when (activeView) {
        AppImpactFilterView.BATTERY -> app.batteryReasonTextPt
        AppImpactFilterView.NETWORK -> {
            if (!hasUsageStatsPermission) {
                "Requer acesso a utilização (NetworkStatsManager)"
            } else {
                "1.º plano: ${NexusFormatters.formatBytes(app.totalForegroundNetworkBytes)} · Fundo: ${NexusFormatters.formatBytes(app.totalBackgroundNetworkBytes)}"
            }
        }
        AppImpactFilterView.STORAGE -> {
            "App ${NexusFormatters.formatBytes(app.appCodeBytes)} · Dados ${NexusFormatters.formatBytes(app.dataBytes)} · Cache ${NexusFormatters.formatBytes(app.cacheBytes)} · ${app.storageTrendTextPt}"
        }
        AppImpactFilterView.UNUSED -> {
            val daysStr = app.daysSinceLastUse?.let { "Sem uso há $it dias" } ?: "Requer acesso a utilização"
            "$daysStr · Cache ${NexusFormatters.formatBytes(app.cacheBytes)}"
        }
        AppImpactFilterView.BLOATWARE -> {
            "App pré-instalada do sistema · Podes desativar nas definições"
        }
    }

    NexusCard(
        backgroundColor = colors.surface,
        borderColor = if (isCheckedForBatch) colors.accent2 else colors.border,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        onClick = onOpenDetail
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!app.isSystemApp) {
                NexusCheckbox48dp(
                    checked = isCheckedForBatch,
                    onToggle = onToggleBatch
                )
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            AppIconOrMonogram(
                packageName = app.packageName,
                monogram = app.monogram,
                iconSizeDp = 38
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (activeView == AppImpactFilterView.BLOATWARE) {
                NexusTextButton(
                    text = "Desativar",
                    color = colors.warn,
                    onClick = onDisableSystemApp
                )
            } else {
                Text(
                    text = metricRightText,
                    style = NexusMonoTextStyles.metricSmall,
                    color = colors.accent2
                )
            }
        }
    }
}

@Composable
private fun AppImpactDetailView(
    app: AppImpactItem,
    hasUsageStatsPermission: Boolean,
    onBack: () -> Unit,
    onUninstall: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = NexusIcons.ArrowBack,
                    contentDescription = "Voltar",
                    tint = colors.text
                )
            }
            AppIconOrMonogram(
                packageName = app.packageName,
                monogram = app.monogram,
                iconSizeDp = 42
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text
                )
                Text(
                    text = app.packageName,
                    style = NexusMonoTextStyles.metricSmall,
                    color = colors.textMuted
                )
            }
        }

        // 4 ações reais: Desinstalar, Limpar cache, Restringir em fundo e Abrir
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!app.isSystemApp) {
                NexusChip(
                    text = "Desinstalar",
                    color = colors.danger,
                    onClick = onUninstall,
                    modifier = Modifier.weight(1f)
                )
            } else {
                NexusChip(
                    text = "Desativar",
                    color = colors.warn,
                    onClick = { NexusSystemIntents.openAppDetailsSettings(context, app.packageName) },
                    modifier = Modifier.weight(1f)
                )
            }
            NexusChip(
                text = "Limpar cache",
                color = colors.accent2,
                onClick = { NexusSystemIntents.openAppDetailsSettings(context, app.packageName) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NexusChip(
                text = "Restringir em fundo",
                color = colors.warn,
                onClick = { NexusSystemIntents.openAppDetailsSettings(context, app.packageName) },
                modifier = Modifier.weight(1f)
            )
            NexusChip(
                text = "Abrir app",
                color = colors.ok,
                onClick = {
                    val launch = context.packageManager.getLaunchIntentForPackage(app.packageName)
                    if (launch != null) {
                        context.startActivity(launch)
                    } else {
                        Toast.makeText(context, "Esta app não tem ecrã de abertura direto.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Gráfico simples de 7 dias
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Text(
                text = "Utilização diária (últimos 7 dias)",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (!hasUsageStatsPermission) {
                Text(
                    text = "Requer acesso a utilização para mostrar o gráfico de 7 dias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            } else {
                val maxMin = (app.dailyUsageMinutes7d.maxOrNull() ?: 1).coerceAtLeast(1)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                ) {
                    val count = app.dailyUsageMinutes7d.size.coerceAtLeast(1)
                    val gap = 8.dp.toPx()
                    val barW = ((size.width - gap * (count - 1)) / count).coerceAtLeast(6f)
                    app.dailyUsageMinutes7d.forEachIndexed { idx, mins ->
                        val frac = (mins.toFloat() / maxMin.toFloat()).coerceIn(0.06f, 1f)
                        val barH = size.height * frac
                        val x = idx * (barW + gap)
                        drawRoundRect(
                            color = if (mins > 0) colors.accent2 else colors.surfaceAlt,
                            topLeft = Offset(x, size.height - barH),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }
        }

        // As 4 métricas detalhadas
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Text("1. Impacto na Bateria", style = MaterialTheme.typography.titleMedium, color = colors.text)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.batteryReasonTextPt,
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent2
            )
            Text(
                text = "Primeiro plano: ${AppImpactCalculator.formatDurationPt(app.foregroundMillis)} · Serviço em fundo: ${AppImpactCalculator.formatDurationPt(app.backgroundServiceMillis)} · Ativações: ${app.estimatedWakeupsCount}",
                style = NexusMonoTextStyles.metricSmall,
                color = colors.textDim
            )
        }

        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Text("2. Dados Móveis e Wi-Fi", style = MaterialTheme.typography.titleMedium, color = colors.text)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Dados móveis: ${NexusFormatters.formatBytes(app.mobileForegroundBytes)} (1.º plano) · ${NexusFormatters.formatBytes(app.mobileBackgroundBytes)} (fundo)",
                style = NexusMonoTextStyles.metricSmall,
                color = colors.textDim
            )
            Text(
                text = "Wi-Fi: ${NexusFormatters.formatBytes(app.wifiForegroundBytes)} (1.º plano) · ${NexusFormatters.formatBytes(app.wifiBackgroundBytes)} (fundo)",
                style = NexusMonoTextStyles.metricSmall,
                color = colors.textDim
            )
        }

        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Text("3. Armazenamento e Tendência", style = MaterialTheme.typography.titleMedium, color = colors.text)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Total: ${NexusFormatters.formatBytes(app.totalStorageBytes)} (${app.storageTrendTextPt})",
                style = NexusMonoTextStyles.metricMedium.copy(fontSize = 15.sp),
                color = colors.text
            )
            Text(
                text = "App: ${NexusFormatters.formatBytes(app.appCodeBytes)} · Dados: ${NexusFormatters.formatBytes(app.dataBytes)} · Cache: ${NexusFormatters.formatBytes(app.cacheBytes)}",
                style = NexusMonoTextStyles.metricSmall,
                color = colors.textDim
            )
        }

        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(14.dp)
        ) {
            Text("4. Utilização e Inatividade", style = MaterialTheme.typography.titleMedium, color = colors.text)
            Spacer(modifier = Modifier.height(4.dp))
            val unusedText = app.daysSinceLastUse?.let { "Última utilização há $it dia(s)" } ?: "Requer acesso a utilização"
            Text(
                text = unusedText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )
        }
    }
}
