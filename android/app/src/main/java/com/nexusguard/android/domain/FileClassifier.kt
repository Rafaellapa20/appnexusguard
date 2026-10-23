package com.nexusguard.android.domain

import androidx.compose.runtime.Immutable
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.abs

@Immutable
data class ScannedFileCandidate(
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val relativePath: String,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    val mimeType: String,
    val archivePackageName: String? = null,
    val archiveVersionCode: Long? = null,
    val installedVersionCode: Long? = null,
    val partialHash: String? = null,
    val sha256Hash: String? = null,
    val dHash64: Long? = null,
    val laplacianSharpness: Double? = null,
    val similarGroupId: String? = null,
    val isSuggestedToKeepInGroup: Boolean = false,
    val messagingAppName: String? = null,
    val messagingMediaType: String? = null,
    val isPreselectedByDefault: Boolean = true
)

@Immutable
data class SimilarPhotoGroup(
    val groupId: String,
    val keepPhoto: ScannedFileCandidate,
    val duplicatePhotos: List<ScannedFileCandidate>,
    val allPhotos: List<ScannedFileCandidate>
)

@Immutable
data class AppCacheCandidate(
    val packageName: String,
    val appName: String,
    val monogram: String,
    val cacheBytes: Long,
    val totalAppBytes: Long
)

data class MediaStoreSqlFilter(
    val selection: String,
    val selectionArgs: List<String>
)

