package com.nexusguard.android.domain

import androidx.compose.runtime.Immutable
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
    val sha256Hash: String? = null
)

object FileClassifier {
    const val LARGE_FILE_THRESHOLD_BYTES: Long = 100L * 1024L * 1024L // 100 MB
    const val OLD_DOWNLOAD_DAYS: Long = 90L
    const val OLD_SCREENSHOT_DAYS: Long = 180L // 6 meses

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
     * Devolve apenas as cópias extra (mantendo sempre o ficheiro original mais antigo de cada grupo)
     * para que o utilizador nunca apague todas as cópias de um ficheiro duplicado por engano.
     */
    fun findDuplicateExtras(
        candidates: List<ScannedFileCandidate>,
        hashProvider: (ScannedFileCandidate) -> String?
    ): List<ScannedFileCandidate> {
        val sameSizeGroups = candidates
            .filter { it.sizeBytes > 1024L }
            .groupBy { it.sizeBytes }
            .values
            .filter { it.size > 1 }

        val duplicateExtras = mutableListOf<ScannedFileCandidate>()
        for (sizeGroup in sameSizeGroups) {
            val byHash = sizeGroup
                .mapNotNull { item ->
                    val hash = item.sha256Hash ?: hashProvider(item)
                    if (hash != null) hash to item.copy(sha256Hash = hash) else null
                }
                .groupBy({ it.first }, { it.second })

            for ((_, identicalItems) in byHash) {
                if (identicalItems.size > 1) {
                    // Ordena por data de modificação (mantém o primeiro original, marca os restantes como duplicados)
                    val sorted = identicalItems.sortedBy { it.dateModifiedSeconds }
                    duplicateExtras.addAll(sorted.drop(1))
                }
            }
        }
        return duplicateExtras
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
