package com.nexusguard.android.ui.clean

import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusguard.android.domain.BrowserPrivacyShortcut
import com.nexusguard.android.domain.DownloadFileItem
import com.nexusguard.android.domain.DownloadsSortOrder
import com.nexusguard.android.domain.LargeVideoItem
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.PhotoOptimizeQuality
import com.nexusguard.android.domain.PhotoOptimizerCalculator
import com.nexusguard.android.domain.PhotoOptimizerCandidate
import com.nexusguard.android.domain.ScannedFileCandidate
import com.nexusguard.android.flavor.CallLogCleanerDelegate
import com.nexusguard.android.ui.components.FileThumbnailOrTypeBadge
import com.nexusguard.android.ui.components.NexusCard
import com.nexusguard.android.ui.components.NexusCheckbox48dp
import com.nexusguard.android.ui.components.NexusChip
import com.nexusguard.android.ui.components.NexusPrimaryGradientButton
import com.nexusguard.android.ui.components.NexusSystemIntents
import com.nexusguard.android.ui.components.NexusTextButton
import com.nexusguard.android.ui.theme.NexusBadgeShape
import com.nexusguard.android.ui.theme.NexusIcons
import com.nexusguard.android.ui.theme.NexusMonoTextStyles
import com.nexusguard.android.ui.theme.NexusTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class AdditionalCleanToolsState(
    val hasClipboardContent: Boolean = false,
    val downloads: List<DownloadFileItem> = emptyList(),
    val largeVideos: List<LargeVideoItem> = emptyList(),
    val photoCandidates: List<PhotoOptimizerCandidate> = emptyList(),
    val selectedQuality: PhotoOptimizeQuality = PhotoOptimizeQuality.QUALITY_85,
    val limitMaxResolution4000px: Boolean = true,
    val isOptimizingPhotos: Boolean = false,
    val browserShortcuts: List<BrowserPrivacyShortcut> = emptyList(),
    val isCallLogAvailable: Boolean = false,
    val hasCallLogPermissions: Boolean = false,
    val oldCallLogsCount: Int = 0,
    val callLogMonthsThreshold: Int = 6
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdditionalCleanSection(
    state: AdditionalCleanToolsState,
    onClearClipboard: () -> Unit,
    onRefreshTools: () -> Unit,
    onPhotoOptionsChanged: (PhotoOptimizeQuality, Boolean) -> Unit,
    onOptimizePhotos: (List<PhotoOptimizerCandidate>, (Int, Long) -> Unit) -> Unit,
    onCallLogMonthsChanged: (Int) -> Unit,
    onDeleteOldCallLogs: (Int, (Int) -> Unit) -> Unit,
    onCallLogPermissionsResult: () -> Unit,
    buildTrashIntent: (List<ScannedFileCandidate>) -> IntentSender?,
    onTrashConfirmed: (List<ScannedFileCandidate>, List<String>) -> Unit
) {
    val colors = NexusTheme.colors
    val context = LocalContext.current

    var expandedTool by remember { mutableStateOf<String?>(null) }
    var downloadsSort by remember { mutableStateOf(DownloadsSortOrder.BY_SIZE_DESC) }
    val selectedDownloadUris = remember { mutableStateListOf<String>() }
    val selectedPhotoUris = remember { mutableStateListOf<String>() }
    var previewPhoto by remember { mutableStateOf<PhotoOptimizerCandidate?>(null) }
    var showCallLogConfirmDialog by remember { mutableStateOf(false) }
    var pendingTrashItems by remember { mutableStateOf<List<ScannedFileCandidate>>(emptyList()) }

    val trashLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && pendingTrashItems.isNotEmpty()) {
            onTrashConfirmed(pendingTrashItems, emptyList())
            selectedDownloadUris.clear()
            pendingTrashItems = emptyList()
            onRefreshTools()
        }
    }

    val callLogPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onCallLogPermissionsResult()
    }

    previewPhoto?.let { candidate ->
        PhotoBeforeAfterPreviewDialog(
            candidate = candidate,
            quality = state.selectedQuality,
            limitMax4000px = state.limitMaxResolution4000px,
            onDismiss = { previewPhoto = null }
        )
    }

    if (showCallLogConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCallLogConfirmDialog = false },
            containerColor = colors.surface,
            titleContentColor = colors.text,
            textContentColor = colors.textDim,
            title = { Text("Confirmar limpeza do registo de chamadas") },
            text = {
                Text(
                    "Vão ser removidas permanentemente ${state.oldCallLogsCount} chamadas com mais de ${state.callLogMonthsThreshold} meses. Esta ação não pode ser anulada."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCallLogConfirmDialog = false
                        onDeleteOldCallLogs(state.callLogMonthsThreshold) { deleted ->
                            Toast.makeText(
                                context,
                                "$deleted registo(s) de chamadas apagado(s).",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                ) {
                    Text("Apagar chamadas", color = colors.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallLogConfirmDialog = false }) {
                    Text("Cancelar", color = colors.textDim)
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Limpezas adicionais e privacidade",
            style = MaterialTheme.typography.titleMedium,
            color = colors.text
        )

        // 1. Área de transferência
        NexusCard {
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
                        Text(
                            text = "Área de transferência",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text
                        )
                        Box(
                            modifier = Modifier
                                .clip(NexusBadgeShape)
                                .background(if (state.hasClipboardContent) colors.warn.copy(alpha = 0.16f) else colors.ok.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (state.hasClipboardContent) "Com conteúdo" else "Vazia",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.hasClipboardContent) colors.warn else colors.ok
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Verifica apenas hasPrimaryClip em primeiro plano. O Nexus Guard nunca lê nem guarda o texto copiado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDim
                    )
                }
                if (state.hasClipboardContent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    NexusTextButton(
                        text = "Limpar área de transferência",
                        color = colors.accent2,
                        onClick = {
                            onClearClipboard()
                            Toast.makeText(context, "Área de transferência limpa.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // 2. Otimizador de fotos (> 3 MB)
        val totalPhotoSavings = state.photoCandidates.sumOf { it.estimatedSavedBytes }
        NexusCard(
            onClick = {
                expandedTool = if (expandedTool == "PHOTOS") null else "PHOTOS"
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Otimizador de fotos (> 3 MB)",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Text(
                        text = "${state.photoCandidates.size} fotos JPEG/HEIC · poupança est. ${NexusFormatters.formatBytes(totalPhotoSavings)}",
                        style = NexusMonoTextStyles.metricSmall,
                        color = colors.accent2
                    )
                }
                Icon(
                    imageVector = NexusIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textDim
                )
            }

            if (expandedTool == "PHOTOS") {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.border)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Qualidade JPEG (mantém EXIF e guarda o original no Lixo Nexus por 30 dias):",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PhotoOptimizeQuality.entries.forEach { q ->
                        NexusChip(
                            text = q.labelPt,
                            color = if (state.selectedQuality == q) colors.accent2 else colors.textDim,
                            onClick = { onPhotoOptionsChanged(q, state.limitMaxResolution4000px) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Limitar resolução máxima a 4000 px",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text
                        )
                        Text(
                            text = "Redimensiona apenas fotos acima de 4000 px mantendo a proporção.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDim
                        )
                    }
                    Switch(
                        checked = state.limitMaxResolution4000px,
                        onCheckedChange = { onPhotoOptionsChanged(state.selectedQuality, it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.text, checkedTrackColor = colors.accent)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                if (state.photoCandidates.isEmpty()) {
                    Text(
                        text = "Nenhuma foto JPEG/HEIC acima de 3 MB encontrada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                } else {
                    state.photoCandidates.take(15).forEach { photo ->
                        val isSelected = photo.contentUri in selectedPhotoUris
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NexusCheckbox48dp(
                                checked = isSelected,
                                onToggle = {
                                    if (!isSelected) {
                                        if (photo.contentUri !in selectedPhotoUris) selectedPhotoUris.add(photo.contentUri)
                                    } else {
                                        selectedPhotoUris.remove(photo.contentUri)
                                    }
                                }
                            )
                            FileThumbnailOrTypeBadge(
                                contentUri = photo.contentUri,
                                displayName = photo.displayName,
                                mimeType = photo.mimeType
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = photo.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${NexusFormatters.formatBytes(photo.sizeBytes)} → ~${NexusFormatters.formatBytes(photo.estimatedCompressedBytes)} (−${NexusFormatters.formatBytes(photo.estimatedSavedBytes)})",
                                    style = NexusMonoTextStyles.metricSmall,
                                    color = colors.ok
                                )
                            }
                            NexusTextButton(
                                text = "Antes/Depois",
                                color = colors.accent2,
                                onClick = { previewPhoto = photo }
                            )
                        }
                    }

                    val chosenPhotos = state.photoCandidates.filter { it.contentUri in selectedPhotoUris }
                    if (chosenPhotos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val estSaveSelected = chosenPhotos.sumOf { it.estimatedSavedBytes }
                        NexusPrimaryGradientButton(
                            text = if (state.isOptimizingPhotos) {
                                "A otimizar fotos..."
                            } else {
                                "Otimizar ${chosenPhotos.size} foto(s) · Poupar ~${NexusFormatters.formatBytes(estSaveSelected)}"
                            },
                            enabled = !state.isOptimizingPhotos,
                            onClick = {
                                onOptimizePhotos(chosenPhotos) { count, saved ->
                                    selectedPhotoUris.clear()
                                    Toast.makeText(
                                        context,
                                        "$count foto(s) otimizada(s) · ${NexusFormatters.formatBytes(saved)} poupados",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // 3. Vista dedicada a Transferências (Download/)
        val downloadsTotalBytes = state.downloads.sumOf { it.sizeBytes }
        NexusCard(
            onClick = {
                expandedTool = if (expandedTool == "DOWNLOADS") null else "DOWNLOADS"
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Transferências (Download/)",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Text(
                        text = "${state.downloads.size} ficheiros · ${NexusFormatters.formatBytes(downloadsTotalBytes)}",
                        style = NexusMonoTextStyles.metricSmall,
                        color = colors.textDim
                    )
                }
                Icon(
                    imageVector = NexusIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textDim
                )
            }

            if (expandedTool == "DOWNLOADS") {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.border)
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DownloadsSortOrder.entries.forEach { order ->
                        NexusChip(
                            text = order.labelPt,
                            color = if (downloadsSort == order) colors.accent2 else colors.textDim,
                            onClick = { downloadsSort = order }
                        )
                    }
                    NexusTextButton(
                        text = if (selectedDownloadUris.size == state.downloads.size && state.downloads.isNotEmpty()) {
                            "Desmarcar todos"
                        } else {
                            "Selecionar todos"
                        },
                        color = colors.accent2,
                        onClick = {
                            if (selectedDownloadUris.size == state.downloads.size) {
                                selectedDownloadUris.clear()
                            } else {
                                selectedDownloadUris.clear()
                                selectedDownloadUris.addAll(state.downloads.map { it.contentUri })
                            }
                        }
                    )
                }

                val sortedDownloads = remember(state.downloads, downloadsSort) {
                    PhotoOptimizerCalculator.sortDownloads(state.downloads, downloadsSort)
                }

                sortedDownloads.take(25).forEach { dl ->
                    val checked = dl.contentUri in selectedDownloadUris
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NexusCheckbox48dp(
                            checked = checked,
                            onToggle = {
                                if (!checked) {
                                    if (dl.contentUri !in selectedDownloadUris) selectedDownloadUris.add(dl.contentUri)
                                } else {
                                    selectedDownloadUris.remove(dl.contentUri)
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dl.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${dl.fileTypeLabelPt} · há ${dl.ageDays} d · ${NexusFormatters.formatBytes(dl.sizeBytes)}",
                                style = NexusMonoTextStyles.metricSmall,
                                color = colors.textDim
                            )
                        }
                    }
                }

                val selectedDlItems = state.downloads.filter { it.contentUri in selectedDownloadUris }
                if (selectedDlItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val cleanableList = selectedDlItems.map { dl ->
                        ScannedFileCandidate(
                            id = dl.id,
                            contentUri = dl.contentUri,
                            displayName = dl.displayName,
                            relativePath = "Download/",
                            sizeBytes = dl.sizeBytes,
                            dateModifiedSeconds = dl.dateModifiedSeconds,
                            mimeType = dl.mimeType
                        )
                    }
                    NexusPrimaryGradientButton(
                        text = "Mover ${selectedDlItems.size} para o Lixo · ${NexusFormatters.formatBytes(selectedDlItems.sumOf { it.sizeBytes })}",
                        onClick = {
                            val sender = buildTrashIntent(cleanableList)
                            if (sender != null) {
                                pendingTrashItems = cleanableList
                                trashLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            } else {
                                onTrashConfirmed(cleanableList, emptyList())
                                selectedDownloadUris.clear()
                                onRefreshTools()
                            }
                        }
                    )
                }
            }
        }

        // 4. Vídeos grandes (> 100 MB)
        val videosTotalBytes = state.largeVideos.sumOf { it.sizeBytes }
        NexusCard(
            onClick = {
                expandedTool = if (expandedTool == "VIDEOS") null else "VIDEOS"
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vídeos grandes (> 100 MB)",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text
                    )
                    Text(
                        text = "${state.largeVideos.size} vídeos · ${NexusFormatters.formatBytes(videosTotalBytes)}",
                        style = NexusMonoTextStyles.metricSmall,
                        color = colors.textDim
                    )
                }
                Icon(
                    imageVector = NexusIcons.ChevronRight,
                    contentDescription = null,
                    tint = colors.textDim
                )
            }

            if (expandedTool == "VIDEOS") {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.border)
                Spacer(modifier = Modifier.height(10.dp))

                if (state.largeVideos.isEmpty()) {
                    Text(
                        text = "Nenhum vídeo acima de 100 MB encontrado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                } else {
                    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "PT")) }
                    state.largeVideos.take(20).forEach { video ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = video.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${NexusFormatters.formatBytes(video.sizeBytes)} · ${PhotoOptimizerCalculator.formatVideoDurationPt(video.durationMillis)} · ${dateFmt.format(Date(video.dateModifiedSeconds * 1000L))}",
                                style = NexusMonoTextStyles.metricSmall,
                                color = colors.textDim
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NexusTextButton(
                                    text = "Abrir na galeria",
                                    color = colors.accent2,
                                    onClick = {
                                        try {
                                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(Uri.parse(video.contentUri), video.mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(viewIntent)
                                        } catch (_: Exception) {
                                        }
                                    }
                                )
                                NexusTextButton(
                                    text = "Partilhar",
                                    color = colors.textDim,
                                    onClick = {
                                        try {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = video.mimeType
                                                putExtra(Intent.EXTRA_STREAM, Uri.parse(video.contentUri))
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, video.displayName))
                                        } catch (_: Exception) {
                                        }
                                    }
                                )
                                NexusTextButton(
                                    text = "Apagar",
                                    color = colors.danger,
                                    onClick = {
                                        val item = listOf(
                                            ScannedFileCandidate(
                                                id = video.id,
                                                contentUri = video.contentUri,
                                                displayName = video.displayName,
                                                relativePath = "Movies/",
                                                sizeBytes = video.sizeBytes,
                                                dateModifiedSeconds = video.dateModifiedSeconds,
                                                mimeType = video.mimeType
                                            )
                                        )
                                        val sender = buildTrashIntent(item)
                                        if (sender != null) {
                                            pendingTrashItems = item
                                            trashLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                        } else {
                                            onTrashConfirmed(item, emptyList())
                                            onRefreshTools()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Navegadores (atalhos honestos)
        NexusCard {
            Text(
                text = "Histórico e dados de navegadores",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Só o próprio navegador pode apagar o histórico. O Android isola os dados de navegação por segurança; usa os atalhos diretos abaixo:",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDim
            )
            Spacer(modifier = Modifier.height(8.dp))
            val installedBrowsers = state.browserShortcuts.filter { it.isInstalled }
            if (installedBrowsers.isEmpty()) {
                Text(
                    text = "Chrome, Samsung Internet ou Firefox não detetados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    installedBrowsers.forEach { browser ->
                        NexusChip(
                            text = "Definições · ${browser.browserName}",
                            color = colors.accent2,
                            onClick = {
                                NexusSystemIntents.openAppDetailsSettings(context, browser.packageName)
                            }
                        )
                    }
                }
            }
        }

        // 6. Registo de chamadas (exclusivo do flavor full, opcional)
        if (state.isCallLogAvailable) {
            NexusCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Registo de chamadas antigo (Full)",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text
                        )
                        Text(
                            text = "Opcional · apaga chamadas antigas apenas com a tua confirmação explícita.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDim
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (!state.hasCallLogPermissions) {
                    NexusTextButton(
                        text = "Conceder permissão ao registo de chamadas",
                        color = colors.accent2,
                        onClick = {
                            callLogPermissionsLauncher.launch(CallLogCleanerDelegate.requiredPermissions)
                        }
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(3, 6, 12).forEach { months ->
                            NexusChip(
                                text = "> $months meses",
                                color = if (state.callLogMonthsThreshold == months) colors.accent2 else colors.textDim,
                                onClick = { onCallLogMonthsChanged(months) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${state.oldCallLogsCount} chamadas com mais de ${state.callLogMonthsThreshold} meses",
                            style = NexusMonoTextStyles.metricSmall,
                            color = colors.textDim
                        )
                        if (state.oldCallLogsCount > 0) {
                            NexusTextButton(
                                text = "Apagar com confirmação",
                                color = colors.danger,
                                onClick = { showCallLogConfirmDialog = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoBeforeAfterPreviewDialog(
    candidate: PhotoOptimizerCandidate,
    quality: PhotoOptimizeQuality,
    limitMax4000px: Boolean,
    onDismiss: () -> Unit
) {
    val colors = NexusTheme.colors
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val (targetW, targetH) = remember(candidate, limitMax4000px) {
        PhotoOptimizerCalculator.calculateTargetDimensions(
            originalWidth = candidate.width,
            originalHeight = candidate.height,
            limitMax4000px = limitMax4000px
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.text,
        textContentColor = colors.textDim,
        title = {
            Text(
                text = "Antes / Depois · ${candidate.displayName}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceAlt)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
                            }
                        }
                        .clickable {
                            zoomScale = if (zoomScale < 2f) 2f else 1f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale
                        )
                    ) {
                        FileThumbnailOrTypeBadge(
                            contentUri = candidate.contentUri,
                            displayName = candidate.displayName,
                            mimeType = candidate.mimeType
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.bg.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Zoom ${String.format(Locale("pt", "PT"), "%.1fx", zoomScale)} (toca para alternar)",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.text
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Original", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
                        Text(
                            text = NexusFormatters.formatBytes(candidate.sizeBytes),
                            style = NexusMonoTextStyles.metricMedium,
                            color = colors.text
                        )
                        Text(
                            text = "${candidate.width} × ${candidate.height} px",
                            style = NexusMonoTextStyles.metricSmall,
                            color = colors.textDim
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Depois (${quality.qualityPercent} %)",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.accent2
                        )
                        Text(
                            text = "~${NexusFormatters.formatBytes(candidate.estimatedCompressedBytes)}",
                            style = NexusMonoTextStyles.metricMedium,
                            color = colors.ok
                        )
                        Text(
                            text = "$targetW × $targetH px",
                            style = NexusMonoTextStyles.metricSmall,
                            color = colors.textDim
                        )
                    }
                }

                Text(
                    text = "Poupança estimada: ${NexusFormatters.formatBytes(candidate.estimatedSavedBytes)} · EXIF preservado · O original fica no Lixo Nexus durante 30 dias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDim
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = colors.accent2)
            }
        }
    )
}