object FileClassifier {
    const val LARGE_FILE_THRESHOLD_BYTES: Long = 100L * 1024L * 1024L // 100 MB
    const val MESSAGING_LARGE_VIDEO_BYTES: Long = 50L * 1024L * 1024L // 50 MB
    const val MIN_DUPLICATE_SIZE_BYTES: Long = 1024L * 1024L // 1 MB
    const val PARTIAL_HASH_CHUNK_BYTES: Int = 64 * 1024 // 64 KB
    const val OLD_DOWNLOAD_DAYS: Long = 90L
    const val OLD_VOICE_NOTE_DAYS: Long = 90L
    const val OLD_SCREENSHOT_DAYS: Long = 180L // 6 meses
    const val UNINSTALLED_APK_OLD_DAYS: Long = 30L
    const val SIMILAR_MAX_HAMMING_DISTANCE: Int = 6
    const val SIMILAR_MAX_TIME_DIFF_SECONDS: Long = 10L * 60L // 10 minutos
    const val BLURRY_LAPLACIAN_THRESHOLD: Double = 85.0

    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+){2,}$")
    private val JUNK_EXTENSIONS = setOf("tmp", "log", "bak", "crdownload", "part")

    // ========================================================================
    // Filtros SQL diretos para o MediaStore
    // ========================================================================
    fun buildLargeFilesSqlFilter(): MediaStoreSqlFilter {
        return MediaStoreSqlFilter(
            selection = "_size > ?",
            selectionArgs = listOf(LARGE_FILE_THRESHOLD_BYTES.toString())
        )
    }

    fun buildOldDownloadsSqlFilter(nowSeconds: Long, useRelativePath: Boolean = true): MediaStoreSqlFilter {
        val cutoffSeconds = nowSeconds - (OLD_DOWNLOAD_DAYS * 24L * 3600L)
        return if (useRelativePath) {
            MediaStoreSqlFilter(
                selection = "relative_path LIKE 'Download/%' AND date_modified < ? AND date_modified > 0",
                selectionArgs = listOf(cutoffSeconds.toString())
            )
        } else {
            MediaStoreSqlFilter(
                selection = "_data LIKE '%/Download/%' AND date_modified < ? AND date_modified > 0",
                selectionArgs = listOf(cutoffSeconds.toString())
            )
        }
    }

    fun buildOldScreenshotsSqlFilter(nowSeconds: Long, useRelativePath: Boolean = true): MediaStoreSqlFilter {
        val cutoffSeconds = nowSeconds - (OLD_SCREENSHOT_DAYS * 24L * 3600L)
        return if (useRelativePath) {
            MediaStoreSqlFilter(
                selection = "relative_path LIKE '%Screenshots%' AND date_modified < ? AND date_modified > 0",
                selectionArgs = listOf(cutoffSeconds.toString())
            )
        } else {
            MediaStoreSqlFilter(
                selection = "_data LIKE '%Screenshots%' AND date_modified < ? AND date_modified > 0",
                selectionArgs = listOf(cutoffSeconds.toString())
            )
        }
    }

    fun buildApkSqlFilter(): MediaStoreSqlFilter {
        return MediaStoreSqlFilter(
            selection = "(mime_type = 'application/vnd.android.package-archive' OR _display_name LIKE '%.apk') AND _size > 0",
            selectionArgs = emptyList()
        )
    }

    fun buildDuplicatesCandidateSqlFilter(): MediaStoreSqlFilter {
        return MediaStoreSqlFilter(
            selection = "_size >= ?",
            selectionArgs = listOf(MIN_DUPLICATE_SIZE_BYTES.toString())
        )
    }

    fun buildImagesSqlFilter(): MediaStoreSqlFilter {
        return MediaStoreSqlFilter(
            selection = "media_type = 1 AND _size > 10240",
            selectionArgs = emptyList()
        )
    }

    fun buildFilesystemJunkSqlFilter(): MediaStoreSqlFilter {
        return MediaStoreSqlFilter(
            selection = "_size = 0 OR _display_name LIKE '%.tmp' OR _display_name LIKE '%.log' OR _display_name LIKE '%.bak' OR _display_name LIKE '%.crdownload' OR _display_name LIKE '%.part'",
            selectionArgs = emptyList()
        )
    }

    // ========================================================================
    // Regras de domínio e verificação
    // ========================================================================
    fun isLargeFile(sizeBytes: Long): Boolean {
        return sizeBytes > LARGE_FILE_THRESHOLD_BYTES
    }

    fun isOldDownload(
        dateModifiedSeconds: Long,
        nowSeconds: Long,
        relativePath: String
    ): Boolean {
        val isInDownloads = relativePath.contains("Download", ignoreCase = true)
        if (!isInDownloads) return false
        val cutoffSeconds = nowSeconds - (OLD_DOWNLOAD_DAYS * 24L * 3600L)
        return dateModifiedSeconds in 1 until cutoffSeconds
    }

    fun isOldScreenshot(
        dateModifiedSeconds: Long,
        nowSeconds: Long,
        displayName: String,
        relativePath: String
    ): Boolean {
        val isScreenshot = relativePath.contains("Screenshots", ignoreCase = true) ||
            displayName.startsWith("Screenshot", ignoreCase = true)
        if (!isScreenshot) return false
        val cutoffSeconds = nowSeconds - (OLD_SCREENSHOT_DAYS * 24L * 3600L)
        return dateModifiedSeconds in 1 until cutoffSeconds
    }

    fun isInstalledApk(
        candidate: ScannedFileCandidate,
        installedPackages: Set<String>
    ): Boolean {
        val isApk = candidate.displayName.endsWith(".apk", ignoreCase = true) ||
            candidate.mimeType.equals("application/vnd.android.package-archive", ignoreCase = true)
        if (!isApk) return false
        val pkg = candidate.archivePackageName ?: return false
        return pkg in installedPackages
    }

    /**
     * Fase 1 (7): APK obsoleto instalado: o pacote está instalado e a versionCode do APK é <= à versão instalada.
     */
    fun isObsoleteInstalledApk(
        candidate: ScannedFileCandidate,
        installedVersions: Map<String, Long>
    ): Boolean {
        val isApk = candidate.displayName.endsWith(".apk", ignoreCase = true) ||
            candidate.mimeType.equals("application/vnd.android.package-archive", ignoreCase = true)
        if (!isApk) return false
        val pkg = candidate.archivePackageName ?: return false
        val installedVer = installedVersions[pkg] ?: return false
        val apkVer = candidate.archiveVersionCode
        return apkVer == null || apkVer <= installedVer
    }

    /**
     * Fase 1 (7): APK de app que NÃO está instalada e tem mais de 30 dias (separado, não pré-selecionado).
     */
    fun isUninstalledOldApk(
        candidate: ScannedFileCandidate,
        installedPackages: Set<String>,
        nowSeconds: Long,
        minDaysOld: Long = UNINSTALLED_APK_OLD_DAYS
    ): Boolean {
        val isApk = candidate.displayName.endsWith(".apk", ignoreCase = true) ||
            candidate.mimeType.equals("application/vnd.android.package-archive", ignoreCase = true)
        if (!isApk) return false
        val pkg = candidate.archivePackageName
        if (pkg != null && pkg in installedPackages) return false
        val cutoffSeconds = nowSeconds - (minDaysOld * 24L * 3600L)
        return candidate.dateModifiedSeconds in 1 until cutoffSeconds
    }

    /**
     * Fase 1 (2): Restos de pacotes desinstalados (`com.x.y`).
     */
    fun isPackageLikeFolderName(folderName: String): Boolean {
        if (!PACKAGE_NAME_REGEX.matches(folderName)) return false
        val lower = folderName.lowercase()
        val commonTlds = listOf("com.", "org.", "net.", "io.", "pt.", "eu.", "de.", "uk.", "jp.", "br.", "app.", "me.", "tv.", "co.")
        return commonTlds.any { lower.startsWith(it) }
    }

    fun findUninstalledPackageFolders(
        folderNamesToPaths: Map<String, String>,
        installedPackages: Set<String>,
        selfPackage: String = "com.nexusguard.android"
    ): List<String> {
        return folderNamesToPaths.entries
            .filter { (folderName, _) ->
                isPackageLikeFolderName(folderName) &&
                    folderName != selfPackage &&
                    folderName !in installedPackages
            }
            .map { it.value }
            .sorted()
    }

    /**
     * Fase 1 (3): Lixo do sistema de ficheiros:
     * DCIM/.thumbnails, *.tmp, *.log, *.bak, *.crdownload, *.part, ficheiros de 0 bytes,
     * e pastas .cache/cache em Download/ e Documents/.
     */
    fun isFilesystemJunkFile(
        displayName: String,
        relativePath: String,
        sizeBytes: Long
    ): Boolean {
        if (sizeBytes == 0L) return true
        val normPath = relativePath.replace('\\', '/')
        if (normPath.contains("DCIM/.thumbnails", ignoreCase = true) ||
            normPath.contains("/.thumbnails", ignoreCase = true) ||
            normPath.startsWith(".thumbnails", ignoreCase = true)
        ) {
            return true
        }
        val ext = displayName.substringAfterLast('.', "").lowercase()
        if (ext in JUNK_EXTENSIONS) return true

        val isDownloadOrDocs = normPath.startsWith("Download", ignoreCase = true) ||
            normPath.contains("/Download/", ignoreCase = true) ||
            normPath.startsWith("Documents", ignoreCase = true) ||
            normPath.contains("/Documents/", ignoreCase = true)

        if (isDownloadOrDocs) {
            val segments = normPath.split('/').filter { it.isNotBlank() }
            if (segments.any { it.equals(".cache", ignoreCase = true) || it.equals("cache", ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    /**
     * Fase 1 (4): Mensagens (WhatsApp, Telegram, Signal):
     * media enviada (/Sent), notas de voz > 90 dias, vídeos > 50 MB e .Statuses antigos.
     */
    fun classifyMessagingMedia(
        displayName: String,
        relativePath: String,
        sizeBytes: Long,
        dateModifiedSeconds: Long,
        nowSeconds: Long
    ): Pair<String, String>? {
        val normPath = relativePath.replace('\\', '/')
        val appName = when {
            normPath.contains("WhatsApp", ignoreCase = true) -> "WhatsApp"
            normPath.contains("Telegram", ignoreCase = true) -> "Telegram"
            normPath.contains("Signal", ignoreCase = true) -> "Signal"
            else -> return null
        }

        // 1. Media enviada (/Sent)
        if (normPath.contains("/Sent/", ignoreCase = true) || normPath.endsWith("/Sent", ignoreCase = true)) {
            return appName to "Media enviada"
        }

        // 2. .Statuses antigos (> 24 h)
        if (normPath.contains(".Statuses", ignoreCase = true)) {
            val statusCutoff = nowSeconds - 24L * 3600L
            if (dateModifiedSeconds in 1 until statusCutoff) {
                return appName to "Estados antigos (.Statuses)"
            }
        }

        // 3. Notas de voz com mais de 90 dias
        val isVoiceNote = normPath.contains("Voice Notes", ignoreCase = true) ||
            normPath.contains("Telegram Audio", ignoreCase = true) ||
            displayName.endsWith(".opus", ignoreCase = true) ||
            (displayName.startsWith("PTT-", ignoreCase = true) && displayName.endsWith(".ogg", ignoreCase = true))
        if (isVoiceNote) {
            val voiceCutoff = nowSeconds - (OLD_VOICE_NOTE_DAYS * 24L * 3600L)
            if (dateModifiedSeconds in 1 until voiceCutoff) {
                return appName to "Notas de voz (> 90 dias)"
            }
        }

        // 4. Vídeos com mais de 50 MB
        val isVideo = normPath.contains("Video", ignoreCase = true) ||
            displayName.endsWith(".mp4", ignoreCase = true) ||
            displayName.endsWith(".mkv", ignoreCase = true) ||
            displayName.endsWith(".mov", ignoreCase = true)
        if (isVideo && sizeBytes > MESSAGING_LARGE_VIDEO_BYTES) {
            return appName to "Vídeos (> 50 MB)"
        }

        return null
    }

    /**
     * Fase 1 (5): dHash 64-bit sobre grelha 9x8 de intensidades em escala de cinzentos (0..255).
     * Compara pixel[row, col] > pixel[row, col + 1] para 8 linhas x 8 colunas = 64 bits.
     */
    fun computeDHash64FromGrayscale9x8(grayscale9x8: IntArray): Long {
        require(grayscale9x8.size >= 72) { "A grelha 9x8 requer pelo menos 72 píxeis" }
        var hash = 0L
        var bitIndex = 0
        for (row in 0 until 8) {
            val rowOffset = row * 9
            for (col in 0 until 8) {
                val left = grayscale9x8[rowOffset + col]
                val right = grayscale9x8[rowOffset + col + 1]
                if (left > right) {
                    hash = hash or (1L shl bitIndex)
                }
                bitIndex++
            }
        }
        return hash
    }

    /**
     * Converte uma imagem ARGB (ex.: miniatura 64x64) numa grelha 9x8 por amostragem de blocos
     * e calcula o dHash de 64 bits.
     */
    fun computeDHash64FromArgb(argbPixels: IntArray, width: Int, height: Int): Long {
        if (width < 9 || height < 8 || argbPixels.size < width * height) return 0L
        val gray9x8 = IntArray(72)
        for (r in 0 until 8) {
            val y = (r * height) / 8
            for (c in 0 until 9) {
                val x = (c * width) / 9
                val argb = argbPixels[y * width + x]
                val red = (argb shr 16) and 0xFF
                val green = (argb shr 8) and 0xFF
                val blue = argb and 0xFF
                gray9x8[r * 9 + c] = (red * 77 + green * 150 + blue * 29) shr 8
            }
        }
        return computeDHash64FromGrayscale9x8(gray9x8)
    }

    /**
     * Distância de Hamming entre dois dHash de 64 bits.
     */
    fun hammingDistance64(hashA: Long, hashB: Long): Int {
        return java.lang.Long.bitCount(hashA xor hashB)
    }

    /**
     * Fase 1 (5 e 6): Variância do operador Laplaciano 3x3 ([0, 1, 0; 1, -4, 1; 0, 1, 0])
     * sobre matriz em escala de cinzentos (0..255). Valores mais altos indicam maior nitidez.
     */
    fun computeLaplacianVariance(grayscalePixels: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || grayscalePixels.size < width * height) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            val rowOffset = y * width
            for (x in 1 until width - 1) {
                val idx = rowOffset + x
                val center = grayscalePixels[idx]
                val top = grayscalePixels[idx - width]
                val bottom = grayscalePixels[idx + width]
                val left = grayscalePixels[idx - 1]
                val right = grayscalePixels[idx + 1]
                val lap = (top + bottom + left + right - 4 * center).toDouble()
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return (sumSq / count) - (mean * mean)
    }

    fun computeLaplacianVarianceFromArgb(argbPixels: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || argbPixels.size < width * height) return 0.0
        val gray = IntArray(width * height)
        for (i in 0 until width * height) {
            val argb = argbPixels[i]
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            gray[i] = (r * 77 + g * 150 + b * 29) shr 8
        }
        return computeLaplacianVariance(gray, width, height)
    }

    fun isBlurryPhoto(
        laplacianVariance: Double,
        threshold: Double = BLURRY_LAPLACIAN_THRESHOLD
    ): Boolean {
        return laplacianVariance < threshold
    }

    /**
     * Fase 1 (5): Agrupa fotos semelhantes (distância de Hamming <= 6 e diferença temporal <= 10 min).
     * Por cada grupo, sugere manter a mais nítida (maior variância do Laplaciano) e marca as outras.
     */
    fun groupSimilarPhotos(
        photos: List<ScannedFileCandidate>,
        maxHammingDistance: Int = SIMILAR_MAX_HAMMING_DISTANCE,
        maxTimeDiffSeconds: Long = SIMILAR_MAX_TIME_DIFF_SECONDS
    ): List<SimilarPhotoGroup> {
        val valid = photos
            .filter { it.dHash64 != null }
            .sortedBy { it.dateModifiedSeconds }
        if (valid.size < 2) return emptyList()

        val visited = BooleanArray(valid.size)
        val groups = mutableListOf<SimilarPhotoGroup>()

        for (i in valid.indices) {
            if (visited[i]) continue
            val seed = valid[i]
            val seedHash = seed.dHash64 ?: continue
            val cluster = mutableListOf(seed)
            visited[i] = true

            for (j in (i + 1) until valid.size) {
                if (visited[j]) continue
                val other = valid[j]
                val timeDiff = abs(other.dateModifiedSeconds - seed.dateModifiedSeconds)
                if (timeDiff > maxTimeDiffSeconds) {
                    // Como a lista está ordenada por data, podemos parar a pesquisa para este seed
                    break
                }
                val otherHash = other.dHash64 ?: continue
                if (hammingDistance64(seedHash, otherHash) <= maxHammingDistance) {
                    cluster.add(other)
                    visited[j] = true
                }
            }

            if (cluster.size > 1) {
                val groupId = "sim_${seed.id}"
                // Escolhe a foto mais nítida (maior laplacianSharpness; empate por maior tamanho e mais antiga)
                val sharpest = cluster.maxWithOrNull(
                    compareBy<ScannedFileCandidate> { it.laplacianSharpness ?: 0.0 }
                        .thenBy { it.sizeBytes }
                        .thenByDescending { it.dateModifiedSeconds }
                ) ?: cluster.first()

                val decoratedAll = cluster.map { item ->
                    val isKeep = item.id == sharpest.id
                    item.copy(
                        similarGroupId = groupId,
                        isSuggestedToKeepInGroup = isKeep,
                        isPreselectedByDefault = false // Semelhantes vêm desmarcadas por segurança na Fase 3, mas sabemos qual sugerir manter vs apagar
                    )
                }
                val keepDecorated = decoratedAll.first { it.isSuggestedToKeepInGroup }
                val duplicatesDecorated = decoratedAll.filter { !it.isSuggestedToKeepInGroup }

                groups.add(
                    SimilarPhotoGroup(
                        groupId = groupId,
                        keepPhoto = keepDecorated,
                        duplicatePhotos = duplicatesDecorated,
                        allPhotos = decoratedAll
                    )
                )
            }
        }
        return groups
    }

    /**
     * Fase 1 (8): Deteção de pastas vazias de baixo para cima (uma pasta que só contém pastas vazias também conta),
     * sem limite artificial de 40, ignorando pastas que contêm `.nomedia`.
     */
    fun findEmptyFoldersBottomUp(rootDir: File): List<String> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()
        val emptyDirs = HashSet<String>()
        val nomediaProtectedDirs = HashSet<String>()

        try {
            rootDir.walkBottomUp().forEach { dir ->
                if (!dir.isDirectory || dir == rootDir) return@forEach
                val children = dir.listFiles() ?: return@forEach

                // Ignora pastas que têm .nomedia (e protege os seus pais de serem dados como vazios)
                val hasNomedia = children.any { it.name.equals(".nomedia", ignoreCase = true) }
                if (hasNomedia) {
                    nomediaProtectedDirs.add(dir.absolutePath)
                    return@forEach
                }

                val allChildrenAreEmptyDirs = children.all { child ->
                    child.isDirectory &&
                        child.absolutePath in emptyDirs &&
                        child.absolutePath !in nomediaProtectedDirs
                }

                if (children.isEmpty() || allChildrenAreEmptyDirs) {
                    emptyDirs.add(dir.absolutePath)
                } else {
                    nomediaProtectedDirs.add(dir.absolutePath)
                }
            }
        } catch (_: Exception) {
        }

        return emptyDirs.sorted()
    }

    /**
     * Calcula o hash parcial: SHA-256 dos primeiros 64 KB + últimos 64 KB + tamanho (8 bytes).
     */
    fun computePartialSha256(
        firstChunk: ByteArray,
        lastChunk: ByteArray,
        sizeBytes: Long
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(firstChunk)
        digest.update(lastChunk)
        val sizeBytesBuffer = ByteBuffer.allocate(8).putLong(sizeBytes).array()
        digest.update(sizeBytesBuffer)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computePartialSha256FromByteArray(allBytes: ByteArray): String {
        val chunkSize = PARTIAL_HASH_CHUNK_BYTES
        val first = if (allBytes.size <= chunkSize) {
            allBytes
        } else {
            allBytes.copyOfRange(0, chunkSize)
        }
        val last = if (allBytes.size <= chunkSize) {
            allBytes
        } else {
            allBytes.copyOfRange(allBytes.size - chunkSize, allBytes.size)
        }
        return computePartialSha256(first, last, allBytes.size.toLong())
    }

    /**
     * Calcula o SHA-256 completo em streaming (sem carregar o ficheiro inteiro em memória).
     */
    fun computeFullSha256Streaming(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Deteção de duplicados em 2 fases:
     * 1. Agrupa por tamanho e ignora ficheiros < 1 MB;
     * 2. Calcula hash parcial (primeiros e últimos 64 KB + tamanho);
     * 3. Só para quem colidir no passo 2, calcula o SHA-256 completo;
     * 4. Conserva sempre o original mais antigo.
     */
    fun findDuplicateExtrasTwoStage(
        candidates: List<ScannedFileCandidate>,
        minSizeBytes: Long = MIN_DUPLICATE_SIZE_BYTES,
        partialHashProvider: (ScannedFileCandidate) -> String?,
        fullHashProvider: (ScannedFileCandidate) -> String?
    ): List<ScannedFileCandidate> {
        val sameSizeGroups = candidates
            .filter { it.sizeBytes >= minSizeBytes }
            .groupBy { it.sizeBytes }
            .values
            .filter { it.size > 1 }

        val duplicateExtras = mutableListOf<ScannedFileCandidate>()

        for (sizeGroup in sameSizeGroups) {
            // Passo 2: Hash parcial
            val byPartialHash = sizeGroup
                .mapNotNull { item ->
                    val pHash = item.partialHash ?: partialHashProvider(item)
                    if (pHash != null) pHash to item.copy(partialHash = pHash) else null
                }
                .groupBy({ it.first }, { it.second })

            for ((_, partialGroup) in byPartialHash) {
                if (partialGroup.size > 1) {
                    // Passo 3: Só calcula SHA-256 completo para colisões do hash parcial
                    val byFullHash = partialGroup
                        .mapNotNull { item ->
                            val fHash = item.sha256Hash ?: fullHashProvider(item)
                            if (fHash != null) fHash to item.copy(sha256Hash = fHash) else null
                        }
                        .groupBy({ it.first }, { it.second })

                    for ((_, identicalItems) in byFullHash) {
                        if (identicalItems.size > 1) {
                            val sorted = identicalItems.sortedWith(
                                compareBy<ScannedFileCandidate> { it.dateModifiedSeconds }
                                    .thenBy { it.id }
                            )
                            duplicateExtras.addAll(sorted.drop(1))
                        }
                    }
                }
            }
        }
        return duplicateExtras
    }

    /**
     * Compatibilidade com testes existentes de 1 fase.
     */
    fun findDuplicateExtras(
        candidates: List<ScannedFileCandidate>,
        hashProvider: (ScannedFileCandidate) -> String?
    ): List<ScannedFileCandidate> {
        return findDuplicateExtrasTwoStage(
            candidates = candidates,
            minSizeBytes = 1025L,
            partialHashProvider = hashProvider,
            fullHashProvider = hashProvider
        )
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
