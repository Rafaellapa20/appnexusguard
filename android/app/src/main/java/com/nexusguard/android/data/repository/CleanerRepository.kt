package com.nexusguard.android.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.domain.FileClassifier
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ScannedFileCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class CleanCategoryType(
    val titlePt: String,
    val emptySubtitlePt: String
) {
    INSTALLED_APKS("APK já instalados", "Transferências · 0 ficheiros"),
    LARGE_FILES("Ficheiros grandes", "> 100 MB · 0 ficheiros"),
    OLD_DOWNLOADS("Transferências antigas", "> 90 dias · 0 ficheiros"),
    DUPLICATES("Duplicados", "Por tamanho e SHA-256 · 0 ficheiros"),
    OLD_SCREENSHOTS("Capturas de ecrã antigas", "> 6 meses · 0 ficheiros"),
    EMPTY_FOLDERS("Pastas vazias", "0 pastas vazias")
}

data class CleanCategoryGroup(
    val type: CleanCategoryType,
    val subtitlePt: String,
    val files: List<ScannedFileCandidate>,
    val emptyFoldersPaths: List<String> = emptyList(),
    val totalSizeBytes: Long
)

data class CleanScanReport(
    val hasMediaPermission: Boolean,
    val hasManageStoragePermission: Boolean,
    val categories: List<CleanCategoryGroup>,
    val totalCleanableBytes: Long,
    val scannedFilesCount: Int
)

