package com.nexusguard.android.domain

import android.os.PowerManager
import com.nexusguard.android.data.repository.CleanScanReport
import com.nexusguard.android.data.repository.LiveBatteryMetrics
import com.nexusguard.android.data.repository.LiveStorageMetrics
import com.nexusguard.android.data.repository.LiveThermalMetrics
import com.nexusguard.android.data.repository.RealSystemSnapshot
import com.nexusguard.android.ui.ScreenUiStateMappers
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class DomainCalculatorsTest {

    @Test
    fun `health score is 100 when storage thermal battery and ram are optimal`() {
        val score = HealthScoreCalculator.calculateScore(
            SystemHealthInputs(
                freeStorageBytes = 60L * 1024 * 1024 * 1024,
                totalStorageBytes = 128L * 1024 * 1024 * 1024,
                thermalLevel = ThermalLevelPt.NORMAL,
                batteryTempCelsius = 30.5f,
                isBatteryHealthy = true,
                unusedAppsOver30Days = 0,
                availableMemoryBytes = 4L * 1024 * 1024 * 1024,
                totalMemoryBytes = 8L * 1024 * 1024 * 1024
            )
        )
        assertEquals(100, score)
        assertEquals("Saúde boa", HealthScoreCalculator.statusLabelPt(score))
    }

    @Test
    fun `health score penalizes low storage high thermal and degraded battery`() {
        val score = HealthScoreCalculator.calculateScore(
            SystemHealthInputs(
                freeStorageBytes = 4L * 1024 * 1024 * 1024, // < 5% (-30)
                totalStorageBytes = 128L * 1024 * 1024 * 1024,
                thermalLevel = ThermalLevelPt.GRAVE, // -22
                batteryTempCelsius = 43.0f, // -10
                isBatteryHealthy = false, // -15
                unusedAppsOver30Days = 10, // -6
                availableMemoryBytes = 1L * 1024 * 1024 * 1024, // < 18% (-6)
                totalMemoryBytes = 8L * 1024 * 1024 * 1024
            )
        )
        assertEquals(11, score)
        assertEquals("Ação necessária", HealthScoreCalculator.statusLabelPt(score))
    }

    @Test
    fun `thermal status mapper maps PowerManager constants to Portuguese labels`() {
        assertEquals("Normal", ThermalStatusMapper.fromPowerManagerStatus(PowerManager.THERMAL_STATUS_NONE).labelPt)
        assertEquals("Ligeiro", ThermalStatusMapper.fromPowerManagerStatus(PowerManager.THERMAL_STATUS_LIGHT).labelPt)
        assertEquals("Moderado", ThermalStatusMapper.fromPowerManagerStatus(PowerManager.THERMAL_STATUS_MODERATE).labelPt)
        assertEquals("Grave", ThermalStatusMapper.fromPowerManagerStatus(PowerManager.THERMAL_STATUS_SEVERE).labelPt)
        assertEquals("Crítico", ThermalStatusMapper.fromPowerManagerStatus(PowerManager.THERMAL_STATUS_CRITICAL).labelPt)
    }

    @Test
    fun `file classifier identifies installed APKs old downloads and SHA-256 duplicates`() {
        val nowSec = 1_800_000_000L
        val hundredDaysAgo = nowSec - (100L * 24L * 3600L)

        val apkCandidate = ScannedFileCandidate(
            id = 1L,
            contentUri = "content://media/external/file/1",
            displayName = "app-release.apk",
            relativePath = "Download/",
            sizeBytes = 25_000_000L,
            dateModifiedSeconds = hundredDaysAgo,
            mimeType = "application/vnd.android.package-archive",
            archivePackageName = "com.example.installed"
        )

        assertTrue(FileClassifier.isInstalledApk(apkCandidate, setOf("com.example.installed")))
        assertFalse(FileClassifier.isInstalledApk(apkCandidate, setOf("com.other.app")))
        assertTrue(FileClassifier.isOldDownload(hundredDaysAgo, nowSec, "Download/"))
        assertTrue(FileClassifier.isLargeFile(150L * 1024 * 1024))
        assertFalse(FileClassifier.isLargeFile(50L * 1024 * 1024))

        val dup1 = apkCandidate.copy(id = 10L, contentUri = "u1", sizeBytes = 50_000L, dateModifiedSeconds = 100L)
        val dup2 = apkCandidate.copy(id = 11L, contentUri = "u2", sizeBytes = 50_000L, dateModifiedSeconds = 200L)
        val different = apkCandidate.copy(id = 12L, contentUri = "u3", sizeBytes = 50_000L, dateModifiedSeconds = 300L)

        val extras = FileClassifier.findDuplicateExtras(listOf(dup1, dup2, different)) { item ->
            if (item.id == 12L) "hash_diff" else "hash_same"
        }
        assertEquals(1, extras.size)
        assertEquals(11L, extras.first().id)
    }

    @Test
    fun `partial hash and streaming full hash work and two-stage duplicate detection skips non-colliding files`() {
        val bytesA = ByteArray(200 * 1024) { idx -> (idx % 251).toByte() }
        val bytesACopy = bytesA.clone()
        val bytesB = ByteArray(200 * 1024) { idx -> ((idx + 7) % 251).toByte() }

        val partialA = FileClassifier.computePartialSha256FromByteArray(bytesA)
        val partialACopy = FileClassifier.computePartialSha256FromByteArray(bytesACopy)
        val partialB = FileClassifier.computePartialSha256FromByteArray(bytesB)

        assertEquals(partialA, partialACopy)
        assertNotEquals(partialA, partialB)

        val fullA = FileClassifier.computeFullSha256Streaming(ByteArrayInputStream(bytesA))
        val fullACopy = FileClassifier.computeFullSha256Streaming(ByteArrayInputStream(bytesACopy))
        assertEquals(fullA, fullACopy)
        assertEquals(FileClassifier.sha256Hex(bytesA), fullA)

        // Testa 2 fases: ficheiros < 1 MB ignorados, e fullHash só chamado quando partialHash colide
        val base = ScannedFileCandidate(
            id = 1L,
            contentUri = "c1",
            displayName = "video.mp4",
            relativePath = "Movies/",
            sizeBytes = 2L * 1024 * 1024, // 2 MB
            dateModifiedSeconds = 1000L,
            mimeType = "video/mp4"
        )
        val smallIgnored1 = base.copy(id = 2L, sizeBytes = 500_000L)
        val smallIgnored2 = base.copy(id = 3L, sizeBytes = 500_000L)
        val dupOriginal = base.copy(id = 10L, dateModifiedSeconds = 100L)
        val dupCopy = base.copy(id = 11L, dateModifiedSeconds = 200L)
        val sameSizeDiffPartial = base.copy(id = 12L, dateModifiedSeconds = 300L)

        var fullHashCallCount = 0
        val extras = FileClassifier.findDuplicateExtrasTwoStage(
            candidates = listOf(smallIgnored1, smallIgnored2, dupOriginal, dupCopy, sameSizeDiffPartial),
            partialHashProvider = { item ->
                if (item.id == 12L) "partial_unique" else "partial_collision"
            },
            fullHashProvider = { _ ->
                fullHashCallCount++
                "full_identical_hash"
            }
        )

        // Apenas dupOriginal (10L) e dupCopy (11L) colidem no partialHash -> fullHash chamado exatamente 2 vezes!
        assertEquals(2, fullHashCallCount)
        assertEquals(1, extras.size)
        // Conserva o mais antigo (10L) e devolve a cópia mais recente (11L)
        assertEquals(11L, extras.first().id)
    }

    @Test
    fun `SQL filters of FileClassifier generate expected MediaStore clauses`() {
        val nowSec = 1_800_000_000L
        val largeFilter = FileClassifier.buildLargeFilesSqlFilter()
        assertEquals("_size > ?", largeFilter.selection)
        assertEquals(listOf((100L * 1024 * 1024).toString()), largeFilter.selectionArgs)

        val dlFilter = FileClassifier.buildOldDownloadsSqlFilter(nowSec, useRelativePath = true)
        assertTrue(dlFilter.selection.contains("relative_path LIKE 'Download/%'"))
        assertTrue(dlFilter.selection.contains("date_modified < ?"))
        assertEquals(listOf((nowSec - 90L * 24 * 3600).toString()), dlFilter.selectionArgs)

        val shotFilter = FileClassifier.buildOldScreenshotsSqlFilter(nowSec, useRelativePath = true)
        assertTrue(shotFilter.selection.contains("relative_path LIKE '%Screenshots%'"))
        assertEquals(listOf((nowSec - 180L * 24 * 3600).toString()), shotFilter.selectionArgs)

        val apkFilter = FileClassifier.buildApkSqlFilter()
        assertTrue(apkFilter.selection.contains("application/vnd.android.package-archive"))
        assertTrue(apkFilter.selection.contains("_display_name LIKE '%.apk'"))

        val dupFilter = FileClassifier.buildDuplicatesCandidateSqlFilter()
        assertEquals("_size >= ?", dupFilter.selection)
        assertEquals(listOf((1024L * 1024L).toString()), dupFilter.selectionArgs)
    }

    @Test
    fun `distinctUntilChanged prevents CleanUiState from emitting when only battery or thermal changes`() = runTest {
        val storageSame = LiveStorageMetrics(
            freeBytes = 40_000_000_000L,
            totalBytes = 128_000_000_000L,
            usedBytes = 88_000_000_000L,
            appsAndSystemBytes = 50_000_000_000L,
            photosBytes = 20_000_000_000L,
            videosBytes = 15_000_000_000L,
            otherMediaBytes = 3_000_000_000L,
            externalStatsAvailable = true
        )

        val snap1 = RealSystemSnapshot(
            isLoading = false,
            healthScore = 92,
            storage = storageSame,
            battery = LiveBatteryMetrics(80, 31.0f, "Boa", true, 4100, false, "120", "Medido", "OK", ""),
            thermal = LiveThermalMetrics(ThermalLevelPt.NORMAL, 0.45f, "margem 0,45")
        )
        // Muda bateria e temperatura, mas o armazenamento mantém-se igual
        val snap2 = snap1.copy(
            battery = snap1.battery.copy(levelPercent = 79, temperatureCelsius = 33.5f),
            thermal = LiveThermalMetrics(ThermalLevelPt.LIGEIRO, 0.70f, "margem 0,70"),
            tempHistory60s = persistentListOf(31.0f, 33.5f)
        )

        val cleanStates = ScreenUiStateMappers.observeCleanUiState(
            snapshotFlow = flowOf(snap1, snap2),
            cleanReportFlow = flowOf(
                CleanScanReport(
                    hasMediaPermission = true,
                    hasManageStoragePermission = false,
                    categories = persistentListOf(),
                    totalCleanableBytes = 0L,
                    scannedFilesCount = 0
                )
            ),
            isScanningFlow = flowOf(false),
            scanStepFlow = flowOf("")
        ).toList()

        // Deve emitir apenas 1 vez porque storage, cleanReport e scanning não mudaram!
        assertEquals(1, cleanStates.size)
    }
}
