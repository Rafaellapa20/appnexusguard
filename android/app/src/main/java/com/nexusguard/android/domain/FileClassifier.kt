package com.nexusguard.android.domain

import androidx.compose.runtime.Immutable
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest

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
    val partialHash: String? = null,
    val sha256Hash: String? = null
)

data class MediaStoreSqlFilter(
    val selection: String,
    val selectionArgs: List<String>
)

object FileClassifier {
    const val LARGE_FILE_THRESHOLD_BYTES: Long = 100L * 1024L * 1024L // 100 MB
    const val MIN_DUPLICATE_SIZE_BYTES: Long = 1024L * 1024L // 1 MB
    const val PARTIAL_HASH_CHUNK_BYTES: Int = 64 * 1024 // 64 KB
    const val OLD_DOWNLOAD_DAYS: Long = 90L
    const val OLD_SCREENSHOT_DAYS: Long = 180L // 6 meses

    // ========================================================================
    // Filtros SQL diretos para o MediaStore (Fase 3)
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