@Singleton
class CleanerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NexusGuardDao
) {

    fun hasMediaReadPermission(): Boolean {
        if (hasManageExternalStoragePermission()) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasManageExternalStoragePermission(): Boolean {
        return if (BuildConfig.IS_FULL_FLAVOR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            false
        }
    }

    suspend fun scanRealDeviceStorage(): CleanScanReport = withContext(Dispatchers.IO) {
        val mediaGranted = hasMediaReadPermission()
        val manageGranted = hasManageExternalStoragePermission()
        val nowSeconds = System.currentTimeMillis() / 1000L

        val installedPackages: Set<String> = try {
            context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        val allCandidates = queryMediaStoreFiles(installedPackages)

        val installedApks = allCandidates.filter {
            FileClassifier.isInstalledApk(it, installedPackages)
        }
        val largeFiles = allCandidates.filter {
            FileClassifier.isLargeFile(it.sizeBytes)
        }
        val oldDownloads = allCandidates.filter {
            FileClassifier.isOldDownload(it.dateModifiedSeconds, nowSeconds, it.relativePath)
        }
        val duplicates = FileClassifier.findDuplicateExtras(allCandidates) { candidate ->
            computeContentUriSha256(Uri.parse(candidate.contentUri))
        }
        val oldScreenshots = allCandidates.filter {
            FileClassifier.isOldScreenshot(
                it.dateModifiedSeconds,
                nowSeconds,
                it.displayName,
                it.relativePath
            )
        }
        val emptyFolders = scanEmptyFolders()

        val groups = listOf(
            CleanCategoryGroup(
                type = CleanCategoryType.INSTALLED_APKS,
                subtitlePt = "Transferências · ${installedApks.size} ficheiro(s)",
                files = installedApks,
                totalSizeBytes = installedApks.sumOf { it.sizeBytes }
            ),
            CleanCategoryGroup(
                type = CleanCategoryType.LARGE_FILES,
                subtitlePt = "> 100 MB · ${largeFiles.size} ficheiro(s)",
                files = largeFiles,
                totalSizeBytes = largeFiles.sumOf { it.sizeBytes }
            ),
            CleanCategoryGroup(
                type = CleanCategoryType.OLD_DOWNLOADS,
                subtitlePt = "> 90 dias · ${oldDownloads.size} ficheiro(s)",
                files = oldDownloads,
                totalSizeBytes = oldDownloads.sumOf { it.sizeBytes }
            ),
            CleanCategoryGroup(
                type = CleanCategoryType.DUPLICATES,
                subtitlePt = "${duplicates.size} cópia(s) idêntica(s) (SHA-256)",
                files = duplicates,
                totalSizeBytes = duplicates.sumOf { it.sizeBytes }
            ),
            CleanCategoryGroup(
                type = CleanCategoryType.OLD_SCREENSHOTS,
                subtitlePt = "> 6 meses · ${oldScreenshots.size} captura(s)",
                files = oldScreenshots,
                totalSizeBytes = oldScreenshots.sumOf { it.sizeBytes }
            ),
            CleanCategoryGroup(
                type = CleanCategoryType.EMPTY_FOLDERS,
                subtitlePt = "${emptyFolders.size} pasta(s) vazia(s)",
                files = emptyList(),
                emptyFoldersPaths = emptyFolders,
                totalSizeBytes = 0L
            )
        )

        val uniqueBytes = groups
            .flatMap { it.files }
            .distinctBy { it.contentUri }
            .sumOf { it.sizeBytes }

        CleanScanReport(
            hasMediaPermission = mediaGranted,
            hasManageStoragePermission = manageGranted,
            categories = groups,
            totalCleanableBytes = uniqueBytes,
            scannedFilesCount = allCandidates.size
        )
    }

    private fun queryMediaStoreFiles(installedPackages: Set<String>): List<ScannedFileCandidate> {
        val results = mutableListOf<ScannedFileCandidate>()
        val filesUri = MediaStore.Files.getContentUri("external")
        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Files.FileColumns.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            projection.add(MediaStore.Files.FileColumns.DATA)
        }

        val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"
        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"

        try {
            context.contentResolver.query(
                filesUri,
                projection.toTypedArray(),
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val mediaTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Ficheiro_$id"
                    val size = cursor.getLong(sizeCol)
                    val dateMod = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: ""
                    val mediaType = cursor.getInt(mediaTypeCol)
                    val relPath = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""

                    val itemUri = when (mediaType) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ->
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        else -> ContentUris.withAppendedId(filesUri, id)
                    }

                    val archivePkg = if (name.endsWith(".apk", ignoreCase = true)) {
                        resolveApkPackageName(relPath, name) ?: installedPackages.firstOrNull {
                            name.contains(it, ignoreCase = true)
                        }
                    } else {
                        null
                    }

                    results.add(
                        ScannedFileCandidate(
                            id = id,
                            contentUri = itemUri.toString(),
                            displayName = name,
                            relativePath = relPath,
                            sizeBytes = size,
                            dateModifiedSeconds = dateMod,
                            mimeType = mime,
                            archivePackageName = archivePkg
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Sem permissão de leitura ainda concedida
        }

        return results
    }

    private fun resolveApkPackageName(relativePath: String, fileName: String): String? {
        return try {
            val root = Environment.getExternalStorageDirectory()
            val candidateFile = if (relativePath.startsWith("/")) {
                File(relativePath)
            } else {
                File(root, "$relativePath$fileName")
            }
            if (candidateFile.exists()) {
                val info = context.packageManager.getPackageArchiveInfo(candidateFile.absolutePath, 0)
                info?.packageName
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeContentUriSha256(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                var totalRead = 0L
                // Lê até 4 MB para hash rápido e determinístico em ficheiros grandes
                while (read > 0 && totalRead < 4L * 1024L * 1024L) {
                    digest.update(buffer, 0, read)
                    totalRead += read
                    read = input.read(buffer)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scanEmptyFolders(): List<String> {
        val emptyDirs = mutableListOf<String>()
        val publicDirs = listOf(
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_DOCUMENTS
        )
        for (dirType in publicDirs) {
            try {
                val root = Environment.getExternalStoragePublicDirectory(dirType)
                if (root != null && root.exists() && root.canRead()) {
                    root.walkTopDown().maxDepth(3).forEach { dir ->
                        if (dir != root && dir.isDirectory) {
                            val children = dir.listFiles()
                            if (children != null && children.isEmpty()) {
                                emptyDirs.add(dir.absolutePath)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignora pastas inacessíveis
            }
        }
        return emptyDirs.distinct()
    }

    fun buildTrashIntentSender(uris: List<Uri>, moveToTrash: Boolean): IntentSender? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            try {
                MediaStore.createTrashRequest(context.contentResolver, uris, moveToTrash).intentSender
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun deleteEmptyFoldersAndRecord(
        emptyFolders: List<String>,
        trashedFiles: List<ScannedFileCandidate>
    ) = withContext(Dispatchers.IO) {
        var deletedFolders = 0
        for (path in emptyFolders) {
            try {
                val f = File(path)
                if (f.exists() && f.isDirectory && f.listFiles()?.isEmpty() == true) {
                    if (f.delete()) deletedFolders++
                }
            } catch (_: Exception) {
            }
        }

        val freedBytes = trashedFiles.sumOf { it.sizeBytes }
        val urisString = trashedFiles.joinToString("|") { it.contentUri }

        if (trashedFiles.isNotEmpty() || deletedFolders > 0) {
            val title = if (freedBytes > 0) {
                "Limpeza · ${NexusFormatters.formatBytes(freedBytes)} para o Lixo"
            } else {
                "Limpeza · $deletedFolders pasta(s) vazia(s) removida(s)"
            }
            val details = buildString {
                if (trashedFiles.isNotEmpty()) {
                    append("${trashedFiles.size} ficheiro(s) movido(s) para o Lixo do sistema (restauráveis por 30 dias).")
                }
                if (deletedFolders > 0) {
                    if (isNotEmpty()) append(" ")
                    append("$deletedFolders pasta(s) vazia(s) eliminada(s).")
                }
            }
            dao.insertHistory(
                HistoryEntity(
                    actionType = "CLEAN",
                    title = title,
                    details = details,
                    timestampEpochMillis = System.currentTimeMillis(),
                    bytesFreed = freedBytes,
                    trashedUrisJson = urisString,
                    canRestore = trashedFiles.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
                    isRestored = false
                )
            )
        }
    }

    suspend fun markHistoryEntryRestored(historyId: Long) = withContext(Dispatchers.IO) {
        dao.markHistoryRestored(historyId)
    }
}
