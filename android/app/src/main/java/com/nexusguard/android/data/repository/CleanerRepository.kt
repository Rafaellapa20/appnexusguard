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
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.local.FileHashCacheEntity
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.domain.FileClassifier
import com.nexusguard.android.domain.MediaStoreSqlFilter
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ScannedFileCandidate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
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

@Immutable
data class CleanCategoryGroup(
    val type: CleanCategoryType,
    val subtitlePt: String,
    val files: ImmutableList<ScannedFileCandidate>,
    val emptyFoldersPaths: ImmutableList<String> = persistentListOf(),
    val totalSizeBytes: Long
)

@Immutable
data class CleanScanReport(
    val hasMediaPermission: Boolean,
    val hasManageStoragePermission: Boolean,
    val categories: ImmutableList<CleanCategoryGroup> = persistentListOf(),
    val totalCleanableBytes: Long,
    val scannedFilesCount: Int,
    val timestampMillis: Long = 0L,
    val freeStorageAtScanBytes: Long = 0L
)

@Immutable
data class ScanProgress(
    val isScanning: Boolean = false,
    val stepText: String = "",
    val progressFraction: Float = 0f,
    val report: CleanScanReport? = null
)

@Singleton
class CleanerRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: NexusGuardDao
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val hashDispatcher = Dispatchers.IO.limitedParallelism(4)

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

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

    fun observeScanProgressFlow(): Flow<ScanProgress> = flow {
        emit(ScanProgress(isScanning = true, stepText = "A iniciar análise de armazenamento...", progressFraction = 0.05f))
        val report = scanRealDeviceStorage { step ->
            _scanProgress.value = _scanProgress.value.copy(isScanning = true, stepText = step)
        }
        val done = ScanProgress(
            isScanning = false,
            stepText = "Análise concluída",
            progressFraction = 1f,
            report = report
        )
        _scanProgress.value = done
        emit(done)
    }.flowOn(Dispatchers.IO)

    suspend fun scanRealDeviceStorage(
        onStepProgress: (String) -> Unit = {}
    ): CleanScanReport = withContext(Dispatchers.IO) {
        val mediaGranted = hasMediaReadPermission()
        val manageGranted = hasManageExternalStoragePermission()
        val nowSeconds = System.currentTimeMillis() / 1000L
        val useRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val updateStep: (String, Float) -> Unit = { text, fraction ->
            onStepProgress(text)
            _scanProgress.value = ScanProgress(
                isScanning = true,
                stepText = text,
                progressFraction = fraction
            )
        }

        updateStep("A ler pacotes instalados...", 0.10f)
        val installedPackages: Set<String> = try {
            context.packageManager.getInstalledApplications(0).map { it.packageName }.toSet()
        } catch (_: Exception) {
            emptySet()
        }

        // 1. Ficheiros grandes (> 100 MB) via filtro SQL direto no MediaStore
        updateStep("A procurar ficheiros grandes (> 100 MB)...", 0.20f)
        val largeFiles = queryMediaStoreBySqlFilter(
            filter = FileClassifier.buildLargeFilesSqlFilter(),
            resolveApkPackage = false
        )

        // 2. Transferências antigas (> 90 dias) via filtro SQL direto
        updateStep("A procurar transferências antigas (> 90 dias)...", 0.35f)
        val oldDownloads = queryMediaStoreBySqlFilter(
            filter = FileClassifier.buildOldDownloadsSqlFilter(nowSeconds, useRelativePath),
            resolveApkPackage = false
        )

        // 3. Capturas de ecrã antigas (> 6 meses) via filtro SQL direto
        updateStep("A procurar capturas de ecrã antigas...", 0.50f)
        val oldScreenshots = queryMediaStoreBySqlFilter(
            filter = FileClassifier.buildOldScreenshotsSqlFilter(nowSeconds, useRelativePath),
            resolveApkPackage = false
        )

        // 4. APKs já instalados via filtro SQL direto
        updateStep("A verificar ficheiros APK instalados...", 0.65f)
        val apkCandidates = queryMediaStoreBySqlFilter(
            filter = FileClassifier.buildApkSqlFilter(),
            resolveApkPackage = true
        ) + scanDownloadsFolderApks(installedPackages)
        val installedApks = apkCandidates
            .distinctBy { it.contentUri }
            .filter { FileClassifier.isInstalledApk(it, installedPackages) }

        // 5. Duplicados (>= 1 MB): agrupamento por tamanho + hash parcial 64 KB + SHA-256 completo + cache Room
        updateStep("A analisar ficheiros duplicados (hash parcial 64 KB + SHA-256)...", 0.80f)
        val dupCandidates = queryMediaStoreBySqlFilter(
            filter = FileClassifier.buildDuplicatesCandidateSqlFilter(),
            resolveApkPackage = false
        )
        val duplicates = findDuplicatesWithRoomCacheAndParallelism(dupCandidates)

        // 6. Pastas vazias (walkTopDown.maxDepth(3), listFiles uma só vez)
        updateStep("A verificar pastas vazias...", 0.92f)
        val emptyFolders = scanEmptyFolders()

        val groups = persistentListOf(
            CleanCategoryGroup(
                type = CleanCategoryType.INSTALLED_APKS,
                subtitlePt = "Transferências · ${installedApks.size} ficheiro(s)",
                files = installedApks.toImmutableList(),
                totalSizeBytes = installedApks.sumOf { it.sizeBytes }
            ),
            CleanCategoryGroup(
                type = CleanCategoryType.LARGE_FILES,
                subtitlePt = "> 100 MB · ${largeFiles.size} ficheiro(s)",
                files = largeFiles.toImmutableList(),
                totalSizeBytes = largeFiles.sumOf { it.sizeBytes }
            ),
            CleanCategoryGroup(
                type = CleanCategoryType.OLD_DOWNLOADS,
                subtitlePt = "> 90 dias · ${oldDownloads.size} ficheiro(s)",
                files = oldDownloads.toImmutableList(),
                totalSizeBytes = oldDownloads.sumOf { it.sizeBytes }
            ),
            CleanCategoryGroup(
                type = CleanCategoryType.DUPLICATES,
                subtitlePt = "${duplicates.size} cópia(s) idêntica(s) (SHA-256)",
                files = duplicates.toImmutableList(),
                totalSizeBytes = duplicates.sumOf { it.sizeBytes }
            ),
            CleanCategoryGroup(
                type = CleanCategoryType.OLD_SCREENSHOTS,
                subtitlePt = "> 6 meses · ${oldScreenshots.size} captura(s)",
                files = oldScreenshots.toImmutableList(),
                totalSizeBytes = oldScreenshots.sumOf { it.sizeBytes }
            ),
            CleanCategoryGroup(
                type = CleanCategoryType.EMPTY_FOLDERS,
                subtitlePt = "${emptyFolders.size} pasta(s) vazia(s)",
                files = persistentListOf(),
                emptyFoldersPaths = emptyFolders.toImmutableList(),
                totalSizeBytes = 0L
            )
        )

        val allUniqueFiles = groups
            .flatMap { it.files }
            .distinctBy { it.contentUri }
        val uniqueBytes = allUniqueFiles.sumOf { it.sizeBytes }

        val report = CleanScanReport(
            hasMediaPermission = mediaGranted,
            hasManageStoragePermission = manageGranted,
            categories = groups,
            totalCleanableBytes = uniqueBytes,
            scannedFilesCount = maxOf(dupCandidates.size, allUniqueFiles.size),
            timestampMillis = System.currentTimeMillis()
        )

        _scanProgress.value = ScanProgress(
            isScanning = false,
            stepText = "Análise concluída",
            progressFraction = 1f,
            report = report
        )

        report
    }

    private suspend fun findDuplicatesWithRoomCacheAndParallelism(
        candidates: List<ScannedFileCandidate>
    ): List<ScannedFileCandidate> = coroutineScope {
        val sameSizeGroups = candidates
            .filter { it.sizeBytes >= FileClassifier.MIN_DUPLICATE_SIZE_BYTES }
            .groupBy { it.sizeBytes }
            .values
            .filter { it.size > 1 }

        if (sameSizeGroups.isEmpty()) return@coroutineScope emptyList()

        val allPotentialFiles = sameSizeGroups.flatten()

        // Carrega cache Room em blocos de 500 IDs
        val cacheMap = mutableMapOf<Long, FileHashCacheEntity>()
        allPotentialFiles.map { it.id }.chunked(500).forEach { idBatch ->
            dao.getCachedFileHashes(idBatch).forEach { entry ->
                cacheMap[entry.id] = entry
            }
        }

        val newCacheEntries = mutableListOf<FileHashCacheEntity>()
        val duplicateExtras = mutableListOf<ScannedFileCandidate>()

        // Passo 2: Calcula hash parcial (primeiros e últimos 64 KB + tamanho) com paralelismo 4
        val withPartialHashes = sameSizeGroups.map { group ->
            group.map { item ->
                async(hashDispatcher) {
                    val cached = cacheMap[item.id]
                    val validCache = cached != null &&
                        cached.dateModified == item.dateModifiedSeconds &&
                        cached.size == item.sizeBytes &&
                        cached.partialHash.isNotBlank()

                    val pHash = if (validCache) {
                        cached!!.partialHash
                    } else {
                        computeUriPartialSha256(Uri.parse(item.contentUri), item.sizeBytes)
                    }

                    if (pHash != null) {
                        val existingFull = if (validCache) cached?.fullHash else null
                        item.copy(partialHash = pHash, sha256Hash = existingFull)
                    } else {
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        // Passo 3: Só para quem colidir no hash parcial, calcula o SHA-256 completo em streaming com paralelismo 4
        for (group in withPartialHashes) {
            val byPartial = group.groupBy { it.partialHash }
            for ((pHash, collidedGroup) in byPartial) {
                if (pHash != null && collidedGroup.size > 1) {
                    val withFull = collidedGroup.map { item ->
                        async(hashDispatcher) {
                            val fHash = item.sha256Hash ?: computeUriFullSha256Streaming(Uri.parse(item.contentUri))
                            if (fHash != null) {
                                synchronized(newCacheEntries) {
                                    newCacheEntries.add(
                                        FileHashCacheEntity(
                                            id = item.id,
                                            dateModified = item.dateModifiedSeconds,
                                            size = item.sizeBytes,
                                            partialHash = pHash,
                                            fullHash = fHash
                                        )
                                    )
                                }
                                item.copy(sha256Hash = fHash)
                            } else {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()

                    val byFull = withFull.groupBy { it.sha256Hash }
                    for ((_, identicalItems) in byFull) {
                        if (identicalItems.size > 1) {
                            // Conserva sempre o original mais antigo
                            val sorted = identicalItems.sortedWith(
                                compareBy<ScannedFileCandidate> { it.dateModifiedSeconds }
                                    .thenBy { it.id }
                            )
                            duplicateExtras.addAll(sorted.drop(1))
                        }
                    }
                } else if (pHash != null && collidedGroup.size == 1) {
                    val single = collidedGroup.first()
                    if (cacheMap[single.id]?.partialHash != pHash) {
                        newCacheEntries.add(
                            FileHashCacheEntity(
                                id = single.id,
                                dateModified = single.dateModifiedSeconds,
                                size = single.sizeBytes,
                                partialHash = pHash,
                                fullHash = single.sha256Hash
                            )
                        )
                    }
                }
            }
        }

        if (newCacheEntries.isNotEmpty()) {
            try {
                dao.upsertFileHashCacheAll(newCacheEntries)
            } catch (_: Exception) {
            }
        }

        duplicateExtras
    }

    private fun queryMediaStoreBySqlFilter(
        filter: MediaStoreSqlFilter,
        resolveApkPackage: Boolean
    ): List<ScannedFileCandidate> {
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

        val sortOrder = "${MediaStore.Files.FileColumns.SIZE} DESC"

        try {
            context.contentResolver.query(
                filesUri,
                projection.toTypedArray(),
                filter.selection,
                filter.selectionArgs.toTypedArray().ifEmpty { null },
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

                var count = 0
                while (cursor.moveToNext() && count < 2500) {
                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: "Ficheiro_$id"
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol)
                    val mime = cursor.getString(mimeCol) ?: ""
                    val mediaType = cursor.getInt(mediaTypeCol)
                    val relPath = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""

                    val contentUri = when (mediaType) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                            ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                            ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ->
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        else -> ContentUris.withAppendedId(filesUri, id)
                    }

                    val apkPkg = if (resolveApkPackage && (displayName.endsWith(".apk", true) || mime.contains("package-archive"))) {
                        resolveApkPackageName(relPath, displayName)
                    } else {
                        null
                    }

                    results.add(
                        ScannedFileCandidate(
                            id = id,
                            contentUri = contentUri.toString(),
                            displayName = displayName,
                            relativePath = relPath,
                            sizeBytes = size,
                            dateModifiedSeconds = dateModified,
                            mimeType = mime,
                            archivePackageName = apkPkg
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    private fun scanDownloadsFolderApks(installedPackages: Set<String>): List<ScannedFileCandidate> {
        val found = mutableListOf<ScannedFileCandidate>()
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val children = downloadsDir?.listFiles() ?: return emptyList()
            for (file in children) {
                if (file.isFile && file.name.endsWith(".apk", ignoreCase = true)) {
                    val pkgInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                    val pkg = pkgInfo?.packageName
                    if (pkg != null && pkg in installedPackages) {
                        found.add(
                            ScannedFileCandidate(
                                id = file.absolutePath.hashCode().toLong(),
                                contentUri = Uri.fromFile(file).toString(),
                                displayName = file.name,
                                relativePath = "Download/",
                                sizeBytes = file.length(),
                                dateModifiedSeconds = file.lastModified() / 1000L,
                                mimeType = "application/vnd.android.package-archive",
                                archivePackageName = pkg
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return found
    }

    private fun resolveApkPackageName(relativePath: String, displayName: String): String? {
        return try {
            val extRoot = Environment.getExternalStorageDirectory()
            val candidateFile = if (relativePath.startsWith("/")) {
                File(relativePath)
            } else {
                File(File(extRoot, relativePath), displayName)
            }
            if (candidateFile.exists() && candidateFile.canRead()) {
                context.packageManager.getPackageArchiveInfo(candidateFile.absolutePath, 0)?.packageName
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Lê apenas os primeiros 64 KB e os últimos 64 KB via FileChannel (sem ler o meio do ficheiro).
     */
    private fun computeUriPartialSha256(uri: Uri, sizeBytes: Long): String? {
        return try {
            val chunkSize = FileClassifier.PARTIAL_HASH_CHUNK_BYTES
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    val fileSize = channel.size().coerceAtLeast(sizeBytes)
                    val firstLen = minOf(chunkSize.toLong(), fileSize).toInt()
                    val firstBuf = ByteBuffer.allocate(firstLen)
                    channel.read(firstBuf, 0L)

                    val lastLen = minOf(chunkSize.toLong(), fileSize).toInt()
                    val lastPos = (fileSize - lastLen).coerceAtLeast(0L)
                    val lastBuf = ByteBuffer.allocate(lastLen)
                    channel.read(lastBuf, lastPos)

                    FileClassifier.computePartialSha256(
                        firstChunk = firstBuf.array(),
                        lastChunk = lastBuf.array(),
                        sizeBytes = fileSize
                    )
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeUriFullSha256Streaming(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                FileClassifier.computeFullSha256Streaming(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fase 3: walkTopDown().maxDepth(3), mas sem chamar listFiles() duas vezes por pasta.
     */
    private fun scanEmptyFolders(): List<String> {
        val emptyPaths = mutableListOf<String>()
        try {
            val root = Environment.getExternalStorageDirectory()
            val targetDirs = listOf(
                File(root, "Download"),
                File(root, "DCIM"),
                File(root, "Pictures"),
                File(root, "Movies"),
                File(root, "Music"),
                File(root, "Documents")
            )
            for (baseDir in targetDirs) {
                if (!baseDir.exists() || !baseDir.isDirectory) continue
                baseDir.walkTopDown()
                    .maxDepth(3)
                    .onEnter { dir ->
                        if (dir == baseDir) return@onEnter true
                        val children = dir.listFiles() ?: return@onEnter false
                        if (children.isEmpty()) {
                            emptyPaths.add(dir.absolutePath)
                            false // Já sabemos que está vazia; não precisa de continuar nem voltar a chamar listFiles()
                        } else {
                            true
                        }
                    }
                    .forEach { _ -> }
            }
        } catch (_: Exception) {
        }
        return emptyPaths.take(40)
    }

    fun buildTrashIntentSender(uris: List<Uri>, moveToTrash: Boolean = true): IntentSender? {
        if (uris.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val mediaUris = uris.filter { it.scheme == "content" }
                if (mediaUris.isEmpty()) return null
                MediaStore.createTrashRequest(
                    context.contentResolver,
                    mediaUris,
                    moveToTrash
                ).intentSender
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
                if (f.exists() && f.isDirectory) {
                    val items = f.listFiles()
                    if (items != null && items.isEmpty() && f.delete()) {
                        deletedFolders++
                    }
                }
            } catch (_: Exception) {
            }
        }

        val freedBytes = trashedFiles.sumOf { it.sizeBytes }
        val urisJoined = trashedFiles.joinToString("|") { it.contentUri }

        if (trashedFiles.isNotEmpty() || deletedFolders > 0) {
            val title = buildString {
                append("Limpeza · ")
                if (freedBytes > 0L) {
                    append(NexusFormatters.formatBytes(freedBytes))
                } else {
                    append("$deletedFolders pasta(s) vazia(s)")
                }
            }
            val details = buildString {
                if (trashedFiles.isNotEmpty()) {
                    append("${trashedFiles.size} ficheiro(s) movido(s) para o Lixo do sistema")
                }
                if (deletedFolders > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("$deletedFolders pasta(s) vazia(s) removida(s)")
                }
            }
            dao.insertHistory(
                HistoryEntity(
                    actionType = "CLEAN",
                    title = title,
                    details = details,
                    timestampEpochMillis = System.currentTimeMillis(),
                    bytesFreed = freedBytes,
                    trashedUrisJson = urisJoined,
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
