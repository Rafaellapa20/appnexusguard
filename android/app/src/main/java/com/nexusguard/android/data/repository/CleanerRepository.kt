package com.nexusguard.android.data.repository

import android.Manifest
import android.app.AppOpsManager
import android.app.RecoverableSecurityException
import android.app.usage.StorageStatsManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.nexusguard.android.BuildConfig
import com.nexusguard.android.data.local.FileHashCacheEntity
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.data.local.NexusTrashEntity
import com.nexusguard.android.domain.AppCacheCandidate
import com.nexusguard.android.domain.FileClassifier
import com.nexusguard.android.domain.MediaStoreSqlFilter
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.ScannedFileCandidate
import com.nexusguard.android.domain.SimilarPhotoGroup
import com.nexusguard.android.worker.NexusTrashPurgeWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class CleanCategoryType(
    val titlePt: String,
    val emptySubtitlePt: String,
    val isSafePreselected: Boolean,
    val requiresFullFlavorManageStorage: Boolean = false
) {
    APP_CACHE("Cache das apps", "StorageStatsManager · 0 B", isSafePreselected = true),
    INSTALLED_APKS("APK instalados e obsoletos", "Transferências · 0 ficheiros", isSafePreselected = true),
    FILESYSTEM_JUNK("Lixo do sistema de ficheiros", ".thumbnails, .tmp, .log, 0 B · 0 ficheiros", isSafePreselected = true),
    DUPLICATES("Duplicados exatos", "Por tamanho e SHA-256 · 0 ficheiros", isSafePreselected = true),
    EMPTY_FOLDERS("Pastas vazias", "Deteção de baixo para cima · 0 pastas", isSafePreselected = true),
    UNINSTALLED_LEFTOVERS("Restos de apps desinstaladas", "Android/media e raiz · 0 ficheiros", isSafePreselected = true, requiresFullFlavorManageStorage = true),
    UNINSTALLED_OLD_APKS("APK não instalados (> 30 dias)", "Transferências · 0 ficheiros", isSafePreselected = false),
    SIMILAR_PHOTOS("Fotos semelhantes", "dHash 64-bit e nitidez · 0 grupos", isSafePreselected = false),
    BLURRY_PHOTOS("Fotos desfocadas", "Sugestão por nitidez (Laplaciano) · 0 fotos", isSafePreselected = false),
    OLD_SCREENSHOTS("Capturas de ecrã antigas", "> 6 meses · 0 capturas", isSafePreselected = false),
    OLD_DOWNLOADS("Transferências antigas", "> 90 dias · 0 ficheiros", isSafePreselected = false),
    LARGE_FILES("Ficheiros grandes", "> 100 MB · 0 ficheiros", isSafePreselected = false),
    MESSAGING_MEDIA("Mensagens (WhatsApp, Telegram, Signal)", "Enviados, voz > 90d, vídeos > 50 MB · 0 ficheiros", isSafePreselected = false)
}

enum class CategoryScanStatus {
    PENDING,
    SCANNING,
    DONE
}

@Immutable
data class CategoryScanProgress(
    val type: CleanCategoryType,
    val status: CategoryScanStatus = CategoryScanStatus.PENDING,
    val itemCount: Int = 0,
    val totalBytes: Long = 0L
)

@Immutable
data class CleanCategoryGroup(
    val type: CleanCategoryType,
    val subtitlePt: String,
    val files: ImmutableList<ScannedFileCandidate>,
    val emptyFoldersPaths: ImmutableList<String> = persistentListOf(),
    val similarGroups: ImmutableList<SimilarPhotoGroup> = persistentListOf(),
    val appCaches: ImmutableList<AppCacheCandidate> = persistentListOf(),
    val totalSizeBytes: Long
)

@Immutable
data class CleanScanReport(
    val hasMediaPermission: Boolean,
    val hasManageStoragePermission: Boolean,
    val hasUsageStatsPermission: Boolean = false,
    val categories: ImmutableList<CleanCategoryGroup> = persistentListOf(),
    val totalCleanableBytes: Long,
    val safePreselectedBytes: Long = 0L,
    val totalAppCacheBytes: Long = 0L,
    val topAppCaches: ImmutableList<AppCacheCandidate> = persistentListOf(),
    val scannedFilesCount: Int,
    val timestampMillis: Long = 0L,
    val freeStorageAtScanBytes: Long = 0L
)

@Immutable
data class DeepCleanCompletionResult(
    val historyEntryId: Long,
    val measuredFreedBytes: Long,
    val beforeUsedBytes: Long,
    val afterUsedBytes: Long,
    val totalStorageBytes: Long,
    val canRestore: Boolean,
    val restoreUntilEpochMillis: Long,
    val trashedUrisJson: String = "",
    val removedFilesCount: Int = 0,
    val removedEmptyFoldersCount: Int = 0
)

@Immutable
data class ScanProgress(
    val isScanning: Boolean = false,
    val stepText: String = "",
    val progressFraction: Float = 0f,
    val categoryProgress: ImmutableMap<CleanCategoryType, CategoryScanProgress> = persistentMapOf(),
    val report: CleanScanReport? = null
)

