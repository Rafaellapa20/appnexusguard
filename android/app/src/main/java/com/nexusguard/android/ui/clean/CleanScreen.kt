package com.nexusguard.android.ui.clean

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.repository.CleanCategoryGroup
import com.nexusguard.android.data.repository.CleanCategoryType
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ScannedFileCandidate
import com.nexusguard.android.ui.CleanUiState
import com.nexusguard.android.ui.components.FileThumbnailOrTypeBadge
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanScreen(
    uiState: CleanUiState,
    onRefreshScan: () -> Unit,
    buildTrashIntent: (List<ScannedFileCandidate>) -> IntentSender?,
    onTrashConfirmed: (List<ScannedFileCandidate>, List<String>) -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current
    val storage = uiState.storage
    val cleanReport = uiState.cleanReport
    val isScanning = uiState.isScanning

    // Categorias selecionadas para limpeza
    val selectedCategories = remember {
        mutableStateListOf(
            CleanCategoryType.INSTALLED_APKS,
            CleanCategoryType.OLD_DOWNLOADS,
            CleanCategoryType.DUPLICATES,
            CleanCategoryType.OLD_SCREENSHOTS,
            CleanCategoryType.EMPTY_FOLDERS
        )
    }

    // Ficheiros individualmente desmarcados na pré-visualização
    val deselectedFileUris = remember { mutableStateListOf<String>() }

    // Categoria aberta para pré-visualização obrigatória
    var previewCategory by remember { mutableStateOf<CleanCategoryGroup?>(null) }

    // Permissão de Media (Android 13+ ou Android <= 12)
    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshScan()
    }

    // Launcher para MediaStore.createTrashRequest
    var pendingTrashFiles by remember { mutableStateOf<List<ScannedFileCandidate>>(emptyList()) }
    var pendingEmptyFolders by remember { mutableStateOf<List<String>>(emptyList()) }

    val trashIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onTrashConfirmed(pendingTrashFiles, pendingEmptyFolders)
            Toast.makeText(
                context,
                "Movido para o Lixo do sistema com sucesso (restaurável durante 30 dias).",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (cleanReport.categories.isEmpty() && !isScanning) {
            onRefreshScan()
        }
    }

    val selectedFilesToTrash = remember(cleanReport, selectedCategories.toList(), deselectedFileUris.toList()) {
        cleanReport.categories
            .filter { it.type in selectedCategories }
            .flatMap { it.files }
            .distinctBy { it.contentUri }
            .filter { it.contentUri !in deselectedFileUris }
    }
    val selectedEmptyFolders = remember(cleanReport, selectedCategories.toList()) {
        cleanReport.categories
            .filter { it.type == CleanCategoryType.EMPTY_FOLDERS && it.type in selectedCategories }
            .flatMap { it.emptyFoldersPaths }
    }

    val totalSelectedBytes = selectedFilesToTrash.sumOf { it.sizeBytes }
    val totalSelectedFormatted = NexusFormatters.formatBytes(totalSelectedBytes)

    // ModalBottomSheet de pré-visualização obrigatória com LazyColumn + key + miniaturas LruCache
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

                if (group.type == CleanCategoryType.EMPTY_FOLDERS) {
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
                                        style = NexusMonoTextStyles.metricSmall,
                                        color = colors.text
                                    )
                                }
                            }
                        }
                    }
                } else if (group.files.isEmpty()) {
                    Text(
                        text = "Nenhum ficheiro encontrado nesta categoria.",
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
                            val isChecked = file.contentUri !in deselectedFileUris
                            FilePreviewRow(
                                file = file,
                                isChecked = isChecked,
                                onToggle = {
                                    if (isChecked) {
                                        deselectedFileUris.add(file.contentUri)
                                    } else {
                                        deselectedFileUris.remove(file.contentUri)
                                    }
                                }
                            )
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
                        color = colors.text
                    )
                    IconButton(
                        onClick = onRefreshScan,
                        modifier = Modifier.size(32.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.accent2
                            )
                        } else {
                            Icon(
                                imageVector = NexusIcons.Refresh,
                                contentDescription = "Atualizar análise",
                                tint = colors.accent2
                            )
                        }
                    }
                }

                if (isScanning && uiState.scanStepText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.scanStepText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent2
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val totalSafe = storage.totalBytes.coerceAtLeast(1L).toFloat()
                val appsW = (storage.appsAndSystemBytes / totalSafe).coerceIn(0.05f, 0.85f)
                val photosW = (storage.photosBytes / totalSafe).coerceIn(0.02f, 0.50f)
                val videosW = (storage.videosBytes / totalSafe).coerceIn(0.02f, 0.50f)
                val freeW = (storage.freeBytes / totalSafe).coerceIn(0.05f, 0.85f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.surfaceAlt)
                ) {
                    Box(modifier = Modifier.weight(appsW).fillMaxHeight().background(colors.accent))
                    Box(modifier = Modifier.weight(photosW).fillMaxHeight().background(colors.accent2))
                    Box(modifier = Modifier.weight(videosW).fillMaxHeight().background(colors.warn))
                    Box(modifier = Modifier.weight(freeW).fillMaxHeight().background(colors.surfaceAlt))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StorageLegendDot(
                        color = colors.accent,
                        label = "Apps/Sist. ${NexusFormatters.formatBytes(storage.appsAndSystemBytes)}"
                    )
                    StorageLegendDot(
                        color = colors.accent2,
                        label = "Fotos ${NexusFormatters.formatBytes(storage.photosBytes)}"
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StorageLegendDot(
                        color = colors.warn,
                        label = "Vídeos ${NexusFormatters.formatBytes(storage.videosBytes)}"
                    )
                    StorageLegendDot(
                        color = colors.textMuted,
                        label = "Livre ${NexusFormatters.formatBytes(storage.freeBytes)}"
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
                        text = "Para encontrar duplicados reais (SHA-256), capturas antigas e ficheiros grandes com pré-visualização, autoriza o acesso aos ficheiros multimédia.",
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

            // Lista das 6 categorias reais (tocar abre pré-visualização; caixa seleciona/desmarca)
            val displayCategories = if (cleanReport.categories.isNotEmpty()) {
                cleanReport.categories
            } else {
                CleanCategoryType.entries.map {
                    CleanCategoryGroup(
                        type = it,
                        subtitlePt = it.emptySubtitlePt,
                        files = kotlinx.collections.immutable.persistentListOf(),
                        totalSizeBytes = 0L
                    )
                }
            }

            displayCategories.forEach { group ->
                val isSelected = group.type in selectedCategories
                CleanCategoryRealRow(
                    group = group,
                    isSelected = isSelected,
                    onToggleSelect = {
                        if (isSelected) {
                            selectedCategories.remove(group.type)
                        } else {
                            selectedCategories.add(group.type)
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
                                text = "Modo avançado: APKs e pastas não-media",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = colors.text
                            )
                            Text(
                                text = "Opcional: concede MANAGE_EXTERNAL_STORAGE para ler APKs em Transferências",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textDim
                            )
                        }
                        TextButton(onClick = { NexusSystemIntents.openManageAllFilesSettings(context) }) {
                            Text("Ativar", color = colors.accent2)
                        }
                    }
                }
            }

            // Linha informativa para a cache do sistema
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button) {
                        NexusSystemIntents.openSystemClearCacheAction(context)
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Caches das outras apps (pede confirmação do sistema)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Abrir →",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent2
                )
            }
        }

        // Barra fixa inferior «Mover para o Lixo · X MB»
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
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
                    } else if (selectedEmptyFolders.isNotEmpty()) {
                        onTrashConfirmed(emptyList(), selectedEmptyFolders)
                        Toast.makeText(
                            context,
                            "${selectedEmptyFolders.size} pasta(s) vazia(s) removida(s).",
                            Toast.LENGTH_SHORT
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
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Toca numa categoria para pré-visualizar · Vai para o Lixo do sistema (30 dias)",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun CleanCategoryRealRow(
    group: CleanCategoryGroup,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpenPreview: () -> Unit
) {
    val colors = NexusTheme.colors
    val sizeText = if (group.type == CleanCategoryType.EMPTY_FOLDERS) {
        "${group.emptyFoldersPaths.size}"
    } else {
        NexusFormatters.formatBytes(group.totalSizeBytes)
    }

    NexusCard(
        backgroundColor = colors.surface,
        borderColor = if (isSelected) colors.accent.copy(alpha = 0.65f) else colors.border,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onOpenPreview
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) colors.accent2 else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) colors.accent2 else colors.borderStrong,
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable(role = Role.Checkbox, onClick = onToggleSelect),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = NexusIcons.Check,
                        contentDescription = null,
                        tint = Color(0xFF0C0F16),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.type.titlePt,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                Text(
                    text = "${group.subtitlePt} · tocar p/ pré-visualizar",
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
        }
    }
}

@Composable
private fun FilePreviewRow(
    file: ScannedFileCandidate,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    val colors = NexusTheme.colors
    NexusCard(
        backgroundColor = colors.surface,
        borderColor = if (isChecked) colors.accent2.copy(alpha = 0.6f) else colors.border,
        contentPadding = PaddingValues(10.dp),
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
                Text(
                    text = "${file.relativePath} · ${NexusFormatters.formatBytes(file.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isChecked) colors.accent2 else Color.Transparent)
                    .border(1.5.dp, if (isChecked) colors.accent2 else colors.borderStrong, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isChecked) {
                    Icon(
                        imageVector = NexusIcons.Check,
                        contentDescription = null,
                        tint = Color(0xFF0C0F16),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
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
        Box(
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
