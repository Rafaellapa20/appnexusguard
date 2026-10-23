package com.nexusguard.android.ui.clean

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.storage.StorageManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.repository.CategoryScanProgress
import com.nexusguard.android.data.repository.CategoryScanStatus
import com.nexusguard.android.data.repository.CleanCategoryGroup
import com.nexusguard.android.data.repository.CleanCategoryType
import com.nexusguard.android.data.repository.DeepCleanCompletionResult
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
import com.nexusguard.android.ui.components.NexusEmptyStateCard
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusShimmerBox
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.components.NexusTextButton
import com.nexusguard.android.ui.components.rememberScaledAnimationDuration
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.collections.immutable.persistentListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private fun iconForCategory(type: CleanCategoryType): ImageVector {
    return when (type) {
        CleanCategoryType.APP_CACHE -> NexusIcons.CategoryCache
        CleanCategoryType.INSTALLED_APKS, CleanCategoryType.UNINSTALLED_OLD_APKS -> NexusIcons.CategoryApk
        CleanCategoryType.FILESYSTEM_JUNK -> NexusIcons.CategoryJunk
        CleanCategoryType.DUPLICATES -> NexusIcons.CategoryDuplicates
        CleanCategoryType.EMPTY_FOLDERS -> NexusIcons.CategoryEmptyFolder
        CleanCategoryType.UNINSTALLED_LEFTOVERS -> NexusIcons.CategoryLeftovers
        CleanCategoryType.SIMILAR_PHOTOS -> NexusIcons.CategorySimilarPhotos
        CleanCategoryType.BLURRY_PHOTOS -> NexusIcons.CategoryBlurryPhotos
        CleanCategoryType.OLD_SCREENSHOTS -> NexusIcons.CategoryScreenshots
        CleanCategoryType.OLD_DOWNLOADS -> NexusIcons.CategoryDownloads
        CleanCategoryType.LARGE_FILES -> NexusIcons.CategoryLargeFiles
        CleanCategoryType.MESSAGING_MEDIA -> NexusIcons.CategoryMessaging
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun CleanScreen(
    uiState: CleanUiState,
    onRefreshScan: () -> Unit,
    onCancelScan: () -> Unit = {},
    onAppCacheCleared: (Long, (Long) -> Unit) -> Unit = { _, _ -> },
    buildTrashIntent: (List<ScannedFileCandidate>) -> IntentSender?,
    onTrashConfirmed: (List<ScannedFileCandidate>, List<String>) -> Unit,
    onDismissCompletion: () -> Unit = {},
    buildRestoreCompletionIntent: (DeepCleanCompletionResult) -> IntentSender? = { null },
    onRestoreConfirmed: (Long) -> Unit = {},
    additionalToolsContent: (@Composable () -> Unit)? = null
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val view = LocalView.current
    val storage = uiState.storage
    val cleanReport = uiState.cleanReport
    val isScanning = uiState.isScanning
    val completionResult = uiState.lastCompletionResult

    // Só as categorias seguras vêm pré-selecionadas
    val selectedCategories = remember {
        mutableStateListOf<CleanCategoryType>().apply {
            addAll(CleanCategoryType.entries.filter { it.isSafePreselected })
        }
    }

    val deselectedFileUris = remember { mutableStateListOf<String>() }
    val explicitlySelectedFileUris = remember { mutableStateListOf<String>() }

    // Categoria aberta para revisão / pré-visualização obrigatória
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
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            }
        }
    }

    // Launcher para restaurar a partir do ecrã de conclusão
    var pendingRestoreEntryId by remember { mutableLongStateOf(-1L) }
    val restoreCompletionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingRestoreEntryId > 0L) {
            onRestoreConfirmed(pendingRestoreEntryId)
            onDismissCompletion()
            Toast.makeText(context, "Ficheiros restaurados com sucesso.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (cleanReport.categories.isEmpty() && !isScanning && completionResult == null) {
            onRefreshScan()
        }
    }

    // 4.º Momento: Ecrã de conclusão após limpeza
    if (completionResult != null) {
        DeepCleanCompletionView(
            result = completionResult,
            totalStorageBytes = storage.totalBytes,
            onRestore = {
                if (completionResult.historyEntryId > 0L) {
                    val sender = buildRestoreCompletionIntent(completionResult)
                    if (sender != null) {
                        pendingRestoreEntryId = completionResult.historyEntryId
                        restoreCompletionLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    } else {
                        onRestoreConfirmed(completionResult.historyEntryId)
                        onDismissCompletion()
                        Toast.makeText(context, "Ficheiros restaurados do Lixo Nexus.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDone = onDismissCompletion
        )
        return
    }

    // 1.º Momento: Ecrã de análise guiada com anel de progresso e categorias uma a uma
    if (isScanning) {
        DeepCleanScanningView(
            scanStepText = uiState.scanStepText,
            progressFraction = uiState.scanProgress.progressFraction,
            categoryStates = uiState.scanProgress.categoryProgress.values.toList(),
            onCancelScan = onCancelScan
        )
        return
    }

    fun isFileChecked(file: ScannedFileCandidate, categoryType: CleanCategoryType): Boolean {
        return if (file.isPreselectedByDefault && categoryType in selectedCategories) {
            file.contentUri !in deselectedFileUris
        } else {
            file.contentUri in explicitlySelectedFileUris || (categoryType in selectedCategories && file.contentUri !in deselectedFileUris && file.isPreselectedByDefault)
        }
    }

    fun setFileCheckedState(file: ScannedFileCandidate, categoryType: CleanCategoryType, targetChecked: Boolean) {
        if (targetChecked) {
            deselectedFileUris.remove(file.contentUri)
            if (file.contentUri !in explicitlySelectedFileUris) {
                explicitlySelectedFileUris.add(file.contentUri)
            }
            if (categoryType !in selectedCategories) {
                selectedCategories.add(categoryType)
            }
        } else {
            explicitlySelectedFileUris.remove(file.contentUri)
            if (file.contentUri !in deselectedFileUris) {
                deselectedFileUris.add(file.contentUri)
            }
        }
    }

    fun toggleFileChecked(file: ScannedFileCandidate, categoryType: CleanCategoryType) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        val currentlyChecked = isFileChecked(file, categoryType)
        setFileCheckedState(file, categoryType, !currentlyChecked)
    }

    fun selectRangeOfFiles(files: List<ScannedFileCandidate>, categoryType: CleanCategoryType, startIdx: Int, endIdx: Int) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        val from = min(startIdx, endIdx).coerceAtLeast(0)
        val to = max(startIdx, endIdx).coerceAtMost(files.lastIndex)
        if (from > to) return
        val targetState = !isFileChecked(files[endIdx], categoryType)
        for (i in from..to) {
            setFileCheckedState(files[i], categoryType, targetState)
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

    // Fase 4: SharedTransitionLayout do cartão da categoria para a vista de revisão
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = previewCategory,
            transitionSpec = {
                fadeIn(spring(dampingRatio = 0.8f, stiffness = 400f)) togetherWith
                    fadeOut(spring(dampingRatio = 0.8f, stiffness = 400f))
            },
            label = "cleanSharedTransition"
        ) { activeReviewGroup ->
            if (activeReviewGroup != null) {
                // 3.º Momento: Revisão com SharedTransition a partir do cartão da categoria
                Surface(
                    color = colors.bg,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CategoryReviewSheetContent(
                        group = activeReviewGroup,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedContent,
                        hasMediaPermission = cleanReport.hasMediaPermission,
                        hasUsageStatsPermission = cleanReport.hasUsageStatsPermission,
                        hasManageStoragePermission = cleanReport.hasManageStoragePermission,
                        isFileChecked = { file -> isFileChecked(file, activeReviewGroup.type) },
                        onToggleFile = { file -> toggleFileChecked(file, activeReviewGroup.type) },
                        onSelectRange = { files, start, end -> selectRangeOfFiles(files, activeReviewGroup.type, start, end) },
                        onClearAllAppCache = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                cacheBytesBeforeDialog = activeReviewGroup.totalSizeBytes
                                try {
                                    clearCacheDialogLauncher.launch(Intent(StorageManager.ACTION_CLEAR_APP_CACHE))
                                } catch (_: Exception) {
                                    NexusSystemIntents.openSystemClearCacheAction(context)
                                }
                            } else {
                                NexusSystemIntents.openSystemClearCacheAction(context)
                            }
                        },
                        onClose = { previewCategory = null }
                    )
                }
            } else {
                // 2.º Momento: Ecrã de resultado («Podes libertar X», barra antes/depois projetada, categorias seguras vs revisão)
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cabeçalho Herói: «Podes libertar X» + Barra de Armazenamento antes/depois projetada
                        NexusCard(
                            backgroundColor = colors.surface,
                            borderColor = colors.borderStrong,
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Podes libertar",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textDim
                                    )
                                    if (uiState.isLoading) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        NexusShimmerBox(width = 140.dp, height = 34.dp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                    } else {
                                        val heroBytes = if (totalSelectedBytes > 0L) totalSelectedBytes else cleanReport.totalCleanableBytes
                                        AnimatedContent(
                                            targetState = NexusFormatters.formatBytes(heroBytes),
                                            transitionSpec = {
                                                fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                                            },
                                            label = "heroCleanableBytes"
                                        ) { heroFormatted ->
                                            Text(
                                                text = heroFormatted,
                                                style = NexusMonoTextStyles.metricHero.copy(fontSize = 34.sp),
                                                color = if (heroBytes > 0L) colors.accent2 else colors.text
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (totalSelectedBytes > 0L) {
                                            "${selectedFilesToTrash.size} ficheiro(s) marcados · Total detetado: ${NexusFormatters.formatBytes(cleanReport.totalCleanableBytes)}"
                                        } else {
                                            "${cleanReport.scannedFilesCount} ficheiros analisados no dispositivo"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted
                                    )
                                }
                                IconButton(
                                    onClick = onRefreshScan,
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = NexusIcons.Refresh,
                                        contentDescription = "Nova análise profunda",
                                        tint = colors.accent2
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Barra antes / depois projetada
                            val projectedUsedBytes = (storage.usedBytes - totalSelectedBytes).coerceAtLeast(0L)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (uiState.isLoading) "Antes: —" else "Antes: ${NexusFormatters.formatBytes(storage.usedBytes)} usados",
                                    style = NexusMonoTextStyles.metricSmall,
                                    color = colors.textDim
                                )
                                Text(
                                    text = if (uiState.isLoading) "Depois: —" else "Depois: ${NexusFormatters.formatBytes(projectedUsedBytes)} (${NexusFormatters.formatBytes(storage.freeBytes + totalSelectedBytes)} livres)",
                                    style = NexusMonoTextStyles.metricSmall,
                                    color = colors.ok
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            ProportionalStorageBar(
                                totalBytes = storage.totalBytes,
                                appsBytes = storage.appsAndSystemBytes,
                                photosBytes = storage.photosBytes,
                                videosBytes = storage.videosBytes,
                                cleanableBytes = totalSelectedBytes.coerceAtLeast(cleanReport.totalCleanableBytes)
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
                                    label = "Libertável ${NexusFormatters.formatBytes(cleanReport.totalCleanableBytes)}"
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
                                        subtitlePt = it.emptySubtitlePt,
                                        files = persistentListOf(),
                                        totalSizeBytes = 0L
                                    )
                                }
                        }

                        val safeCategories = displayCategories.filter { it.type.isSafePreselected }
                        val reviewCategories = displayCategories.filter { !it.type.isSafePreselected }

                        Text(
                            text = "Limpeza segura (pré-selecionada)",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.ok
                        )
                        safeCategories.forEach { group ->
                            val isSelected = group.type in selectedCategories
                            CleanCategoryRealRow(
                                group = group,
                                isSelected = isSelected,
                                isScanning = false,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
                                onToggleSelect = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    if (isSelected) {
                                        selectedCategories.remove(group.type)
                                    } else {
                                        selectedCategories.add(group.type)
                                    }
                                },
                                onOpenPreview = { previewCategory = group }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Requer revisão manual (desmarcada por omissão)",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.warn
                        )
                        reviewCategories.forEach { group ->
                            val isSelected = group.type in selectedCategories
                            CleanCategoryRealRow(
                                group = group,
                                isSelected = isSelected,
                                isScanning = false,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
                                onToggleSelect = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    if (isSelected) {
                                        selectedCategories.remove(group.type)
                                    } else {
                                        selectedCategories.add(group.type)
                                        previewCategory = group
                                    }
                                },
                                onOpenPreview = { previewCategory = group }
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
                        }

                        if (additionalToolsContent != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            additionalToolsContent()
                        }
                    }

                    // Barra fixa inferior «Limpar X» / «Mover para o Lixo · X»
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
                                text = "Limpar $totalSelectedFormatted",
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
                                                "Limpar (Mover para o Lixo) · "
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
                                    "Toca numa categoria para rever · Vai para o Lixo (restaurável durante 30 dias)"
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
        }
    }
}

