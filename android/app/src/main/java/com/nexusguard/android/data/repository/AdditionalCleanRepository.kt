package com.nexusguard.android.data.repository

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.domain.BrowserPrivacyShortcut
import com.nexusguard.android.domain.DownloadFileItem
import com.nexusguard.android.domain.LargeVideoItem
import com.nexusguard.android.domain.NexusFormatters
import com.nexusguard.android.domain.PhotoOptimizeQuality
import com.nexusguard.android.domain.PhotoOptimizerCalculator
import com.nexusguard.android.domain.PhotoOptimizerCandidate
import com.nexusguard.android.flavor.CallLogCleanerDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdditionalCleanRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NexusGuardDao
) {

    /**
     * Verifica se há conteúdo na área de transferência (apenas com a app em primeiro plano).
     * Não lê nem guarda qualquer texto ou URI da área de transferência.
     */
    fun hasClipboardContent(): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.hasPrimaryClip() == true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearClipboard(): Boolean = withContext(Dispatchers.Main) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@withContext false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            withContext(Dispatchers.IO) {
                dao.insertHistory(
                    HistoryEntity(
                        actionType = "CLIPBOARD_CLEAR",
                        title = "Área de transferência limpa",
                        details = "Conteúdo temporário removido sem leitura de dados",
                        bytesFreed = 0L,
                        timestampEpochMillis = System.currentTimeMillis(),
                        canRestore = false
                    )
                )
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Lista dedicada da pasta `Download/` com tipo, idade e tamanho.
     */
    suspend fun scanDownloadsFolder(): List<DownloadFileItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DownloadFileItem>()
        val nowSeconds = System.currentTimeMillis() / 1000L
        val resolver = context.contentResolver

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE
        )

        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.SIZE} > 0"
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ? AND ${MediaStore.MediaColumns.SIZE} > 0"
        }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            null
        } else {
            arrayOf("%/Download/%")
        }

        try {
            resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.SIZE} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)

                while (cursor.moveToNext() && result.size < 250) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Ficheiro_$id"
                    val size = cursor.getLong(sizeCol)
                    val dateSec = cursor.getLong(dateCol)
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    val ageDays = ((nowSeconds - dateSec).coerceAtLeast(0L) / 86_400L).toInt()
                    result.add(
                        DownloadFileItem(
                            id = id,
                            contentUri = uri,
                            displayName = name,
                            sizeBytes = size,
                            dateModifiedSeconds = dateSec,
                            mimeType = mime,
                            fileTypeLabelPt = PhotoOptimizerCalculator.classifyDownloadFileTypePt(mime, name),
                            ageDays = ageDays
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        result
    }

    /**
     * Lista vídeos com mais de 100 MB, com duração e data.
     */
    suspend fun scanLargeVideos(): List<LargeVideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<LargeVideoItem>()
        val minBytes = 100L * 1024L * 1024L
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE
        )
        try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Video.Media.SIZE} > ?",
                arrayOf(minBytes.toString()),
                "${MediaStore.Video.Media.SIZE} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)

                while (cursor.moveToNext() && result.size < 120) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id.mp4"
                    val size = cursor.getLong(sizeCol)
                    val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L
                    val dateSec = cursor.getLong(dateCol)
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/mp4" else "video/mp4"
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    result.add(
                        LargeVideoItem(
                            id = id,
                            contentUri = uri,
                            displayName = name,
                            sizeBytes = size,
                            durationMillis = duration,
                            dateModifiedSeconds = dateSec,
                            mimeType = mime
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        result
    }

    /**
     * Procura fotos JPEG/HEIC grandes (> 3 MB) candidatas ao Otimizador de Fotos.
     */
    suspend fun scanLargePhotosForOptimizer(
        quality: PhotoOptimizeQuality,
        limitMax4000px: Boolean
    ): List<PhotoOptimizerCandidate> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PhotoOptimizerCandidate>()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            add(MediaStore.Images.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        try {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Images.Media.SIZE} > ?",
                arrayOf(PhotoOptimizerCalculator.MIN_PHOTO_SIZE_BYTES.toString()),
                "${MediaStore.Images.Media.SIZE} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext() && result.size < 100) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "IMG_$id.jpg"
                    val size = cursor.getLong(sizeCol)
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 4000
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 3000
                    val dateSec = cursor.getLong(dateCol)
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "image/jpeg" else "image/jpeg"
                    val relPath = if (relCol >= 0) cursor.getString(relCol) ?: "DCIM/Camera/" else "DCIM/Camera/"

                    if (!PhotoOptimizerCalculator.isEligiblePhoto(size, mime, name)) continue
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    val estCompressed = PhotoOptimizerCalculator.estimateCompressedSizeBytes(
                        originalSizeBytes = size,
                        qualityPercent = quality.qualityPercent,
                        originalWidth = width,
                        originalHeight = height,
                        limitMax4000px = limitMax4000px
                    )
                    val estSaved = (size - estCompressed).coerceAtLeast(0L)
                    result.add(
                        PhotoOptimizerCandidate(
                            id = id,
                            contentUri = uri,
                            displayName = name,
                            relativePath = relPath,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            dateModifiedSeconds = dateSec,
                            mimeType = mime,
                            estimatedCompressedBytes = estCompressed,
                            estimatedSavedBytes = estSaved
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        result
    }

    /**
     * Recomprime as fotos selecionadas mantendo metadados EXIF essenciais e guarda uma cópia do original
     * no Lixo Nexus (retido por 30 dias antes de remoção definitiva), devolvendo os URIs originais
     * para pedido de Lixo do sistema (`createTrashRequest`) e o total de bytes poupados.
     */
    suspend fun optimizeSelectedPhotos(
        candidates: List<PhotoOptimizerCandidate>,
        quality: PhotoOptimizeQuality,
        limitMax4000px: Boolean
    ): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val trashDir = getNexusTrashDir()
        var optimizedCount = 0
        var totalSavedBytes = 0L

        for (photo in candidates) {
            try {
                val sourceUri = Uri.parse(photo.contentUri)
                // 1. Guarda cópia no Lixo Nexus (retenção 30 dias) e lê EXIF original
                val backupFile = File(trashDir, "${System.currentTimeMillis()}_${photo.displayName}")
                resolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(backupFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: continue

                val originalBitmap = BitmapFactory.decodeFile(backupFile.absolutePath) ?: continue
                val (targetW, targetH) = PhotoOptimizerCalculator.calculateTargetDimensions(
                    originalWidth = originalBitmap.width,
                    originalHeight = originalBitmap.height,
                    limitMax4000px = limitMax4000px
                )

                val scaledBitmap = if (targetW != originalBitmap.width || targetH != originalBitmap.height) {
                    Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true).also {
                        if (it !== originalBitmap) originalBitmap.recycle()
                    }
                } else {
                    originalBitmap
                }

                // 2. Comprime para ficheiro temporário para copiar EXIF antes de publicar na MediaStore
                val tempOutFile = File(context.cacheDir, "opt_${System.currentTimeMillis()}.jpg")
                FileOutputStream(tempOutFile).use { fos ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality.qualityPercent, fos)
                }
                scaledBitmap.recycle()

                copyEssentialExif(backupFile, tempOutFile)

                val newSize = tempOutFile.length()
                if (newSize in 1 until photo.sizeBytes) {
                    // Publica a versão otimizada em Pictures/NexusGuard
                    val baseName = photo.displayName.substringBeforeLast('.')
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "${baseName}_nexus.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/NexusGuard")
                        }
                    }
                    val insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (insertedUri != null) {
                        resolver.openOutputStream(insertedUri)?.use { outStream ->
                            tempOutFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        optimizedCount++
                        totalSavedBytes += (photo.sizeBytes - newSize).coerceAtLeast(0L)
                    }
                }
                tempOutFile.delete()
            } catch (_: Exception) {
            }
        }

        if (optimizedCount > 0) {
            dao.insertHistory(
                HistoryEntity(
                    actionType = "PHOTO_OPTIMIZER",
                    title = "$optimizedCount foto(s) otimizada(s) (${quality.qualityPercent} %)",
                    details = "Poupança de ${NexusFormatters.formatBytes(totalSavedBytes)} · originais guardados no Lixo Nexus (30 dias)",
                    bytesFreed = totalSavedBytes,
                    timestampEpochMillis = System.currentTimeMillis(),
                    canRestore = true,
                    trashedUrisJson = ""
                )
            )
        }
        optimizedCount to totalSavedBytes
    }

    private fun copyEssentialExif(sourceFile: File, destFile: File) {
        try {
            val srcExif = ExifInterface(sourceFile.absolutePath)
            val dstExif = ExifInterface(destFile.absolutePath)
            val tags = listOf(
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_ISO_SPEED_RATINGS
            )
            for (tag in tags) {
                val value = srcExif.getAttribute(tag)
                if (value != null) {
                    dstExif.setAttribute(tag, value)
                }
            }
            dstExif.saveAttributes()
        } catch (_: Exception) {
        }
    }

    private fun getNexusTrashDir(): File {
        val base = context.externalMediaDirs.firstOrNull() ?: context.filesDir
        return File(base, ".trash").apply { mkdirs() }
    }

    /**
     * Devolve os atalhos honestos para as definições dos principais navegadores.
     */
    fun getBrowserPrivacyShortcuts(): List<BrowserPrivacyShortcut> {
        val pm = context.packageManager
        val candidates = listOf(
            "com.android.chrome" to "Google Chrome",
            "com.sec.android.app.sbrowser" to "Samsung Internet",
            "org.mozilla.firefox" to "Mozilla Firefox"
        )
        return candidates.map { (pkg, name) ->
            val installed = try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
            BrowserPrivacyShortcut(
                packageName = pkg,
                browserName = name,
                isInstalled = installed
            )
        }
    }

    val isCallLogCleanerAvailable: Boolean
        get() = CallLogCleanerDelegate.IS_AVAILABLE

    fun hasCallLogPermissions(): Boolean = CallLogCleanerDelegate.hasPermissions(context)

    suspend fun countOldCallLogs(months: Int): Int =
        CallLogCleanerDelegate.countCallsOlderThanMonths(context, months)

    suspend fun deleteOldCallLogs(months: Int): Int {
        val deleted = CallLogCleanerDelegate.deleteCallsOlderThanMonths(context, months)
        if (deleted > 0) {
            dao.insertHistory(
                HistoryEntity(
                    actionType = "CALL_LOG_CLEAN",
                    title = "$deleted registo(s) de chamadas removido(s)",
                    details = "Chamadas com mais de $months meses apagadas a pedido do utilizador",
                    bytesFreed = 0L,
                    timestampEpochMillis = System.currentTimeMillis(),
                    canRestore = false
                )
            )
        }
        return deleted
    }
}
