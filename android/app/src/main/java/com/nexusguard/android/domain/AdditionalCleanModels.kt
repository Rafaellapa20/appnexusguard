package com.nexusguard.android.domain

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class PhotoOptimizeQuality(val qualityPercent: Int, val labelPt: String) {
    QUALITY_80(80, "80 % · Máxima poupança"),
    QUALITY_85(85, "85 % · Equilibrado"),
    QUALITY_90(90, "90 % · Alta fidelidade")
}

enum class DownloadsSortOrder(val labelPt: String) {
    BY_SIZE_DESC("Maior tamanho"),
    BY_AGE_OLDEST("Mais antigos"),
    BY_TYPE("Por tipo")
}

@Immutable
data class PhotoOptimizerCandidate(
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateModifiedSeconds: Long,
    val mimeType: String,
    val estimatedCompressedBytes: Long,
    val estimatedSavedBytes: Long
)

@Immutable
data class DownloadFileItem(
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    val mimeType: String,
    val fileTypeLabelPt: String,
    val ageDays: Int
)

@Immutable
data class LargeVideoItem(
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationMillis: Long,
    val dateModifiedSeconds: Long,
    val mimeType: String
)

@Immutable
data class BrowserPrivacyShortcut(
    val packageName: String,
    val browserName: String,
    val isInstalled: Boolean
)

object PhotoOptimizerCalculator {

    const val MIN_PHOTO_SIZE_BYTES: Long = 3L * 1024L * 1024L // > 3 MB
    const val MAX_OPTIONAL_RESOLUTION_PX: Int = 4000
    const val TRASH_RETENTION_DAYS: Int = 30

    fun isEligiblePhoto(sizeBytes: Long, mimeType: String, displayName: String): Boolean {
        if (sizeBytes <= MIN_PHOTO_SIZE_BYTES) return false
        val lowerMime = mimeType.lowercase()
        val lowerName = displayName.lowercase()
        return lowerMime.contains("jpeg") ||
            lowerMime.contains("jpg") ||
            lowerMime.contains("heic") ||
            lowerMime.contains("heif") ||
            lowerName.endsWith(".jpg") ||
            lowerName.endsWith(".jpeg") ||
            lowerName.endsWith(".heic") ||
            lowerName.endsWith(".heif")
    }

    /**
     * Calcula as dimensões finais mantendo o rácio de aspeto quando `limitMax4000px` está ativo.
     */
    fun calculateTargetDimensions(
        originalWidth: Int,
        originalHeight: Int,
        limitMax4000px: Boolean,
        maxDimensionPx: Int = MAX_OPTIONAL_RESOLUTION_PX
    ): Pair<Int, Int> {
        if (originalWidth <= 0 || originalHeight <= 0) return originalWidth.coerceAtLeast(0) to originalHeight.coerceAtLeast(0)
        val longestSide = max(originalWidth, originalHeight)
        if (!limitMax4000px || longestSide <= maxDimensionPx) {
            return originalWidth to originalHeight
        }
        val scale = maxDimensionPx.toDouble() / longestSide.toDouble()
        val targetW = (originalWidth * scale).roundToInt().coerceAtLeast(1)
        val targetH = (originalHeight * scale).roundToInt().coerceAtLeast(1)
        return targetW to targetH
    }

    /**
     * Estima o tamanho após recompressão JPEG com base na qualidade escolhida (80/85/90)
     * e no fator de redução de área de píxeis quando `limitMax4000px` reduz a resolução.
     */
    fun estimateCompressedSizeBytes(
        originalSizeBytes: Long,
        qualityPercent: Int,
        originalWidth: Int,
        originalHeight: Int,
        limitMax4000px: Boolean
    ): Long {
        if (originalSizeBytes <= 0L) return 0L
        val qualityRatio = when {
            qualityPercent <= 80 -> 0.34
            qualityPercent <= 85 -> 0.42
            else -> 0.54
        }
        val (targetW, targetH) = calculateTargetDimensions(originalWidth, originalHeight, limitMax4000px)
        val areaRatio = if (originalWidth > 0 && originalHeight > 0) {
            val origArea = originalWidth.toDouble() * originalHeight.toDouble()
            val targetArea = targetW.toDouble() * targetH.toDouble()
            (targetArea / origArea).coerceIn(0.15, 1.0)
        } else {
            1.0
        }
        val estimated = (originalSizeBytes.toDouble() * qualityRatio * areaRatio).roundToLong()
        return estimated.coerceIn(128L * 1024L, originalSizeBytes)
    }

    fun estimateSavedBytes(
        originalSizeBytes: Long,
        qualityPercent: Int,
        originalWidth: Int,
        originalHeight: Int,
        limitMax4000px: Boolean
    ): Long {
        val compressed = estimateCompressedSizeBytes(
            originalSizeBytes = originalSizeBytes,
            qualityPercent = qualityPercent,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            limitMax4000px = limitMax4000px
        )
        return (originalSizeBytes - compressed).coerceAtLeast(0L)
    }

    fun classifyDownloadFileTypePt(mimeType: String, displayName: String): String {
        val lowerMime = mimeType.lowercase()
        val lowerName = displayName.lowercase()
        return when {
            lowerMime.contains("package-archive") || lowerName.endsWith(".apk") -> "APK"
            lowerMime.startsWith("Document") || lowerMime.contains("pdf") ||
                lowerName.endsWith(".pdf") || lowerName.endsWith(".docx") ||
                lowerName.endsWith(".xlsx") || lowerName.endsWith(".txt") -> "Documento"
            lowerMime.startsWith("image/") -> "Imagem"
            lowerMime.startsWith("video/") -> "Vídeo"
            lowerMime.startsWith("audio/") -> "Áudio"
            lowerMime.contains("zip") || lowerMime.contains("rar") ||
                lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") -> "Arquivo"
            else -> "Outro"
        }
    }

    fun sortDownloads(
        items: List<DownloadFileItem>,
        sortOrder: DownloadsSortOrder
    ): List<DownloadFileItem> {
        return when (sortOrder) {
            DownloadsSortOrder.BY_SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
            DownloadsSortOrder.BY_AGE_OLDEST -> items.sortedByDescending { it.ageDays }
            DownloadsSortOrder.BY_TYPE -> items.sortedWith(
                compareBy<DownloadFileItem> { it.fileTypeLabelPt }.thenByDescending { it.sizeBytes }
            )
        }
    }

    fun formatVideoDurationPt(durationMillis: Long): String {
        if (durationMillis <= 0L) return "Duração N/D"
        val totalSeconds = durationMillis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