@Composable
private fun DeepCleanScanningView(
    scanStepText: String,
    progressFraction: Float,
    categoryStates: List<CategoryScanProgress>,
    onCancelScan: () -> Unit
) {
    val colors = NexusTheme.colors
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = rememberScaledAnimationDuration(260)),
        label = "scanProgressRing"
    )
    val percentInt = (animatedProgress * 100f).toInt().coerceIn(0, 100)
    val totalFoundSoFar = categoryStates.sumOf { it.totalBytes }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Anel de progresso com percentagem real
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(148.dp)
        ) {
            val sweepBrush = Brush.sweepGradient(
                colors = listOf(colors.accent, colors.accent2, colors.accent)
            )
            Canvas(modifier = Modifier.size(134.dp)) {
                drawArc(
                    color = colors.surfaceAlt,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                )
                rotate(degrees = -90f) {
                    drawArc(
                        brush = sweepBrush,
                        startAngle = 0f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$percentInt%",
                    style = NexusMonoTextStyles.metricHero.copy(fontSize = 34.sp),
                    color = colors.text
                )
                AnimatedContent(
                    targetState = NexusFormatters.formatBytes(totalFoundSoFar),
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "scanningBytesSoFar"
                ) { formatted ->
                    Text(
                        text = formatted,
                        style = NexusMonoTextStyles.metricSmall,
                        color = colors.accent2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = scanStepText.ifBlank { "A analisar categorias em paralelo..." },
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.text,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Lista de categorias que se vão ativando uma a uma (✓, contagem e tamanho a subir com AnimatedContent)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = categoryStates,
                key = { it.type.name }
            ) { item ->
                val isDone = item.status == CategoryScanStatus.DONE
                NexusCard(
                    backgroundColor = colors.surface,
                    borderColor = if (isDone) colors.ok.copy(alpha = 0.5f) else colors.border,
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
                            if (isDone) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(colors.ok.copy(alpha = 0.18f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = NexusIcons.Check,
                                        contentDescription = "Concluído",
                                        tint = colors.ok,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = colors.accent2
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = item.type.titlePt,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = colors.text
                                )
                                Text(
                                    text = if (isDone) "${item.itemCount} item(ns)" else "A verificar...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted
                                )
                            }
                        }
                        AnimatedContent(
                            targetState = if (isDone) NexusFormatters.formatBytes(item.totalBytes) else "…",
                            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                            label = "catBytes_${item.type.name}"
                        ) { sizeStr ->
                            Text(
                                text = sizeStr,
                                style = NexusMonoTextStyles.metricSmall,
                                color = if (item.totalBytes > 0L) colors.accent2 else colors.textDim
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        NexusTextButton(
            text = "Cancelar análise",
            color = colors.danger,
            onClick = onCancelScan
        )
    }
}

@Composable
private fun DeepCleanCompletionView(
    result: DeepCleanCompletionResult,
    totalStorageBytes: Long,
    onRestore: () -> Unit,
    onDone: () -> Unit
) {
    val colors = NexusTheme.colors
    val view = LocalView.current

    LaunchedEffect(result.historyEntryId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    val animFraction by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = rememberScaledAnimationDuration(750)),
        label = "freedBytesCounter"
    )
    val animatedFreedBytes = (result.measuredFreedBytes * animFraction).toLong()

    val restoreUntilDateStr = remember(result.restoreUntilEpochMillis) {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-PT"))
        fmt.format(Date(result.restoreUntilEpochMillis))
    }
    val freeBeforeBytes = (result.totalStorageBytes - result.beforeUsedBytes).coerceAtLeast(0L)
    val freeAfterBytes = (result.totalStorageBytes - result.afterUsedBytes).coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(colors.ok.copy(alpha = 0.16f))
                .border(2.dp, colors.ok, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = NexusIcons.Check,
                contentDescription = "Limpeza concluída",
                tint = colors.ok,
                modifier = Modifier.size(42.dp)
            )
        }

        Text(
            text = "Libertado",
            style = MaterialTheme.typography.labelLarge,
            color = colors.ok
        )

        Text(
            text = NexusFormatters.formatBytes(animatedFreedBytes),
            style = NexusMonoTextStyles.metricHero.copy(fontSize = 42.sp),
            color = colors.text
        )

        Text(
            text = "${result.removedFilesCount} ficheiro(s) e ${result.removedEmptyFoldersCount} pasta(s) vazia(s) processados",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textDim,
            textAlign = TextAlign.Center
        )

        // Cartão Antes -> Depois medido de novo via StatFs
        NexusCard(
            backgroundColor = colors.surface,
            borderColor = colors.borderStrong,
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = "Armazenamento medido (StatFs antes → depois)",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Antes da limpeza", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                    Text(
                        text = "${NexusFormatters.formatBytes(result.beforeUsedBytes)} usados",
                        style = NexusMonoTextStyles.metricMedium,
                        color = colors.textDim
                    )
                    Text(
                        text = "${NexusFormatters.formatBytes(freeBeforeBytes)} livres",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                }
                Text("→", style = NexusMonoTextStyles.metricLarge, color = colors.accent2)
                Column(horizontalAlignment = Alignment.End) {
                    Text("Medido agora", style = MaterialTheme.typography.labelSmall, color = colors.ok)
                    Text(
                        text = "${NexusFormatters.formatBytes(result.afterUsedBytes)} usados",
                        style = NexusMonoTextStyles.metricMedium,
                        color = colors.ok
                    )
                    Text(
                        text = "${NexusFormatters.formatBytes(freeAfterBytes)} livres de ${NexusFormatters.formatBytes(totalStorageBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textDim
                    )
                }
            }

            if (result.canRestore) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Pode ser restaurado até $restoreUntilDateStr (quarentena de 30 dias no Lixo).",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.accent2
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (result.canRestore && result.removedFilesCount > 0) {
            NexusChip(
                text = "Restaurar ficheiros agora",
                color = colors.warn,
                onClick = onRestore
            )
        }

        NexusPrimaryGradientButton(
            text = "Concluir",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CategoryReviewSheetContent(
    group: CleanCategoryGroup,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    hasMediaPermission: Boolean,
    hasUsageStatsPermission: Boolean,
    hasManageStoragePermission: Boolean,
    isFileChecked: (ScannedFileCandidate) -> Boolean,
    onToggleFile: (ScannedFileCandidate) -> Unit,
    onSelectRange: (List<ScannedFileCandidate>, Int, Int) -> Unit,
    onClearAllAppCache: () -> Unit,
    onClose: () -> Unit
) {
    val colors = NexusTheme.colors
    val isPhotoOrVideoCategory = group.type == CleanCategoryType.BLURRY_PHOTOS ||
        group.type == CleanCategoryType.OLD_SCREENSHOTS ||
        group.files.count { it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") } > (group.files.size / 2)

    var useGridView by remember(group.type) { mutableStateOf(isPhotoOrVideoCategory) }
    var lastAnchorIndex by remember(group.type) { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        with(sharedTransitionScope) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "cat_header_${group.type.name}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = NexusIcons.ArrowBack,
                            contentDescription = "Voltar",
                            tint = colors.text
                        )
                    }
                    Icon(
                        imageVector = iconForCategory(group.type),
                        contentDescription = null,
                        tint = colors.accent2,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = group.type.titlePt,
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.text
                        )
                        Text(
                            text = group.subtitlePt,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDim
                        )
                    }
                }
                if (group.files.isNotEmpty() && group.type != CleanCategoryType.SIMILAR_PHOTOS) {
                    NexusChip(
                        text = if (useGridView) "Vista: Grelha" else "Vista: Lista",
                        color = colors.accent2,
                        onClick = { useGridView = !useGridView }
                    )
                }
            }
        }

        if (group.files.size > 1 && group.type != CleanCategoryType.SIMILAR_PHOTOS) {
            Text(
                text = if (lastAnchorIndex >= 0) {
                    "Toque longo ativo no item #${lastAnchorIndex + 1}: faz toque longo noutro item para marcar o intervalo."
                } else {
                    "Dica: toca para marcar/desmarcar ou faz toque longo em dois itens para seleção em intervalo."
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (lastAnchorIndex >= 0) colors.accent2 else colors.textMuted
            )
        }

        when (group.type) {
            CleanCategoryType.APP_CACHE -> {
                AppCachePreviewSection(
                    group = group,
                    hasUsageStatsPermission = hasUsageStatsPermission,
                    hasManageStoragePermission = hasManageStoragePermission,
                    onClearAllAppCache = onClearAllAppCache
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
                    isFileChecked = isFileChecked,
                    onToggleFile = onToggleFile
                )
            }
            else -> {
                if (group.files.isEmpty()) {
                    NexusEmptyStateCard(
                        title = if (!hasMediaPermission) "Permissão de ficheiros necessária" else "Tudo limpo nesta categoria",
                        subtitle = if (!hasMediaPermission) {
                            "Autoriza a leitura de ficheiros multimédia para analisar esta categoria."
                        } else {
                            "Nenhum ficheiro encontrado em ${group.type.titlePt}."
                        },
                        icon = iconForCategory(group.type)
                    )
                } else if (useGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = group.files,
                            key = { _, it -> it.contentUri }
                        ) { index, file ->
                            val isChecked = isFileChecked(file)
                            FileGridReviewItem(
                                file = file,
                                isChecked = isChecked,
                                onClick = {
                                    lastAnchorIndex = index
                                    onToggleFile(file)
                                },
                                onLongClick = {
                                    if (lastAnchorIndex >= 0 && lastAnchorIndex != index) {
                                        onSelectRange(group.files, lastAnchorIndex, index)
                                        lastAnchorIndex = -1
                                    } else {
                                        lastAnchorIndex = index
                                        onToggleFile(file)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = group.files,
                            key = { _, it -> it.contentUri }
                        ) { index, file ->
                            val isChecked = isFileChecked(file)
                            val badge = if (group.type == CleanCategoryType.MESSAGING_MEDIA) {
                                listOfNotNull(file.messagingAppName, file.messagingMediaType).joinToString(" · ")
                            } else null
                            FilePreviewRow(
                                file = file,
                                isChecked = isChecked,
                                badgePrefix = badge,
                                onToggle = {
                                    lastAnchorIndex = index
                                    onToggleFile(file)
                                },
                                onLongPress = {
                                    if (lastAnchorIndex >= 0 && lastAnchorIndex != index) {
                                        onSelectRange(group.files, lastAnchorIndex, index)
                                        lastAnchorIndex = -1
                                    } else {
                                        lastAnchorIndex = index
                                        onToggleFile(file)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        NexusPrimaryGradientButton(
            text = "Concluir revisão",
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridReviewItem(
    file: ScannedFileCandidate,
    isChecked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = NexusTheme.colors
    NexusCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        backgroundColor = colors.surface,
        borderColor = if (isChecked) colors.accent2 else colors.border,
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileThumbnailOrTypeBadge(
                contentUri = file.contentUri,
                mimeType = file.mimeType,
                displayName = file.displayName,
                sizeDp = 54
            )
            NexusCheckbox48dp(
                checked = isChecked,
                onToggle = onClick
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = file.displayName,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = NexusFormatters.formatBytes(file.sizeBytes),
            style = NexusMonoTextStyles.metricSmall,
            color = colors.textMuted
        )
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
        modifier = Modifier.height(350.dp),
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
                    text = "Comparação lado a lado (${simGroup.allPhotos.size} fotos)",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Comparação lado a lado: mais nítida à esquerda (Manter), restantes à direita para marcar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Coluna esquerda: Mais nítida (Manter)
                    NexusCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = colors.surfaceAlt,
                        borderColor = colors.ok.copy(alpha = 0.65f),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FileThumbnailOrTypeBadge(
                                contentUri = simGroup.keepPhoto.contentUri,
                                mimeType = simGroup.keepPhoto.mimeType,
                                displayName = simGroup.keepPhoto.displayName,
                                sizeDp = 44
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Manter (Mais nítida)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = colors.ok
                                )
                                Text(
                                    text = simGroup.keepPhoto.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = NexusFormatters.formatBytes(simGroup.keepPhoto.sizeBytes),
                                    style = NexusMonoTextStyles.metricSmall,
                                    color = colors.textMuted
                                )
                            }
                        }
                    }

                    // Coluna direita: Semelhante(s) para revisão
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        simGroup.duplicatePhotos.forEach { photo ->
                            val checked = isFileChecked(photo)
                            NexusCard(
                                backgroundColor = colors.surfaceAlt,
                                borderColor = if (checked) colors.accent2 else colors.border,
                                contentPadding = PaddingValues(8.dp),
                                onClick = { onToggleFile(photo) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    FileThumbnailOrTypeBadge(
                                        contentUri = photo.contentUri,
                                        mimeType = photo.mimeType,
                                        displayName = photo.displayName,
                                        sizeDp = 38
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = photo.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = NexusFormatters.formatBytes(photo.sizeBytes),
                                            style = NexusMonoTextStyles.metricSmall,
                                            color = colors.textMuted
                                        )
                                    }
                                    NexusCheckbox48dp(
                                        checked = checked,
                                        onToggle = { onToggleFile(photo) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CleanCategoryRealRow(
    group: CleanCategoryGroup,
    isSelected: Boolean,
    isScanning: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope,
    onToggleSelect: () -> Unit,
    onOpenPreview: () -> Unit
) {
    val colors = NexusTheme.colors
    val sizeText = when {
        isScanning -> "—"
        group.type == CleanCategoryType.EMPTY_FOLDERS -> "${group.emptyFoldersPaths.size}"
        else -> NexusFormatters.formatBytes(group.totalSizeBytes)
    }

    with(sharedTransitionScope) {
        NexusCard(
            modifier = Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(key = "cat_header_${group.type.name}"),
                animatedVisibilityScope = animatedVisibilityScope
            ),
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
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    NexusCheckbox48dp(
                        checked = isSelected,
                        onToggle = onToggleSelect
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.surfaceAlt),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconForCategory(group.type),
                        contentDescription = null,
                        tint = if (isSelected) colors.accent2 else colors.textDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
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
                    contentDescription = "Rever ${group.type.titlePt}",
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilePreviewRow(
    file: ScannedFileCandidate,
    isChecked: Boolean,
    badgePrefix: String? = null,
    onToggle: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val colors = NexusTheme.colors
    NexusCard(
        modifier = if (onLongPress != null) {
            Modifier.combinedClickable(
                onClick = onToggle,
                onLongClick = onLongPress
            )
        } else {
            Modifier
        },
        backgroundColor = colors.surface,
        borderColor = if (isChecked) colors.accent2.copy(alpha = 0.6f) else colors.border,
        contentPadding = PaddingValues(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        onClick = if (onLongPress == null) onToggle else null
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
