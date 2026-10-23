package com.nexusguard.android.ui.clean

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.storage.StorageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.repository.CleanCategoryGroup
import com.nexusguard.android.data.repository.CleanCategoryType
import com.nexusguard.android.domain.AppCacheCandidate
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ScannedFileCandidate
import com.nexusguard.android.domain.SimilarPhotoGroup
import com.nexusguard.android.ui.CleanUiState
import com.nexusguard.android.ui.components.AppIconOrMonogram
import com.nexusguard.android.ui.components.FileThumbnailOrTypeBadge
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusCheckbox48dp
import com.nexusguard.android.ui.components.NexusChip
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.components.NexusTextButton
import com.nexusguard.android.ui.components.rememberScaledAnimationDuration
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CleanScreen(
    uiState: CleanUiState,
    onRefreshScan: () -> Unit,
    onCancelScan: () -> Unit = {},
    onAppCacheCleared: (Long, (Long) -> Unit) -> Unit = { _, _ -> },
    buildTrashIntent: (List<ScannedFileCandidate>) -> IntentSender?,
    onTrashConfirmed: (List<ScannedFileCandidate>, List<String>) -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val storage = uiState.storage
    val cleanReport = uiState.cleanReport
    val isScanning = uiState.isScanning

    // Fase 1 & 3: Só as categorias seguras vêm pré-selecionadas (cache, APK instalados, lixo, duplicados exatos, pastas vazias, restos)
    val selectedCategories = remember {
        mutableStateListOf<CleanCategoryType>().apply {
            addAll(CleanCategoryType.entries.filter { it.isSafePreselected })
        }
    }

    // Ficheiros explicitamente selecionados (para categorias não pré-selecionadas) ou desmarcados (para categorias seguras)
    val deselectedFileUris = remember { mutableStateListOf<String>() }
    val explicitlySelectedFileUris = remember { mutableStateListOf<String>() }

    // Categoria aberta para pré-visualização obrigatória
    var previewCategory by remember { mutableStateOf<CleanCategoryGroup?>(null) }

    // Permissão de Media (Android 13+ ou Android <= 12)
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshScan()
    }

    // Launcher para StorageManager.ACTION_CLEAR_APP_CACHE (API 30+)
    var cacheBytesBeforeDialog by remember { mutableLongStateOf(0L) }
    val clearCacheDialogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onAppCacheCleared(cacheBytesBeforeDialog) { freedBytes ->
            val msg = if (freedBytes > 0L) {
                "Cache das apps limpa: ${NexusFormatters.formatBytes(freedBytes)} libertados (medido via StorageStatsManager)."
            } else {
                "Nenhuma alteração detetada na cache das apps."
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // Launcher para MediaStore.createTrashRequest / createDeleteRequest
    var pendingTrashFiles by remember { mutableStateOf<List<ScannedFileCandidate>>(emptyList()) }
    var pendingEmptyFolders by remember { mutableStateOf<List<String>>(emptyList()) }

    val trashIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onTrashConfirmed(pendingTrashFiles, pendingEmptyFolders)
            val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                "Movido para o Lixo com sucesso (restaurável durante 30 dias)."
            } else {
                "Ficheiros removidos (abaixo do Android 11 não há Lixo reversível)."
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (cleanReport.categories.isEmpty() && !isScanning) {
            onRefreshScan()
        }
    }

    fun isFileChecked(file: ScannedFileCandidate, categoryType: CleanCategoryType): Boolean {
        return if (file.isPreselectedByDefault && categoryType in selectedCategories) {
            file.contentUri !in deselectedFileUris
        } else {
            file.contentUri in explicitlySelectedFileUris || (categoryType in selectedCategories && file.contentUri !in deselectedFileUris)
        }
    }

    fun toggleFileChecked(file: ScannedFileCandidate, categoryType: CleanCategoryType) {
        val currentlyChecked = isFileChecked(file, categoryType)
        if (currentlyChecked) {
            explicitlySelectedFileUris.remove(file.contentUri)
            if (file.contentUri !in deselectedFileUris) {
                deselectedFileUris.add(file.contentUri)
            }
        } else {
            deselectedFileUris.remove(file.contentUri)
            if (file.contentUri !in explicitlySelectedFileUris) {
                explicitlySelectedFileUris.add(file.contentUri)
            }
            if (categoryType !in selectedCategories) {
                selectedCategories.add(categoryType)
            }
        }
    }

    val selectedFilesToTrash = remember(
        cleanReport,
        selectedCategories.toList(),
        deselectedFileUris.toList(),
        explicitlySelectedFileUris.toList()
    ) {
        cleanReport.categories
            .filter { it.type in selectedCategories }
            .flatMap { group ->
                group.files.filter { file -> isFileChecked(file, group.type) }
            }
            .distinctBy { it.contentUri }
    }
    val selectedEmptyFolders = remember(cleanReport, selectedCategories.toList()) {
        cleanReport.categories
            .filter { it.type == CleanCategoryType.EMPTY_FOLDERS && it.type in selectedCategories }
            .flatMap { it.emptyFoldersPaths }
    }

    val totalSelectedBytes = selectedFilesToTrash.sumOf { it.sizeBytes }
    val totalSelectedFormatted = NexusFormatters.formatBytes(totalSelectedBytes)

    // ModalBottomSheet de pré-visualização obrigatória
    previewCategory?.let { group ->
        ModalBottomSheet(
            onDismissRequest = { previewCategory = null },
            containerColor = colors.bgRaised,
            contentColor = colors.text
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Pré-visualização · ${group.type.titlePt}",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.text
                )
                Text(
                    text = group.subtitlePt,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )

                when (group.type) {
                    CleanCategoryType.APP_CACHE -> {
                        AppCachePreviewSection(
                            group = group,
                            hasUsageStatsPermission = cleanReport.hasUsageStatsPermission,
                            hasManageStoragePermission = cleanReport.hasManageStoragePermission,
                            onClearAllAppCache = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    cacheBytesBeforeDialog = group.totalSizeBytes
                                    try {
                                        clearCacheDialogLauncher.launch(
                                            Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
                                        )
                                    } catch (_: Exception) {
                                        NexusSystemIntents.openSystemClearCacheAction(context)
                                    }
                                } else {
                                    NexusSystemIntents.openSystemClearCacheAction(context)
                                }
                            }
                        )
                    }
                    CleanCategoryType.EMPTY_FOLDERS -> {
                        if (group.emptyFoldersPaths.isEmpty()) {
                            Text(
                                text = "Nenhuma pasta vazia encontrada.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(260.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(
                                    items = group.emptyFoldersPaths,
                                    key = { it }
                                ) { folderPath ->
                                    NexusCard(
                                        backgroundColor = colors.surface,
                                        borderColor = colors.border,
                                        contentPadding = PaddingValues(10.dp)
                                    ) {
                                        Text(
                                            text = folderPath,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.text
                                        )
                                    }
                                }
                            }
                        }
                    }
                    CleanCategoryType.SIMILAR_PHOTOS -> {
                        SimilarPhotosPreviewSection(
                            groups = group.similarGroups,
                            isFileChecked = { file -> isFileChecked(file, group.type) },
                            onToggleFile = { file -> toggleFileChecked(file, group.type) }
                        )
                    }
                    CleanCategoryType.MESSAGING_MEDIA -> {
                        MessagingMediaPreviewSection(
                            files = group.files,
                            isFileChecked = { file -> isFileChecked(file, group.type) },
                            onToggleFile = { file -> toggleFileChecked(file, group.type) }
                        )
                    }
                    else -> {
                        if (group.files.isEmpty()) {
                            Text(
                                text = if (!cleanReport.hasMediaPermission) {
                                    "Permissão de leitura de ficheiros não concedida."
                                } else {
                                    "Nenhum ficheiro encontrado nesta categoria."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(320.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = group.files,
                                    key = { it.contentUri }
                                ) { file ->
                                    val isChecked = isFileChecked(file, group.type)
                                    FilePreviewRow(
                                        file = file,
                                        isChecked = isChecked,
                                        onToggle = { toggleFileChecked(file, group.type) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                NexusPrimaryGradientButton(
                    text = "Concluir pré-visualização",
                    onClick = { previewCategory = null },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cartão de Armazenamento Real (StatFs + StorageStatsManager)
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
                        text = if (uiState.isLoading) {
                            "A calcular armazenamento..."
                        } else {
                            "${NexusFormatters.formatBytes(storage.usedBytes)} usados de ${NexusFormatters.formatBytes(storage.totalBytes)}"
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.text,
                        modifier = Modifier.weight(1f)
                    )
                    if (isScanning) {
                        NexusTextButton(
                            text = "Cancelar",
                            color = colors.danger,
                            onClick = onCancelScan
                        )
                    } else {
                        IconButton(
                            onClick = onRefreshScan,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = NexusIcons.Refresh,
                                contentDescription = "Atualizar análise",
                                tint = colors.accent2
                            )
                        }
                    }
                }

                if (isScanning) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.scanStepText.ifBlank { "A analisar categorias em paralelo..." },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent2
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { uiState.scanProgress.progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.accent2,
                        trackColor = colors.surfaceAlt
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                ProportionalStorageBar(
                    totalBytes = storage.totalBytes,
                    appsBytes = storage.appsAndSystemBytes,
                    photosBytes = storage.photosBytes,
                    videosBytes = storage.videosBytes,
                    cleanableBytes = cleanReport.totalCleanableBytes
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StorageLegendDot(
                        color = colors.accent,
                        label = if (uiState.isLoading) "Apps/Sist. —" else "Apps/Sist. ${NexusFormatters.formatBytes(storage.appsAndSystemBytes)}"
                    )
                    StorageLegendDot(
                        color = colors.accent2,
                        label = if (uiState.isLoading) "Fotos —" else "Fotos ${NexusFormatters.formatBytes(storage.photosBytes)}"
                    )
                    StorageLegendDot(
                        color = colors.ok,
                        label = if (uiState.isLoading) "Vídeos —" else "Vídeos ${NexusFormatters.formatBytes(storage.videosBytes)}"
                    )
                    StorageLegendDot(
                        color = colors.warn,
                        label = "Limpar ${NexusFormatters.formatBytes(cleanReport.totalCleanableBytes)}"
                    )
                    StorageLegendDot(
                        color = colors.textMuted,
                        label = if (uiState.isLoading) "Livre —" else "Livre ${NexusFormatters.formatBytes(storage.freeBytes)}"
                    )
                }
            }

            // Se ainda não tiver permissão de leitura de media, mostra explicação clara e botão
            if (!cleanReport.hasMediaPermission) {
                NexusCard(
                    backgroundColor = colors.surfaceAlt,
                    borderColor = colors.accent,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Text(
                        text = "Permissão necessária para analisar ficheiros",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Para encontrar duplicados reais (SHA-256), fotos semelhantes (dHash), capturas antigas e ficheiros grandes com pré-visualização, autoriza o acesso aos ficheiros multimédia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    NexusPrimaryGradientButton(
                        text = "Permitir leitura de ficheiros",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                mediaPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_MEDIA_IMAGES,
                                        Manifest.permission.READ_MEDIA_VIDEO,
                                        Manifest.permission.READ_MEDIA_AUDIO
                                    )
                                )
                            } else {
                                mediaPermissionLauncher.launch(
                                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val displayCategories = if (cleanReport.categories.isNotEmpty()) {
                cleanReport.categories
            } else {
                CleanCategoryType.entries
                    .filter { !it.requiresFullFlavorManageStorage || BuildConfig.IS_FULL_FLAVOR }
                    .map {
                        CleanCategoryGroup(
                            type = it,
                            subtitlePt = if (isScanning) "A analisar..." else it.emptySubtitlePt,
                            files = persistentListOf(),
                            totalSizeBytes = 0L
                        )
                    }
            }

            displayCategories.forEach { group ->
                val isSelected = group.type in selectedCategories
                CleanCategoryRealRow(
                    group = group,
                    isSelected = isSelected,
                    isScanning = isScanning && cleanReport.categories.isEmpty(),
                    onToggleSelect = {
                        if (isSelected) {
                            selectedCategories.remove(group.type)
                        } else {
                            selectedCategories.add(group.type)
                            if (!group.type.isSafePreselected) {
                                // Abre a pré-visualização para categorias que exigem revisão manual (semelhantes, desfocadas, grandes, mensagens)
                                previewCategory = group
                            }
                        }
                    },
                    onOpenPreview = {
                        previewCategory = group
                    }
                )
            }

            // Opção avançada MANAGE_EXTERNAL_STORAGE (apenas no flavor Full se não concedida)
            if (BuildConfig.IS_FULL_FLAVOR &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !cleanReport.hasManageStoragePermission
            ) {
                NexusCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo avançado: Restos de apps, subpastas APK e Lixo Nexus",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = colors.text
                            )
                            Text(
                                text = "Opcional: concede MANAGE_EXTERNAL_STORAGE para restos em Android/media e limpeza global de cache",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textDim
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        NexusChip(
                            text = "Autorizar",
                            color = colors.accent2,
                            onClick = { NexusSystemIntents.openManageAllFilesSettings(context) }
                        )
                    }
                }
            } else if (!BuildConfig.IS_FULL_FLAVOR) {
                NexusCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = "Restos de apps desinstaladas (Android/media)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = colors.textDim
                    )
                    Text(
                        text = "Disponível na versão completa (requer MANAGE_EXTERNAL_STORAGE).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }
        }

        // Barra fixa inferior com sombra e o total animado (AnimatedContent)
        val animDuration = rememberScaledAnimationDuration(180)
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
                    text = "Mover para o Lixo · $totalSelectedFormatted",
                    enabled = selectedFilesToTrash.isNotEmpty() || selectedEmptyFolders.isNotEmpty(),
                    onClick = {
                        val intentSender = buildTrashIntent(selectedFilesToTrash)
                        if (intentSender != null) {
                            pendingTrashFiles = selectedFilesToTrash
                            pendingEmptyFolders = selectedEmptyFolders
                            trashIntentLauncher.launch(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        } else if (selectedFilesToTrash.isNotEmpty() || selectedEmptyFolders.isNotEmpty()) {
                            onTrashConfirmed(selectedFilesToTrash, selectedEmptyFolders)
                            Toast.makeText(
                                context,
                                "Limpeza concluída (ficheiros movidos para o Lixo Nexus · 30 dias).",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            val firstGroup = cleanReport.categories.firstOrNull {
                                it.type in selectedCategories && it.files.isNotEmpty()
                            }
                            if (firstGroup != null) {
                                previewCategory = firstGroup
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentSlot = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    "Mover para o Lixo · "
                                } else {
                                    "Limpar · "
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onAccent
                            )
                            AnimatedContent(
                                targetState = totalSelectedFormatted,
                                transitionSpec = {
                                    fadeIn(tween(animDuration)) togetherWith fadeOut(tween(animDuration))
                                },
                                label = "cleanTotalAnimated"
                            ) { formattedTotal ->
                                Text(
                                    text = formattedTotal,
                                    style = NexusMonoTextStyles.metricMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onAccent
                                )
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        "Toca numa categoria para pré-visualizar · Vai para o Lixo (restaurável 30 dias)"
                    } else {
                        "Atenção: abaixo do Android 11 (API 30) a remoção no MediaStore não é reversível"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) colors.textMuted else colors.warn,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun AppCachePreviewSection(
    group: CleanCategoryGroup,
    hasUsageStatsPermission: Boolean,
    hasManageStoragePermission: Boolean,
    onClearAllAppCache: () -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current

    if (!hasUsageStatsPermission) {
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.border,
            contentPadding = PaddingValues(12.dp)
        ) {
            Text(
                text = "Requer acesso a utilização (PACKAGE_USAGE_STATS)",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = colors.text
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Para ler o cacheBytes real de cada app via StorageStatsManager, concede o Acesso a Dados de Utilização.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )
            Spacer(modifier = Modifier.height(8.dp))
            NexusChip(
                text = "Conceder acesso a utilização",
                color = colors.accent2,
                onClick = { NexusSystemIntents.openUsageAccessSettings(context) }
            )
        }
        return
    }

    if (BuildConfig.IS_FULL_FLAVOR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasManageStoragePermission) {
        NexusPrimaryGradientButton(
            text = "Limpar cache de todas as apps (${NexusFormatters.formatBytes(group.totalSizeBytes)})",
            onClick = onClearAllAppCache,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Abre o diálogo oficial do Android (ACTION_CLEAR_APP_CACHE) e mede a diferença real antes e depois.",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted
        )
    } else {
        Text(
            text = "Sem root ou permissão avançada, o Android exige limpar a cache nas definições de cada app:",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDim
        )
    }

    if (group.appCaches.isEmpty()) {
        Text(
            text = "Nenhuma app com cache significativa encontrada.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(vertical = 12.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.height(280.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = group.appCaches,
                key = { it.packageName }
            ) { item: AppCacheCandidate ->
                NexusCard(
                    backgroundColor = colors.surface,
                    borderColor = colors.border,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
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
                                packageName = item.packageName,
                                monogram = item.monogram,
                                iconSizeDp = 34
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = item.appName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Cache: ${NexusFormatters.formatBytes(item.cacheBytes)}",
                                    style = NexusMonoTextStyles.metricSmall,
                                    color = colors.warn
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        NexusTextButton(
                            text = "Abrir definições",
                            color = colors.accent2,
                            onClick = {
                                NexusSystemIntents.openAppDetailsSettings(context, item.packageName)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarPhotosPreviewSection(
    groups: List<SimilarPhotoGroup>,
    isFileChecked: (ScannedFileCandidate) -> Boolean,
    onToggleFile: (ScannedFileCandidate) -> Unit
) {
    val colors = NexusTheme.colors
    if (groups.isEmpty()) {
        Text(
            text = "Nenhum grupo de fotos semelhantes detetado (dHash ≤ 6 bits em janela de 10 min).",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        return
    }

    LazyColumn(
        modifier = Modifier.height(340.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(
            items = groups,
            key = { it.groupId }
        ) { simGroup ->
            NexusCard(
                backgroundColor = colors.surface,
                borderColor = colors.border,
                contentPadding = PaddingValues(10.dp)
            ) {
                Text(
                    text = "Grupo (${simGroup.allPhotos.size} fotos) · Manter mais nítida: ${simGroup.keepPhoto.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.ok
                )
                Spacer(modifier = Modifier.height(6.dp))
                simGroup.duplicatePhotos.forEach { photo ->
                    val checked = isFileChecked(photo)
                    FilePreviewRow(
                        file = photo,
                        isChecked = checked,
                        onToggle = { onToggleFile(photo) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun MessagingMediaPreviewSection(
    files: List<ScannedFileCandidate>,
    isFileChecked: (ScannedFileCandidate) -> Boolean,
    onToggleFile: (ScannedFileCandidate) -> Unit
) {
    val colors = NexusTheme.colors
    if (files.isEmpty()) {
        Text(
            text = "Nenhum ficheiro de mensagens (WhatsApp, Telegram, Signal) encontrado com estes critérios.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        return
    }

    LazyColumn(
        modifier = Modifier.height(320.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = files,
            key = { it.contentUri }
        ) { file ->
            val checked = isFileChecked(file)
            FilePreviewRow(
                file = file,
                isChecked = checked,
                badgePrefix = listOfNotNull(file.messagingAppName, file.messagingMediaType).joinToString(" · "),
                onToggle = { onToggleFile(file) }
            )
        }
    }
}

@Composable
private fun ProportionalStorageBar(
    totalBytes: Long,
    appsBytes: Long,
    photosBytes: Long,
    videosBytes: Long,
    cleanableBytes: Long
) {
    val colors = NexusTheme.colors
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(colors.surfaceAlt)
    ) {
        if (totalBytes <= 0L) return@Canvas
        val totalF = totalBytes.toFloat()
        val minVisualPx = 2.dp.toPx()

        val rawSegments = listOf(
            appsBytes to colors.accent,
            photosBytes to colors.accent2,
            videosBytes to colors.ok,
            cleanableBytes to colors.warn
        )

        var currentX = 0f
        rawSegments.forEach { (bytes, segColor) ->
            if (bytes > 0L && currentX < size.width) {
                val rawWidth = (bytes.toFloat() / totalF) * size.width
                val segWidth = rawWidth.coerceAtLeast(minVisualPx).coerceAtMost(size.width - currentX)
                drawRect(
                    color = segColor,
                    topLeft = Offset(currentX, 0f),
                    size = Size(segWidth, size.height)
                )
                currentX += segWidth
            }
        }
    }
}

@Composable
private fun CleanCategoryRealRow(
    group: CleanCategoryGroup,
    isSelected: Boolean,
    isScanning: Boolean,
    onToggleSelect: () -> Unit,
    onOpenPreview: () -> Unit
) {
    val colors = NexusTheme.colors
    val sizeText = when {
        isScanning -> "—"
        group.type == CleanCategoryType.EMPTY_FOLDERS -> "${group.emptyFoldersPaths.size}"
        else -> NexusFormatters.formatBytes(group.totalSizeBytes)
    }

    NexusCard(
        backgroundColor = colors.surface,
        borderColor = if (isSelected) colors.accent.copy(alpha = 0.65f) else colors.border,
        contentPadding = PaddingValues(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        onClick = onOpenPreview
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (group.type == CleanCategoryType.APP_CACHE) {
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                NexusCheckbox48dp(
                    checked = isSelected,
                    onToggle = onToggleSelect
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.type.titlePt,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                Text(
                    text = group.subtitlePt,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sizeText,
                style = NexusMonoTextStyles.metricSmall,
                color = colors.text
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = NexusIcons.ChevronRight,
                contentDescription = "Pré-visualizar ${group.type.titlePt}",
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun FilePreviewRow(
    file: ScannedFileCandidate,
    isChecked: Boolean,
    badgePrefix: String? = null,
    onToggle: () -> Unit
) {
    val colors = NexusTheme.colors
    NexusCard(
        backgroundColor = colors.surface,
        borderColor = if (isChecked) colors.accent2.copy(alpha = 0.6f) else colors.border,
        contentPadding = PaddingValues(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileThumbnailOrTypeBadge(
                contentUri = file.contentUri,
                mimeType = file.mimeType,
                displayName = file.displayName
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val pathAndSize = buildString {
                    if (!badgePrefix.isNullOrBlank()) {
                        append(badgePrefix)
                        append(" · ")
                    }
                    append(file.relativePath)
                    append(" · ")
                    append(NexusFormatters.formatBytes(file.sizeBytes))
                }
                Text(
                    text = pathAndSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            NexusCheckbox48dp(
                checked = isChecked,
                onToggle = onToggle
            )
        }
    }
}

@Composable
private fun StorageLegendDot(
    color: Color,
    label: String
) {
    val colors = NexusTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textDim
        )
    }
}