@Singleton
class CleanerRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: NexusGuardDao
) {
    companion object {
        private const val MEDIASTORE_PAGE_SIZE = 500
        private const val THIRTY_DAYS_MILLIS = 30L * 24L * 3600_000L
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val hashDispatcher = Dispatchers.IO.limitedParallelism(4)

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    @Volatile
    private var activeScanJob: Job? = null

    init {
        NexusTrashPurgeWorker.scheduleDailyPurge(context)
    }

    fun cancelCurrentScan() {
        activeScanJob?.cancel()
        _scanProgress.value = _scanProgress.value.copy(
            isScanning = false,
            stepText = "Análise cancelada"
        )
    }

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

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun observeScanProgressFlow(): Flow<ScanProgress> = flow {
        emit(ScanProgress(isScanning = true, stepText = "A iniciar análise profunda...", progressFraction = 0.05f))
        val report = scanRealDeviceStorage { step ->
            _scanProgress.value = _scanProgress.value.copy(isScanning = true, stepText = step)
        }
        val done = _scanProgress.value.copy(
            isScanning = false,
            stepText = "Análise concluída",
            progressFraction = 1f,
            report = report
        )
        _scanProgress.value = done
        emit(done)
    }.flowOn(Dispatchers.IO)

    /**
     * Fase 1: Executa todas as categorias independentes em paralelo com `async`,
     * emitindo o `ScanProgress` por categoria (estado, contagem, bytes) e permitindo cancelar.
     */
    suspend fun scanRealDeviceStorage(
        onStepProgress: (String) -> Unit = {}
    ): CleanScanReport = withContext(Dispatchers.IO) {
        activeScanJob = currentCoroutineContext()[Job]
        val mediaGranted = hasMediaReadPermission()
        val manageGranted = hasManageExternalStoragePermission()
        val usageGranted = hasUsageStatsPermission()
        val nowSeconds = System.currentTimeMillis() / 1000L
        val useRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        val progressMap = ConcurrentHashMap<CleanCategoryType, CategoryScanProgress>()
        val visibleTypes = CleanCategoryType.entries.filter {
            !it.requiresFullFlavorManageStorage || BuildConfig.IS_FULL_FLAVOR
        }
        visibleTypes.forEach { type ->
            progressMap[type] = CategoryScanProgress(type = type, status = CategoryScanStatus.PENDING)
        }

        fun publishProgress(stepText: String) {
            onStepProgress(stepText)
            val doneCount = progressMap.values.count { it.status == CategoryScanStatus.DONE }
            val totalCount = visibleTypes.size.coerceAtLeast(1)
            val fraction = (doneCount.toFloat() / totalCount.toFloat()).coerceIn(0.05f, 0.99f)
            _scanProgress.value = ScanProgress(
                isScanning = true,
                stepText = stepText,
                progressFraction = fraction,
                categoryProgress = progressMap.toMap().toImmutableMap()
            )
        }

        fun markCategoryStart(type: CleanCategoryType, stepLabel: String) {
            progressMap[type] = CategoryScanProgress(type = type, status = CategoryScanStatus.SCANNING)
            publishProgress(stepLabel)
        }

        fun markCategoryDone(type: CleanCategoryType, count: Int, bytes: Long) {
            progressMap[type] = CategoryScanProgress(
                type = type,
                status = CategoryScanStatus.DONE,
                itemCount = count,
                totalBytes = bytes
            )
            publishProgress("${type.titlePt} concluído")
        }

        publishProgress("A ler pacotes instalados e versões...")
        val installedVersions: Map<String, Long> = readInstalledPackagesVersions()
        val installedPackages: Set<String> = installedVersions.keys

        coroutineScope {
            // 1. Cache das apps (StorageStatsManager)
            val appCachesDeferred = async {
                markCategoryStart(CleanCategoryType.APP_CACHE, "A medir cache das apps (StorageStatsManager)...")
                val (totalCache, top10Caches) = queryAppCaches(usageGranted)
                markCategoryDone(CleanCategoryType.APP_CACHE, top10Caches.size, totalCache)
                CleanCategoryGroup(
                    type = CleanCategoryType.APP_CACHE,
                    subtitlePt = if (!usageGranted) {
                        "Requer acesso a utilização para ler cache por app"
                    } else {
                        "Top ${top10Caches.size} apps · Total ${NexusFormatters.formatBytes(totalCache)}"
                    },
                    files = persistentListOf(),
                    appCaches = top10Caches.toImmutableList(),
                    totalSizeBytes = totalCache
                ) to (totalCache to top10Caches)
            }

            // 2. Ficheiros grandes (> 100 MB) paginados
            val largeFilesDeferred = async {
                markCategoryStart(CleanCategoryType.LARGE_FILES, "A procurar ficheiros grandes (> 100 MB)...")
                val files = queryMediaStorePaginated(
                    filter = FileClassifier.buildLargeFilesSqlFilter(),
                    resolveApkInfo = false,
                    isPreselected = false
                )
                val bytes = files.sumOf { it.sizeBytes }
                markCategoryDone(CleanCategoryType.LARGE_FILES, files.size, bytes)
                CleanCategoryGroup(
                    type = CleanCategoryType.LARGE_FILES,
                    subtitlePt = "> 100 MB · ${files.size} ficheiro(s)",
                    files = files.toImmutableList(),
                    totalSizeBytes = bytes
                )
            }

            // 3. Transferências antigas (> 90 dias) paginadas
            val oldDownloadsDeferred = async {
                markCategoryStart(CleanCategoryType.OLD_DOWNLOADS, "A procurar transferências antigas (> 90 dias)...")
                val files = queryMediaStorePaginated(
                    filter = FileClassifier.buildOldDownloadsSqlFilter(nowSeconds, useRelativePath),
                    resolveApkInfo = false,
                    isPreselected = false
                )
                val bytes = files.sumOf { it.sizeBytes }
                markCategoryDone(CleanCategoryType.OLD_DOWNLOADS, files.size, bytes)
                CleanCategoryGroup(
                    type = CleanCategoryType.OLD_DOWNLOADS,
                    subtitlePt = "> 90 dias · ${files.size} ficheiro(s)",
                    files = files.toImmutableList(),
                    totalSizeBytes = bytes
                )
            }

            // 4. Capturas de ecrã antigas (> 6 meses) paginadas
            val oldScreenshotsDeferred = async {
                markCategoryStart(CleanCategoryType.OLD_SCREENSHOTS, "A procurar capturas de ecrã antigas...")
                val files = queryMediaStorePaginated(
                    filter = FileClassifier.buildOldScreenshotsSqlFilter(nowSeconds, useRelativePath),
                    resolveApkInfo = false,
                    isPreselected = false
                )
                val bytes = files.sumOf { it.sizeBytes }
                markCategoryDone(CleanCategoryType.OLD_SCREENSHOTS, files.size, bytes)
                CleanCategoryGroup(
                    type = CleanCategoryType.OLD_SCREENSHOTS,
                    subtitlePt = "> 6 meses · ${files.size} captura(s)",
                    files = files.toImmutableList(),
                    totalSizeBytes = bytes
                )
            }

            // 5. APK obsoletos (instalados / versionCode <= instalado) + APK não instalados (> 30 dias)
            val apksDeferred = async {
                markCategoryStart(CleanCategoryType.INSTALLED_APKS, "A verificar ficheiros APK em Transferências e subpastas...")
                markCategoryStart(CleanCategoryType.UNINSTALLED_OLD_APKS, "A verificar APK antigos não instalados...")
                val allApks = (
                    queryMediaStorePaginated(
                        filter = FileClassifier.buildApkSqlFilter(),
                        resolveApkInfo = true,
                        isPreselected = true
                    ) + scanDownloadsFolderApksRecursive(installedVersions)
                    ).distinctBy { it.contentUri }

                val obsoleteInstalled = allApks
                    .filter { FileClassifier.isObsoleteInstalledApk(it, installedVersions) }
                    .map { it.copy(isPreselectedByDefault = true) }

                val uninstalledOld = allApks
                    .filter { FileClassifier.isUninstalledOldApk(it, installedPackages, nowSeconds) }
                    .map { it.copy(isPreselectedByDefault = false) }

                val obsBytes = obsoleteInstalled.sumOf { it.sizeBytes }
                val uninstBytes = uninstalledOld.sumOf { it.sizeBytes }
                markCategoryDone(CleanCategoryType.INSTALLED_APKS, obsoleteInstalled.size, obsBytes)
                markCategoryDone(CleanCategoryType.UNINSTALLED_OLD_APKS, uninstalledOld.size, uninstBytes)

                val groupObsolete = CleanCategoryGroup(
                    type = CleanCategoryType.INSTALLED_APKS,
                    subtitlePt = "Instalados ou versão ≤ atual · ${obsoleteInstalled.size} APK",
                    files = obsoleteInstalled.toImmutableList(),
                    totalSizeBytes = obsBytes
                )
                val groupUninstalledOld = CleanCategoryGroup(
                    type = CleanCategoryType.UNINSTALLED_OLD_APKS,
                    subtitlePt = "Não instalados (> 30 dias) · ${uninstalledOld.size} APK",
                    files = uninstalledOld.toImmutableList(),
                    totalSizeBytes = uninstBytes
                )
                groupObsolete to groupUninstalledOld
            }

            // 6. Lixo do sistema de ficheiros (.thumbnails, *.tmp, *.log, *.bak, *.crdownload, *.part, 0 B, .cache)
            val fsJunkDeferred = async {
                markCategoryStart(CleanCategoryType.FILESYSTEM_JUNK, "A procurar lixo do sistema de ficheiros (.tmp, .log, 0 B)...")
                val files = scanFilesystemJunk(useRelativePath, manageGranted)
                val bytes = files.sumOf { it.sizeBytes }
                markCategoryDone(CleanCategoryType.FILESYSTEM_JUNK, files.size, bytes)
                CleanCategoryGroup(
                    type = CleanCategoryType.FILESYSTEM_JUNK,
                    subtitlePt = ".thumbnails, .tmp, .log, .bak, 0 B · ${files.size} ficheiro(s)",
                    files = files.toImmutableList(),
                    totalSizeBytes = bytes
                )
            }

            // 7. Restos de apps desinstaladas (só no full com MANAGE_EXTERNAL_STORAGE)
            val leftoversDeferred = async {
                if (!BuildConfig.IS_FULL_FLAVOR) return@async null
                markCategoryStart(CleanCategoryType.UNINSTALLED_LEFTOVERS, "A procurar restos de apps desinstaladas...")
                val files = if (manageGranted) {
                    scanUninstalledAppLeftovers(installedPackages)
                } else {
                    emptyList()
                }
                val bytes = files.sumOf { it.sizeBytes }
                markCategoryDone(CleanCategoryType.UNINSTALLED_LEFTOVERS, files.size, bytes)
                CleanCategoryGroup(
                    type = CleanCategoryType.UNINSTALLED_LEFTOVERS,
                    subtitlePt = if (!manageGranted) {
                        "Requer acesso avançado a ficheiros (MANAGE_EXTERNAL_STORAGE)"
                    } else {
                        "Android/media e raiz · ${files.size} ficheiro(s) órfão(s)"
                    },
                    files = files.toImmutableList(),
                    totalSizeBytes = bytes
                )
            }

            // 8. Mensagens (WhatsApp, Telegram, Signal)
            val messagingDeferred = async {
                markCategoryStart(CleanCategoryType.MESSAGING_MEDIA, "A analisar media de mensagens (WhatsApp, Telegram, Signal)...")
                val files = scanMessagingMedia(nowSeconds)
                val bytes = files.sumOf { it.sizeBytes }
                markCategoryDone(CleanCategoryType.MESSAGING_MEDIA, files.size, bytes)
                val appsSummary = files.mapNotNull { it.messagingAppName }.distinct().joinToString(", ")
                CleanCategoryGroup(
                    type = CleanCategoryType.MESSAGING_MEDIA,
                    subtitlePt = if (files.isEmpty()) {
                        "Enviados, voz > 90d, vídeos > 50 MB · 0 ficheiros"
                    } else {
                        "$appsSummary · ${files.size} ficheiro(s)"
                    },
                    files = files.toImmutableList(),
                    totalSizeBytes = bytes
                )
            }

            // 9. Duplicados exatos (paginados por _id sem corte de 2500 + hash parcial + SHA-256 + Room cache)
            val duplicatesDeferred = async {
                markCategoryStart(CleanCategoryType.DUPLICATES, "A analisar duplicados exatos (paginação completa + SHA-256)...")
                val dupCandidates = queryMediaStorePaginated(
                    filter = FileClassifier.buildDuplicatesCandidateSqlFilter(),
                    resolveApkInfo = false,
                    isPreselected = true
                )
                val duplicates = findDuplicatesWithRoomCacheAndParallelism(dupCandidates)
                val bytes = duplicates.sumOf { it.sizeBytes }
                markCategoryDone(CleanCategoryType.DUPLICATES, duplicates.size, bytes)
                CleanCategoryGroup(
                    type = CleanCategoryType.DUPLICATES,
                    subtitlePt = "${duplicates.size} cópia(s) idêntica(s) (SHA-256)",
                    files = duplicates.toImmutableList(),
                    totalSizeBytes = bytes
                ) to dupCandidates.size
            }

            // 10. Fotos semelhantes (dHash 64-bit + Laplaciano) e Fotos desfocadas
            val photosAnalysisDeferred = async {
                markCategoryStart(CleanCategoryType.SIMILAR_PHOTOS, "A analisar fotos semelhantes e nitidez (dHash + Laplaciano)...")
                markCategoryStart(CleanCategoryType.BLURRY_PHOTOS, "A avaliar fotos desfocadas...")
                val (similarGroups, blurryPhotos) = analyzeSimilarAndBlurryPhotos()
                val similarDuplicateFiles = similarGroups.flatMap { it.duplicatePhotos }
                val simBytes = similarDuplicateFiles.sumOf { it.sizeBytes }
                val blurBytes = blurryPhotos.sumOf { it.sizeBytes }
                markCategoryDone(CleanCategoryType.SIMILAR_PHOTOS, similarDuplicateFiles.size, simBytes)
                markCategoryDone(CleanCategoryType.BLURRY_PHOTOS, blurryPhotos.size, blurBytes)

                val simCategory = CleanCategoryGroup(
                    type = CleanCategoryType.SIMILAR_PHOTOS,
                    subtitlePt = "${similarGroups.size} grupo(s) · ${similarDuplicateFiles.size} sugestão(ões) mantendo a mais nítida",
                    files = similarDuplicateFiles.toImmutableList(),
                    similarGroups = similarGroups.toImmutableList(),
                    totalSizeBytes = simBytes
                )
                val blurCategory = CleanCategoryGroup(
                    type = CleanCategoryType.BLURRY_PHOTOS,
                    subtitlePt = "Sugestão por baixa nitidez · ${blurryPhotos.size} foto(s)",
                    files = blurryPhotos.toImmutableList(),
                    totalSizeBytes = blurBytes
                )
                simCategory to blurCategory
            }

            // 11. Pastas vazias (deteção de baixo para cima sem limite de 40, ignorando .nomedia)
            val emptyFoldersDeferred = async {
                markCategoryStart(CleanCategoryType.EMPTY_FOLDERS, "A detetar pastas vazias (baixo para cima)...")
                val emptyFolders = scanEmptyFoldersBottomUp()
                markCategoryDone(CleanCategoryType.EMPTY_FOLDERS, emptyFolders.size, 0L)
                CleanCategoryGroup(
                    type = CleanCategoryType.EMPTY_FOLDERS,
                    subtitlePt = "${emptyFolders.size} pasta(s) vazia(s)",
                    files = persistentListOf(),
                    emptyFoldersPaths = emptyFolders.toImmutableList(),
                    totalSizeBytes = 0L
                )
            }

            val (appCacheGroup, appCacheMeta) = appCachesDeferred.await()
            val (totalAppCacheBytes, top10Caches) = appCacheMeta
            val (obsoleteApksGroup, uninstalledOldApksGroup) = apksDeferred.await()
            val fsJunkGroup = fsJunkDeferred.await()
            val (duplicatesGroup, scannedDupCount) = duplicatesDeferred.await()
            val emptyFoldersGroup = emptyFoldersDeferred.await()
            val leftoversGroup = leftoversDeferred.await()
            val (similarPhotosGroup, blurryPhotosGroup) = photosAnalysisDeferred.await()
            val oldScreenshotsGroup = oldScreenshotsDeferred.await()
            val oldDownloadsGroup = oldDownloadsDeferred.await()
            val largeFilesGroup = largeFilesDeferred.await()
            val messagingGroup = messagingDeferred.await()

            val groups = buildList {
                add(appCacheGroup)
                add(obsoleteApksGroup)
                add(fsJunkGroup)
                add(duplicatesGroup)
                add(emptyFoldersGroup)
                if (leftoversGroup != null) add(leftoversGroup)
                add(similarPhotosGroup)
                add(blurryPhotosGroup)
                add(oldScreenshotsGroup)
                add(oldDownloadsGroup)
                add(largeFilesGroup)
                add(messagingGroup)
                add(uninstalledOldApksGroup)
            }.toImmutableList()

            val allUniqueFiles = groups
                .flatMap { it.files }
                .distinctBy { it.contentUri }
            val uniqueFilesBytes = allUniqueFiles.sumOf { it.sizeBytes }
            val safeFilesBytes = groups
                .filter { it.type.isSafePreselected }
                .flatMap { it.files }
                .distinctBy { it.contentUri }
                .sumOf { it.sizeBytes }

            val report = CleanScanReport(
                hasMediaPermission = mediaGranted,
                hasManageStoragePermission = manageGranted,
                hasUsageStatsPermission = usageGranted,
                categories = groups,
                totalCleanableBytes = uniqueFilesBytes + totalAppCacheBytes,
                safePreselectedBytes = safeFilesBytes + totalAppCacheBytes,
                totalAppCacheBytes = totalAppCacheBytes,
                topAppCaches = top10Caches.toImmutableList(),
                scannedFilesCount = maxOf(scannedDupCount, allUniqueFiles.size),
                timestampMillis = System.currentTimeMillis()
            )

            _scanProgress.value = ScanProgress(
                isScanning = false,
                stepText = "Análise concluída",
                progressFraction = 1f,
                categoryProgress = progressMap.toMap().toImmutableMap(),
                report = report
            )

            report
        }
    }

    /**
     * Fase 1 (1): Lê o cacheBytes real por app via StorageStatsManager.queryStatsForPackage
     * e devolve o total e o top 10.
     */
    suspend fun queryAppCaches(hasUsagePermission: Boolean = hasUsageStatsPermission()): Pair<Long, List<AppCacheCandidate>> =
        withContext(Dispatchers.IO) {
            if (!hasUsagePermission) return@withContext 0L to emptyList()
            val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                ?: return@withContext 0L to emptyList()
            val pm = context.packageManager
            val userHandle = Process.myUserHandle()

            val apps = try {
                pm.getInstalledApplications(0)
            } catch (_: Exception) {
                emptyList()
            }

            var totalCache = 0L
            val candidates = mutableListOf<AppCacheCandidate>()

            for (appInfo in apps) {
                currentCoroutineContext().ensureActive()
                val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (!isUserApp && pm.getLaunchIntentForPackage(appInfo.packageName) == null) continue

                try {
                    val stats = storageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        appInfo.packageName,
                        userHandle
                    )
                    val cBytes = stats.cacheBytes.coerceAtLeast(0L)
                    if (cBytes > 0L) {
                        totalCache += cBytes
                        val label = try {
                            pm.getApplicationLabel(appInfo).toString().ifBlank { appInfo.packageName }
                        } catch (_: Exception) {
                            appInfo.packageName
                        }
                        val words = label.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                        val monogram = when {
                            words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
                            label.length >= 2 -> label.take(2).uppercase()
                            else -> label.uppercase()
                        }
                        candidates.add(
                            AppCacheCandidate(
                                packageName = appInfo.packageName,
                                appName = label,
                                monogram = monogram,
                                cacheBytes = cBytes,
                                totalAppBytes = stats.appBytes + stats.dataBytes + cBytes
                            )
                        )
                    }
                } catch (_: Exception) {
                }
            }

            val top10 = candidates
                .sortedByDescending { it.cacheBytes }
                .take(10)

            totalCache to top10
        }

    /**
     * Fase 1 (1): Regista no Histórico o espaço libertado real após o diálogo do sistema
     * `StorageManager.ACTION_CLEAR_APP_CACHE`, comparando o `cacheBytes` antes e depois.
     */
    suspend fun recordRealClearedAppCacheDiff(beforeCacheBytes: Long): Long = withContext(Dispatchers.IO) {
        val (afterCacheBytes, _) = queryAppCaches(hasUsageStatsPermission())
        val freed = (beforeCacheBytes - afterCacheBytes).coerceAtLeast(0L)
        if (freed > 0L) {
            dao.insertHistory(
                HistoryEntity(
                    actionType = "CACHE_CLEAR",
                    title = "Cache das apps limpa · ${NexusFormatters.formatBytes(freed)}",
                    details = "Medido via StorageStatsManager antes (${NexusFormatters.formatBytes(beforeCacheBytes)}) e depois (${NexusFormatters.formatBytes(afterCacheBytes)})",
                    timestampEpochMillis = System.currentTimeMillis(),
                    bytesFreed = freed,
                    canRestore = false
                )
            )
        }
        freed
    }

    /**
     * Fase 1 (8): Paginação completa por `_id` com `LIMIT/OFFSET` (ou `QUERY_ARG_LIMIT`/`QUERY_ARG_OFFSET` no API 30+)
     * até ao fim, sem o limite fixo de 2500.
     */
    private suspend fun queryMediaStorePaginated(
        filter: MediaStoreSqlFilter,
        resolveApkInfo: Boolean,
        isPreselected: Boolean
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

        var lastId = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val pageSelection = if (filter.selection.isNotBlank()) {
                "(${filter.selection}) AND ${MediaStore.Files.FileColumns._ID} > ?"
            } else {
                "${MediaStore.Files.FileColumns._ID} > ?"
            }
            val pageArgs = (filter.selectionArgs + lastId.toString()).toTypedArray()

            var fetchedInPage = 0
            try {
                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val queryArgs = Bundle().apply {
                        putString(ContentResolver.QUERY_ARG_SQL_SELECTION, pageSelection)
                        putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, pageArgs)
                        putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Files.FileColumns._ID} ASC")
                        putInt(ContentResolver.QUERY_ARG_LIMIT, MEDIASTORE_PAGE_SIZE)
                    }
                    context.contentResolver.query(filesUri, projection.toTypedArray(), queryArgs, null)
                } else {
                    context.contentResolver.query(
                        filesUri,
                        projection.toTypedArray(),
                        pageSelection,
                        pageArgs,
                        "${MediaStore.Files.FileColumns._ID} ASC LIMIT $MEDIASTORE_PAGE_SIZE"
                    )
                }

                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    val mediaTypeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        c.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    } else {
                        @Suppress("DEPRECATION")
                        c.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                    }

                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        if (id > lastId) lastId = id
                        fetchedInPage++

                        val displayName = c.getString(nameCol) ?: "Ficheiro_$id"
                        val size = c.getLong(sizeCol)
                        val dateModified = c.getLong(dateCol)
                        val mime = c.getString(mimeCol) ?: ""
                        val mediaType = c.getInt(mediaTypeCol)
                        val relPath = if (pathCol >= 0) c.getString(pathCol) ?: "" else ""

                        val contentUri = when (mediaType) {
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE ->
                                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO ->
                                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO ->
                                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                            else -> ContentUris.withAppendedId(filesUri, id)
                        }

                        val (apkPkg, apkVer) = if (resolveApkInfo && (displayName.endsWith(".apk", true) || mime.contains("package-archive"))) {
                            resolveApkPackageAndVersion(relPath, displayName)
                        } else {
                            null to null
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
                                archivePackageName = apkPkg,
                                archiveVersionCode = apkVer,
                                isPreselectedByDefault = isPreselected
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                break
            }

            if (fetchedInPage < MEDIASTORE_PAGE_SIZE) {
                break
            }
        }

        return results.sortedByDescending { it.sizeBytes }
    }

    /**
     * Fase 1 (7 e 8): Desce também às subpastas de `Download/` e extrai packageName e versionCode.
     */
    private suspend fun scanDownloadsFolderApksRecursive(
        installedVersions: Map<String, Long>
    ): List<ScannedFileCandidate> {
        val found = mutableListOf<ScannedFileCandidate>()
        try {
            val root = Environment.getExternalStorageDirectory()
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: return emptyList()
            if (!downloadsDir.exists() || !downloadsDir.isDirectory) return emptyList()

            downloadsDir.walkTopDown().maxDepth(5).forEach { file ->
                currentCoroutineContext().ensureActive()
                if (file.isFile && file.name.endsWith(".apk", ignoreCase = true) && file.length() > 0L) {
                    val pkgInfo = try {
                        context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                    } catch (_: Exception) {
                        null
                    }
                    val pkg = pkgInfo?.packageName
                    val verCode = pkgInfo?.let { PackageInfoCompat.getLongVersionCode(it) }
                    val installedVer = pkg?.let { installedVersions[it] }
                    val relPath = file.parentFile?.absolutePath
                        ?.removePrefix(root.absolutePath)
                        ?.trimStart('/', '\\')
                        ?.let { "$it/" } ?: "Download/"

                    found.add(
                        ScannedFileCandidate(
                            id = file.absolutePath.hashCode().toLong(),
                            contentUri = Uri.fromFile(file).toString(),
                            displayName = file.name,
                            relativePath = relPath,
                            sizeBytes = file.length(),
                            dateModifiedSeconds = file.lastModified() / 1000L,
                            mimeType = "application/vnd.android.package-archive",
                            archivePackageName = pkg,
                            archiveVersionCode = verCode,
                            installedVersionCode = installedVer
                        )
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return found
    }

    /**
     * Fase 1 (2): Restos de apps desinstaladas (só no `full` com `MANAGE_EXTERNAL_STORAGE`):
     * pastas em `Android/media/<pkg>` e pastas na raiz com nome de pacote (`com.x.y`) que já não está instalado.
     */
    private suspend fun scanUninstalledAppLeftovers(
        installedPackages: Set<String>
    ): List<ScannedFileCandidate> {
        if (!BuildConfig.IS_FULL_FLAVOR || !hasManageExternalStoragePermission()) return emptyList()
        val found = mutableListOf<ScannedFileCandidate>()
        try {
            val root = Environment.getExternalStorageDirectory() ?: return emptyList()
            val androidMediaDir = File(root, "Android/media")

            val candidateDirsMap = mutableMapOf<String, String>()
            androidMediaDir.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    candidateDirsMap[dir.name] = dir.absolutePath
                }
            }
            root.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    candidateDirsMap[dir.name] = dir.absolutePath
                }
            }

            val orphanFolderPaths = FileClassifier.findUninstalledPackageFolders(
                folderNamesToPaths = candidateDirsMap,
                installedPackages = installedPackages,
                selfPackage = context.packageName
            )

            for (folderPath in orphanFolderPaths) {
                currentCoroutineContext().ensureActive()
                val dir = File(folderPath)
                dir.walkTopDown().maxDepth(6).forEach { file ->
                    if (file.isFile) {
                        val relPath = file.parentFile?.absolutePath
                            ?.removePrefix(root.absolutePath)
                            ?.trimStart('/', '\\')
                            ?.let { "$it/" } ?: ""
                        found.add(
                            ScannedFileCandidate(
                                id = file.absolutePath.hashCode().toLong(),
                                contentUri = Uri.fromFile(file).toString(),
                                displayName = file.name,
                                relativePath = relPath,
                                sizeBytes = file.length(),
                                dateModifiedSeconds = file.lastModified() / 1000L,
                                mimeType = "application/octet-stream",
                                archivePackageName = dir.name,
                                isPreselectedByDefault = true
                            )
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return found.sortedByDescending { it.sizeBytes }
    }

    /**
     * Fase 1 (3): Lixo do sistema de ficheiros:
     * DCIM/.thumbnails, *.tmp, *.log, *.bak, *.crdownload, *.part, ficheiros de 0 bytes,
     * e pastas .cache/cache em Download/ e Documents/.
     */
    private suspend fun scanFilesystemJunk(
        useRelativePath: Boolean,
        manageGranted: Boolean
    ): List<ScannedFileCandidate> {
        val mediaJunk = queryMediaStorePaginated(
            filter = FileClassifier.buildFilesystemJunkSqlFilter(),
            resolveApkInfo = false,
            isPreselected = true
        ).filter { FileClassifier.isFilesystemJunkFile(it.displayName, it.relativePath, it.sizeBytes) }

        val directFiles = mutableListOf<ScannedFileCandidate>()
        if (manageGranted) {
            try {
                val root = Environment.getExternalStorageDirectory()
                val targetDirs = listOf(
                    File(root, "DCIM/.thumbnails"),
                    File(root, "Pictures/.thumbnails"),
                    File(root, "Download"),
                    File(root, "Documents")
                )
                for (base in targetDirs) {
                    if (!base.exists() || !base.isDirectory) continue
                    base.walkTopDown().maxDepth(4).forEach { file ->
                        currentCoroutineContext().ensureActive()
                        if (file.isFile) {
                            val relPath = file.parentFile?.absolutePath
                                ?.removePrefix(root.absolutePath)
                                ?.trimStart('/', '\\')
                                ?.let { "$it/" } ?: ""
                            if (FileClassifier.isFilesystemJunkFile(file.name, relPath, file.length())) {
                                directFiles.add(
                                    ScannedFileCandidate(
                                        id = file.absolutePath.hashCode().toLong(),
                                        contentUri = Uri.fromFile(file).toString(),
                                        displayName = file.name,
                                        relativePath = relPath,
                                        sizeBytes = file.length(),
                                        dateModifiedSeconds = file.lastModified() / 1000L,
                                        mimeType = "application/octet-stream",
                                        isPreselectedByDefault = true
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }

        return (mediaJunk + directFiles)
            .distinctBy { "${it.relativePath}/${it.displayName}" }
            .sortedByDescending { it.sizeBytes }
    }

    /**
     * Fase 1 (4): Mensagens (WhatsApp, Telegram, Signal):
     * media enviada (/Sent), notas de voz > 90 dias, vídeos > 50 MB e .Statuses antigos.
     */
    private suspend fun scanMessagingMedia(nowSeconds: Long): List<ScannedFileCandidate> {
        val filter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStoreSqlFilter(
                selection = "relative_path LIKE '%WhatsApp%' OR relative_path LIKE '%Telegram%' OR relative_path LIKE '%Signal%'",
                selectionArgs = emptyList()
            )
        } else {
            MediaStoreSqlFilter(
                selection = "_data LIKE '%WhatsApp%' OR _data LIKE '%Telegram%' OR _data LIKE '%Signal%'",
                selectionArgs = emptyList()
            )
        }

        val rawCandidates = queryMediaStorePaginated(
            filter = filter,
            resolveApkInfo = false,
            isPreselected = false
        )

        return rawCandidates.mapNotNull { item ->
            val classification = FileClassifier.classifyMessagingMedia(
                displayName = item.displayName,
                relativePath = item.relativePath,
                sizeBytes = item.sizeBytes,
                dateModifiedSeconds = item.dateModifiedSeconds,
                nowSeconds = nowSeconds
            ) ?: return@mapNotNull null

            item.copy(
                messagingAppName = classification.first,
                messagingMediaType = classification.second,
                isPreselectedByDefault = false
            )
        }.sortedByDescending { it.sizeBytes }
    }

    /**
     * Fase 1 (5 e 6): Fotos semelhantes (dHash 64-bit sobre miniatura 64x64 + distância de Hamming <= 6 + < 10 min)
     * e Fotos desfocadas (variância do Laplaciano abaixo do limiar), guardando na cache Room.
     */
    private suspend fun analyzeSimilarAndBlurryPhotos(): Pair<List<SimilarPhotoGroup>, List<ScannedFileCandidate>> =
        coroutineScope {
            if (!hasMediaReadPermission()) return@coroutineScope emptyList<SimilarPhotoGroup>() to emptyList()

            val imageCandidates = queryMediaStorePaginated(
                filter = FileClassifier.buildImagesSqlFilter(),
                resolveApkInfo = false,
                isPreselected = false
            ).filter {
                !it.relativePath.contains("Screenshots", ignoreCase = true) &&
                    !it.displayName.startsWith("Screenshot", ignoreCase = true)
            }.take(800) // Analisa até 800 fotos recentes/principais com cache Room persistente

            if (imageCandidates.isEmpty()) return@coroutineScope emptyList<SimilarPhotoGroup>() to emptyList()

            val cacheMap = mutableMapOf<Long, FileHashCacheEntity>()
            imageCandidates.map { it.id }.chunked(500).forEach { ids ->
                dao.getCachedFileHashes(ids).forEach { entry ->
                    cacheMap[entry.id] = entry
                }
            }

            val updatedCacheEntries = mutableListOf<FileHashCacheEntity>()

            val analyzedPhotos = imageCandidates.map { photo ->
                async(hashDispatcher) {
                    currentCoroutineContext().ensureActive()
                    val cached = cacheMap[photo.id]
                    val hasValidVisualCache = cached != null &&
                        cached.dateModified == photo.dateModifiedSeconds &&
                        cached.size == photo.sizeBytes &&
                        cached.dHash != null &&
                        cached.laplacianSharpness != null

                    if (hasValidVisualCache) {
                        photo.copy(
                            dHash64 = cached!!.dHash,
                            laplacianSharpness = cached.laplacianSharpness,
                            isPreselectedByDefault = false
                        )
                    } else {
                        val metrics = computePhotoDHashAndSharpness(Uri.parse(photo.contentUri))
                        if (metrics != null) {
                            val (dHash, sharpness) = metrics
                            synchronized(updatedCacheEntries) {
                                updatedCacheEntries.add(
                                    FileHashCacheEntity(
                                        id = photo.id,
                                        dateModified = photo.dateModifiedSeconds,
                                        size = photo.sizeBytes,
                                        partialHash = cached?.partialHash ?: "",
                                        fullHash = cached?.fullHash,
                                        dHash = dHash,
                                        laplacianSharpness = sharpness
                                    )
                                )
                            }
                            photo.copy(
                                dHash64 = dHash,
                                laplacianSharpness = sharpness,
                                isPreselectedByDefault = false
                            )
                        } else {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()

            if (updatedCacheEntries.isNotEmpty()) {
                try {
                    dao.upsertFileHashCacheAll(updatedCacheEntries)
                } catch (_: Exception) {
                }
            }

            val similarGroups = FileClassifier.groupSimilarPhotos(analyzedPhotos)
            val blurryPhotos = analyzedPhotos
                .filter { photo ->
                    val sharpness = photo.laplacianSharpness ?: return@filter false
                    FileClassifier.isBlurryPhoto(sharpness)
                }
                .sortedBy { it.laplacianSharpness ?: Double.MAX_VALUE }

            similarGroups to blurryPhotos
        }

    private fun computePhotoDHashAndSharpness(uri: Uri): Pair<Long, Double>? {
        return try {
            val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(64, 64), null)
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                    BitmapFactory.decodeStream(stream, null, opts)?.let { decoded ->
                        Bitmap.createScaledBitmap(decoded, 64, 64, true)
                    }
                }
            }
            if (bitmap == null) return null
            val scaled = if (bitmap.width != 64 || bitmap.height != 64) {
                Bitmap.createScaledBitmap(bitmap, 64, 64, true)
            } else {
                bitmap
            }
            val pixels = IntArray(64 * 64)
            scaled.getPixels(pixels, 0, 64, 0, 0, 64, 64)
            val dHash = FileClassifier.computeDHash64FromArgb(pixels, 64, 64)
            val sharpness = FileClassifier.computeLaplacianVarianceFromArgb(pixels, 64, 64)
            dHash to sharpness
        } catch (_: Exception) {
            null
        }
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

        val cacheMap = mutableMapOf<Long, FileHashCacheEntity>()
        allPotentialFiles.map { it.id }.chunked(500).forEach { idBatch ->
            dao.getCachedFileHashes(idBatch).forEach { entry ->
                cacheMap[entry.id] = entry
            }
        }

        val newCacheEntries = mutableListOf<FileHashCacheEntity>()
        val duplicateExtras = mutableListOf<ScannedFileCandidate>()

        val withPartialHashes = sameSizeGroups.map { group ->
            group.map { item ->
                async(hashDispatcher) {
                    currentCoroutineContext().ensureActive()
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

        for (group in withPartialHashes) {
            val byPartial = group.groupBy { it.partialHash }
            for ((pHash, collidedGroup) in byPartial) {
                if (pHash != null && collidedGroup.size > 1) {
                    val withFull = collidedGroup.map { item ->
                        async(hashDispatcher) {
                            currentCoroutineContext().ensureActive()
                            val cached = cacheMap[item.id]
                            val fHash = item.sha256Hash ?: computeUriFullSha256Streaming(Uri.parse(item.contentUri))
                            if (fHash != null) {
                                synchronized(newCacheEntries) {
                                    newCacheEntries.add(
                                        FileHashCacheEntity(
                                            id = item.id,
                                            dateModified = item.dateModifiedSeconds,
                                            size = item.sizeBytes,
                                            partialHash = pHash,
                                            fullHash = fHash,
                                            dHash = cached?.dHash,
                                            laplacianSharpness = cached?.laplacianSharpness
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
                            val sorted = identicalItems.sortedWith(
                                compareBy<ScannedFileCandidate> { it.dateModifiedSeconds }
                                    .thenBy { it.id }
                            )
                            duplicateExtras.addAll(sorted.drop(1).map { it.copy(isPreselectedByDefault = true) })
                        }
                    }
                } else if (pHash != null && collidedGroup.size == 1) {
                    val single = collidedGroup.first()
                    val cached = cacheMap[single.id]
                    if (cached?.partialHash != pHash) {
                        newCacheEntries.add(
                            FileHashCacheEntity(
                                id = single.id,
                                dateModified = single.dateModifiedSeconds,
                                size = single.sizeBytes,
                                partialHash = pHash,
                                fullHash = single.sha256Hash,
                                dHash = cached?.dHash,
                                laplacianSharpness = cached?.laplacianSharpness
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

    private fun readInstalledPackagesVersions(): Map<String, Long> {
        val pm = context.packageManager
        return try {
            pm.getInstalledPackages(0).associate { pkgInfo ->
                pkgInfo.packageName to PackageInfoCompat.getLongVersionCode(pkgInfo)
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun resolveApkPackageAndVersion(relativePath: String, displayName: String): Pair<String?, Long?> {
        return try {
            val extRoot = Environment.getExternalStorageDirectory()
            val candidateFile = if (relativePath.startsWith("/")) {
                File(relativePath)
            } else {
                File(File(extRoot, relativePath), displayName)
            }
            if (candidateFile.exists() && candidateFile.canRead()) {
                val info = context.packageManager.getPackageArchiveInfo(candidateFile.absolutePath, 0)
                info?.packageName to info?.let { PackageInfoCompat.getLongVersionCode(it) }
            } else {
                null to null
            }
        } catch (_: Exception) {
            null to null
        }
    }

    private fun computeUriPartialSha256(uri: Uri, sizeBytes: Long): String? {
        return try {
            val chunkSize = FileClassifier.PARTIAL_HASH_CHUNK_BYTES
            if (uri.scheme == "file") {
                val f = File(uri.path ?: return null)
                FileInputStream(f).channel.use { channel ->
                    val fileSize = channel.size().coerceAtLeast(sizeBytes)
                    val firstLen = minOf(chunkSize.toLong(), fileSize).toInt()
                    val firstBuf = ByteBuffer.allocate(firstLen)
                    channel.read(firstBuf, 0L)

                    val lastLen = minOf(chunkSize.toLong(), fileSize).toInt()
                    val lastPos = (fileSize - lastLen).coerceAtLeast(0L)
                    val lastBuf = ByteBuffer.allocate(lastLen)
                    channel.read(lastBuf, lastPos)

                    return FileClassifier.computePartialSha256(firstBuf.array(), lastBuf.array(), fileSize)
                }
            }
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
            if (uri.scheme == "file") {
                val f = File(uri.path ?: return null)
                FileInputStream(f).use { stream ->
                    return FileClassifier.computeFullSha256Streaming(stream)
                }
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                FileClassifier.computeFullSha256Streaming(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Fase 1 (8): Deteção de pastas vazias de baixo para cima (`findEmptyFoldersBottomUp`),
     * sem o `take(40)`, ignorando pastas que contêm `.nomedia`.
     */
    private suspend fun scanEmptyFoldersBottomUp(): List<String> {
        val emptyPaths = mutableListOf<String>()
        try {
            val root = Environment.getExternalStorageDirectory() ?: return emptyList()
            val targetDirs = listOf(
                File(root, "Download"),
                File(root, "DCIM"),
                File(root, "Pictures"),
                File(root, "Movies"),
                File(root, "Music"),
                File(root, "Documents")
            )
            for (baseDir in targetDirs) {
                currentCoroutineContext().ensureActive()
                if (!baseDir.exists() || !baseDir.isDirectory) continue
                emptyPaths.addAll(FileClassifier.findEmptyFoldersBottomUp(baseDir))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return emptyPaths
    }

    /**
     * Fase 1 (8):
     * - API 30+: `MediaStore.createTrashRequest(contentResolver, mediaUris, moveToTrash)`
     * - Abaixo da API 30: quando `moveToTrash == true`, tenta apagar ou obtém o `IntentSender` de
     *   `RecoverableSecurityException` (não reversível, `canRestore = false`).
     */
    fun buildTrashIntentSender(uris: List<Uri>, moveToTrash: Boolean = true): IntentSender? {
        val mediaUris = uris.filter { it.scheme == "content" }
        if (mediaUris.isEmpty()) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                MediaStore.createTrashRequest(
                    context.contentResolver,
                    mediaUris,
                    moveToTrash
                ).intentSender
            } catch (_: Exception) {
                if (moveToTrash) {
                    try {
                        MediaStore.createDeleteRequest(context.contentResolver, mediaUris).intentSender
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
            }
        } else if (moveToTrash && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29: Verifica se exige RecoverableSecurityException
            for (uri in mediaUris) {
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (rse: RecoverableSecurityException) {
                    return rse.userAction.actionIntent.intentSender
                } catch (_: Exception) {
                }
            }
            null
        } else {
            null
        }
    }

    /**
     * Fase 1 (8): Move ficheiros `file://` (no `full`) para o **Lixo Nexus** próprio (`Android/media/<pkg>/.trash`),
     * guarda metadados em Room (`NexusTrashEntity`) restauráveis durante 30 dias,
     * remove pastas vazias de baixo para cima e regista no Histórico.
     */
    suspend fun deleteEmptyFoldersAndRecord(
        emptyFolders: List<String>,
        trashedFiles: List<ScannedFileCandidate>,
        beforeUsedStorageBytes: Long = 0L
    ): DeepCleanCompletionResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val statBefore = android.os.StatFs(Environment.getDataDirectory().path)
        val totalStorage = statBefore.totalBytes
        val effectiveBeforeUsed = if (beforeUsedStorageBytes > 0L) {
            beforeUsedStorageBytes
        } else {
            (totalStorage - statBefore.availableBytes).coerceAtLeast(0L)
        }

        var deletedFolders = 0

        // Ordena por comprimento decrescente para apagar subpastas primeiro (baixo para cima)
        for (path in emptyFolders.sortedByDescending { it.length }) {
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

        // Abaixo da API 30, apaga os content:// restantes que não precisaram de IntentSender
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            for (candidate in trashedFiles.filter { it.contentUri.startsWith("content://") }) {
                try {
                    context.contentResolver.delete(Uri.parse(candidate.contentUri), null, null)
                } catch (_: Exception) {
                }
            }
        }

        // Processa URIs file:// movendo-os para o Lixo Nexus (Android/media/<pkg>/.trash)
        val fileSchemeCandidates = trashedFiles.filter { it.contentUri.startsWith("file://") }
        val movedToNexusTrash = mutableListOf<Pair<ScannedFileCandidate, File>>()
        val trashDir = resolveNexusTrashDirectory()

        for (candidate in fileSchemeCandidates) {
            try {
                val srcPath = Uri.parse(candidate.contentUri).path ?: continue
                val srcFile = File(srcPath)
                if (!srcFile.exists()) continue
                val destFile = File(trashDir, "${now}_${srcFile.name}")
                val moved = srcFile.renameTo(destFile) || run {
                    srcFile.copyTo(destFile, overwrite = true)
                    srcFile.delete()
                }
                if (moved) {
                    movedToNexusTrash.add(candidate to destFile)
                }
            } catch (_: Exception) {
            }
        }

        val contentSchemeCandidates = trashedFiles.filter { it.contentUri.startsWith("content://") }
        val effectiveTrashedFiles = contentSchemeCandidates + movedToNexusTrash.map { it.first }
        val candidateSumBytes = effectiveTrashedFiles.sumOf { it.sizeBytes }

        // Mede novamente o armazenamento real após a operação
        val statAfter = android.os.StatFs(Environment.getDataDirectory().path)
        val rawAfterUsed = (totalStorage - statAfter.availableBytes).coerceAtLeast(0L)
        val measuredDelta = (effectiveBeforeUsed - rawAfterUsed).coerceAtLeast(0L)
        val freedBytes = if (measuredDelta > 0L) measuredDelta else candidateSumBytes
        val effectiveAfterUsed = (effectiveBeforeUsed - freedBytes).coerceAtLeast(0L)

        val urisJoined = effectiveTrashedFiles.joinToString("|") { it.contentUri }

        val canRestoreMediaStore = contentSchemeCandidates.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val canRestoreNexusTrash = movedToNexusTrash.isNotEmpty()
        val canRestoreAny = canRestoreMediaStore || canRestoreNexusTrash
        val restoreUntilMillis = now + THIRTY_DAYS_MILLIS
        var insertedHistoryId = 0L

        if (effectiveTrashedFiles.isNotEmpty() || deletedFolders > 0) {
            val title = buildString {
                append("Limpeza · ")
                if (freedBytes > 0L) {
                    append(NexusFormatters.formatBytes(freedBytes))
                } else {
                    append("$deletedFolders pasta(s) vazia(s)")
                }
            }
            val details = buildString {
                if (effectiveTrashedFiles.isNotEmpty()) {
                    if (canRestoreAny) {
                        append("${effectiveTrashedFiles.size} ficheiro(s) movido(s) para o Lixo (restaurável 30 dias)")
                    } else {
                        append("${effectiveTrashedFiles.size} ficheiro(s) eliminado(s) permanentemente (API < 30)")
                    }
                }
                if (deletedFolders > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("$deletedFolders pasta(s) vazia(s) removida(s)")
                }
            }
            insertedHistoryId = dao.insertHistory(
                HistoryEntity(
                    actionType = "CLEAN",
                    title = title,
                    details = details,
                    timestampEpochMillis = now,
                    bytesFreed = freedBytes,
                    trashedUrisJson = urisJoined,
                    canRestore = canRestoreAny,
                    isRestored = false
                )
            )

            if (movedToNexusTrash.isNotEmpty()) {
                val trashEntities = movedToNexusTrash.map { (cand, destFile) ->
                    NexusTrashEntity(
                        historyId = insertedHistoryId,
                        originalPath = Uri.parse(cand.contentUri).path ?: "",
                        trashFilePath = destFile.absolutePath,
                        displayName = cand.displayName,
                        sizeBytes = cand.sizeBytes,
                        trashedAtEpochMillis = now,
                        expiresAtEpochMillis = restoreUntilMillis,
                        isRestored = false
                    )
                }
                dao.insertNexusTrashItems(trashEntities)
            }
        }

        DeepCleanCompletionResult(
            historyEntryId = insertedHistoryId,
            measuredFreedBytes = freedBytes,
            beforeUsedBytes = effectiveBeforeUsed,
            afterUsedBytes = effectiveAfterUsed,
            totalStorageBytes = totalStorage,
            canRestore = canRestoreAny,
            restoreUntilEpochMillis = restoreUntilMillis,
            trashedUrisJson = urisJoined,
            removedFilesCount = effectiveTrashedFiles.size,
            removedEmptyFoldersCount = deletedFolders
        )
    }

    /**
     * Restaura ficheiros do **Lixo Nexus** (`Android/media/<pkg>/.trash`) associados a um `historyId`
     * e marca a entrada do Histórico como restaurada.
     */
    suspend fun restoreNexusTrashAndMarkHistory(historyId: Long): Int = withContext(Dispatchers.IO) {
        val trashItems = dao.getNexusTrashItemsByHistoryId(historyId)
        var restoredCount = 0
        for (item in trashItems) {
            try {
                val trashFile = File(item.trashFilePath)
                val origFile = File(item.originalPath)
                if (trashFile.exists()) {
                    origFile.parentFile?.mkdirs()
                    val ok = trashFile.renameTo(origFile) || run {
                        trashFile.copyTo(origFile, overwrite = true)
                        trashFile.delete()
                    }
                    if (ok) restoredCount++
                }
            } catch (_: Exception) {
            }
        }
        dao.markNexusTrashItemsRestored(historyId)
        dao.markHistoryRestored(historyId)
        restoredCount
    }

    suspend fun markHistoryEntryRestored(historyId: Long) = withContext(Dispatchers.IO) {
        restoreNexusTrashAndMarkHistory(historyId)
    }

    private fun resolveNexusTrashDirectory(): File {
        @Suppress("DEPRECATION")
        val mediaDir = context.externalMediaDirs.firstOrNull()
            ?: File(Environment.getExternalStorageDirectory(), "Android/media/${context.packageName}")
        val trashDir = File(mediaDir, ".trash")
        if (!trashDir.exists()) {
            trashDir.mkdirs()
        }
        return trashDir
    }
}
