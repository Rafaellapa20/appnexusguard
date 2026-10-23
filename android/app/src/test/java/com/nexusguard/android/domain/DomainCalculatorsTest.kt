package com.nexusguard.android.domain

import android.os.PowerManager
import com.nexusguard.android.data.local.BatterySampleEntity
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

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

    @Test
    fun `5000 file gallery benchmark confirms two-stage duplicate scan is more than 2x faster`() {
        val gallery5000 = (1..5000).map { idx ->
            val size = when {
                idx <= 4400 -> 350_000L + (idx % 500) // < 1 MB (filtrado por SQL / ignorado)
                idx <= 4940 -> 2_000_000L + ((idx / 2) * 4096L) // Pares do mesmo tamanho mas conteúdo diferente
                else -> 4_500_000L // 60 ficheiros duplicados reais
            }
            ScannedFileCandidate(
                id = idx.toLong(),
                contentUri = "content://media/external/file/$idx",
                displayName = "IMG_$idx.jpg",
                relativePath = "DCIM/Camera/",
                sizeBytes = size,
                dateModifiedSeconds = 1_700_000_000L + idx,
                mimeType = "image/jpeg"
            )
        }

        val samplePayload2Mb = ByteArray(512 * 1024) { (it % 251).toByte() }
        val samplePayload128Kb = ByteArray(128 * 1024) { (it % 251).toByte() }

        val startLegacyNs = System.nanoTime()
        val legacyDuplicates = FileClassifier.findDuplicateExtras(
            candidates = gallery5000.filter { it.sizeBytes >= 32 * 1024L },
            hashProvider = { item ->
                val digest = FileClassifier.sha256Hex(samplePayload2Mb)
                if (item.id <= 4940L) "${digest}_${item.id}" else digest
            }
        )
        val legacyMs = (System.nanoTime() - startLegacyNs) / 1_000_000.0

        val startTwoStageNs = System.nanoTime()
        val twoStageDuplicates = FileClassifier.findDuplicateExtrasTwoStage(
            candidates = gallery5000,
            partialHashProvider = { item ->
                val p = FileClassifier.sha256Hex(samplePayload128Kb)
                if (item.id <= 4940L) "${p}_${item.id}" else p
            },
            fullHashProvider = { _ ->
                FileClassifier.sha256Hex(samplePayload2Mb)
            }
        )
        val twoStageMs = (System.nanoTime() - startTwoStageNs) / 1_000_000.0

        assertEquals(legacyDuplicates.size, twoStageDuplicates.size)
        assertEquals(59, twoStageDuplicates.size)
        assertTrue(
            "Esperado twoStageMs ($twoStageMs ms) < metade de legacyMs ($legacyMs ms)",
            twoStageMs < legacyMs * 0.5
        )
    }

    @Test
    fun `64-bit dHash and Hamming distance group similar photos and suggest keeping sharpest`() {
        val gridA = IntArray(72) { idx -> if ((idx % 9) < 4) 220 else 40 }
        val gridAlmostSame = gridA.clone().apply { this[0] = 30; this[1] = 230 } // 1 bit flip
        val gridVeryDifferent = IntArray(72) { idx -> if ((idx % 9) < 4) 10 else 245 }

        val hashA = FileClassifier.computeDHash64FromGrayscale9x8(gridA)
        val hashB = FileClassifier.computeDHash64FromGrayscale9x8(gridAlmostSame)
        val hashC = FileClassifier.computeDHash64FromGrayscale9x8(gridVeryDifferent)

        val distAB = FileClassifier.hammingDistance64(hashA, hashB)
        val distAC = FileClassifier.hammingDistance64(hashA, hashC)
        assertTrue("Distância AB deve ser <= 6 (foi $distAB)", distAB <= 6)
        assertTrue("Distância AC deve ser > 6 (foi $distAC)", distAC > 6)

        val basePhoto = ScannedFileCandidate(
            id = 100L,
            contentUri = "content://media/external/images/100",
            displayName = "IMG_100.jpg",
            relativePath = "DCIM/Camera/",
            sizeBytes = 3_000_000L,
            dateModifiedSeconds = 1_700_000_000L,
            mimeType = "image/jpeg",
            dHash64 = hashA,
            laplacianSharpness = 110.0
        )
        val sharperTwin = basePhoto.copy(
            id = 101L,
            contentUri = "content://media/external/images/101",
            displayName = "IMG_101.jpg",
            dateModifiedSeconds = 1_700_000_120L, // +2 min (< 10 min)
            dHash64 = hashB,
            laplacianSharpness = 260.0 // mais nítida!
        )
        val distantTime = basePhoto.copy(
            id = 102L,
            contentUri = "content://media/external/images/102",
            displayName = "IMG_102.jpg",
            dateModifiedSeconds = 1_700_003_600L, // +60 min (> 10 min)
            dHash64 = hashB,
            laplacianSharpness = 300.0
        )

        val groups = FileClassifier.groupSimilarPhotos(listOf(basePhoto, sharperTwin, distantTime))
        assertEquals(1, groups.size)
        assertEquals(101L, groups.first().keepPhoto.id)
        assertEquals(listOf(100L), groups.first().duplicatePhotos.map { it.id })
    }

    @Test
    fun `Laplacian variance distinguishes sharp checkerboard from blurry flat image`() {
        val flatGray = IntArray(16 * 16) { 128 }
        val sharpCheckerboard = IntArray(16 * 16) { idx ->
            val r = idx / 16
            val c = idx % 16
            if ((r + c) % 2 == 0) 240 else 20
        }

        val blurryVar = FileClassifier.computeLaplacianVariance(flatGray, 16, 16)
        val sharpVar = FileClassifier.computeLaplacianVariance(sharpCheckerboard, 16, 16)

        assertEquals(0.0, blurryVar, 0.001)
        assertTrue(FileClassifier.isBlurryPhoto(blurryVar))
        assertTrue("Esperada variância alta para imagem nítida ($sharpVar)", sharpVar > 1000.0)
        assertFalse(FileClassifier.isBlurryPhoto(sharpVar))
    }

    @Test
    fun `obsolete APK rules distinguish installed versionCode vs uninstalled over 30 days`() {
        val nowSec = 1_800_000_000L
        val fortyDaysAgo = nowSec - 40L * 24L * 3600L
        val fiveDaysAgo = nowSec - 5L * 24L * 3600L

        val installedVersions = mapOf("com.app.installed" to 10L)
        val installedSet = installedVersions.keys

        val apkOlderOrSameInstalled = ScannedFileCandidate(
            id = 1L,
            contentUri = "u1",
            displayName = "app-v10.apk",
            relativePath = "Download/sub/",
            sizeBytes = 15_000_000L,
            dateModifiedSeconds = fiveDaysAgo,
            mimeType = "application/vnd.android.package-archive",
            archivePackageName = "com.app.installed",
            archiveVersionCode = 10L
        )
        val apkNewerUpdate = apkOlderOrSameInstalled.copy(id = 2L, archiveVersionCode = 12L)
        val apkUninstalledOld = apkOlderOrSameInstalled.copy(
            id = 3L,
            archivePackageName = "com.app.uninstalled",
            archiveVersionCode = 1L,
            dateModifiedSeconds = fortyDaysAgo
        )
        val apkUninstalledRecent = apkUninstalledOld.copy(id = 4L, dateModifiedSeconds = fiveDaysAgo)

        assertTrue(FileClassifier.isObsoleteInstalledApk(apkOlderOrSameInstalled, installedVersions))
        assertFalse(FileClassifier.isObsoleteInstalledApk(apkNewerUpdate, installedVersions))
        assertTrue(FileClassifier.isUninstalledOldApk(apkUninstalledOld, installedSet, nowSec))
        assertFalse(FileClassifier.isUninstalledOldApk(apkUninstalledRecent, installedSet, nowSec))
    }

    @Test
    fun `bottom-up empty folder detection includes parent of empty folders and skips nomedia`() {
        val tempRoot = Files.createTempDirectory("nexus_empty_test").toFile()
        try {
            val parentOnlyEmpty = tempRoot.resolve("parent_of_empty")
            val childEmpty = parentOnlyEmpty.resolve("child_empty")
            childEmpty.mkdirs()

            val nomediaDir = tempRoot.resolve("protected_by_nomedia")
            nomediaDir.mkdirs()
            nomediaDir.resolve(".nomedia").createNewFile()

            val nonEmptyDir = tempRoot.resolve("has_real_file")
            nonEmptyDir.mkdirs()
            nonEmptyDir.resolve("doc.txt").writeText("nexus")

            val detected = FileClassifier.findEmptyFoldersBottomUp(tempRoot)
            assertTrue(detected.contains(childEmpty.absolutePath))
            assertTrue(detected.contains(parentOnlyEmpty.absolutePath))
            assertFalse(detected.contains(nomediaDir.absolutePath))
            assertFalse(detected.contains(nonEmptyDir.absolutePath))
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `uninstalled package leftovers detects com_x_y folders not in installed packages`() {
        val folders = mapOf(
            "com.old.removedgame" to "/storage/emulated/0/Android/media/com.old.removedgame",
            "com.installed.keep" to "/storage/emulated/0/Android/media/com.installed.keep",
            "com.nexusguard.android" to "/storage/emulated/0/Android/media/com.nexusguard.android",
            "DCIM" to "/storage/emulated/0/DCIM"
        )
        val installed = setOf("com.installed.keep")
        val leftovers = FileClassifier.findUninstalledPackageFolders(folders, installed)
        assertEquals(listOf("/storage/emulated/0/Android/media/com.old.removedgame"), leftovers)
    }

    @Test
    fun `battery rate calculator requires 2h of samples and calculates screen-on and idle drain per hour`() {
        val t0 = 1_700_000_000_000L
        val shortSamples = listOf(
            BatterySampleEntity(timestampEpochMillis = t0, levelPercent = 90, temperatureCelsius = 30f, isCharging = false, isScreenOn = true),
            BatterySampleEntity(timestampEpochMillis = t0 + 3600_000L, levelPercent = 84, temperatureCelsius = 31f, isCharging = false, isScreenOn = true)
        )
        val insufficient = BatteryRateCalculator.calculateConsumption(shortSamples, 84, t0 + 3600_000L)
        assertFalse(insufficient.hasEnoughSamples2h)
        assertTrue(insufficient.collectingStatusText.contains("a recolher dados"))
        assertNull(insufficient.screenOnDrainPercentPerHour)

        // 1 h ecrã ligado (-10%) + 2 h ecrã desligado em repouso (-4% => 2%/h)
        val validSamples = listOf(
            BatterySampleEntity(timestampEpochMillis = t0, levelPercent = 90, temperatureCelsius = 30f, isCharging = false, isScreenOn = true),
            BatterySampleEntity(timestampEpochMillis = t0 + 3600_000L, levelPercent = 80, temperatureCelsius = 32f, isCharging = false, isScreenOn = false),
            BatterySampleEntity(timestampEpochMillis = t0 + 3 * 3600_000L, levelPercent = 76, temperatureCelsius = 28f, isCharging = false, isScreenOn = false)
        )
        val measured = BatteryRateCalculator.calculateConsumption(validSamples, 76, t0 + 3 * 3600_000L)
        assertTrue(measured.hasEnoughSamples2h)
        assertNotNull(measured.screenOnDrainPercentPerHour)
        assertNotNull(measured.screenOffIdleDrainPercentPerHour)
        assertEquals(10.0, measured.screenOnDrainPercentPerHour!!, 0.05)
        assertEquals(2.0, measured.screenOffIdleDrainPercentPerHour!!, 0.05)
    }

    @Test
    fun `background process candidate selector filters out recent foreground system launcher and active media`() {
        val now = 1_000_000_000L
        val fortyMinAgo = now - 40 * 60_000L
        val tenMinAgo = now - 10 * 60_000L

        val inputs = listOf(
            BackgroundAppCandidateInput(
                packageName = "com.candidate.fgs",
                hasUnstoppedForegroundService = true,
                hasActivityInLast3Hours = true,
                lastForegroundResumeMillis = fortyMinAgo
            ),
            BackgroundAppCandidateInput(
                packageName = "com.recent.foreground",
                hasUnstoppedForegroundService = false,
                hasActivityInLast3Hours = true,
                lastForegroundResumeMillis = tenMinAgo // < 30 min -> ignorada
            ),
            BackgroundAppCandidateInput(
                packageName = "com.music.active",
                hasUnstoppedForegroundService = true,
                hasActivityInLast3Hours = true,
                lastForegroundResumeMillis = fortyMinAgo,
                isActiveMusicOrNavApp = true // música ativa -> ignorada
            ),
            BackgroundAppCandidateInput(
                packageName = "com.android.launcher3",
                hasUnstoppedForegroundService = false,
                hasActivityInLast3Hours = true,
                lastForegroundResumeMillis = fortyMinAgo,
                isLauncher = true // launcher -> ignorada
            )
        )

        val selected = BackgroundProcessCandidateSelector.selectCandidates(inputs, now)
        assertEquals(listOf("com.candidate.fgs"), selected)
    }

    @Test
    fun `app impact calculator formats battery reason filters unused apps and verifies batch uninstall`() {
        val reason = AppImpactCalculator.buildBatteryReasonTextPt(
            foregroundMillis = 15 * 60_000L,
            backgroundServiceMillis = (2 * 60 + 14) * 60_000L,
            wakeupsCount = 8
        )
        assertEquals("2 h 14 min em fundo com serviço ativo · 15 min em ecrã · 8 ativações", reason)
        assertEquals("3 de 7", AppImpactCalculator.formatBatchStepProgressPt(3, 7))

        val app35d = AppImpactItem(
            packageName = "com.unused.35",
            appName = "Unused 35",
            monogram = "U3",
            uid = 1001,
            isSystemApp = false,
            canBeDisabledBloatware = false,
            totalStorageBytes = 100_000_000L,
            daysSinceLastUse = 35
        )
        val app95d = app35d.copy(
            packageName = "com.unused.95",
            appName = "Unused 95",
            totalStorageBytes = 250_000_000L,
            daysSinceLastUse = 95
        )
        val systemUnused = app95d.copy(packageName = "com.android.sys", isSystemApp = true)

        val unused30 = AppImpactCalculator.filterUnusedApps(listOf(app35d, app95d, systemUnused), 30)
        val unused90 = AppImpactCalculator.filterUnusedApps(listOf(app35d, app95d, systemUnused), 90)
        assertEquals(listOf("com.unused.95", "com.unused.35"), unused30.map { it.packageName })
        assertEquals(listOf("com.unused.95"), unused90.map { it.packageName })

        // Simula que apenas com.unused.95 foi realmente desinstalado pelo utilizador
        val (removed, freedBytes) = AppImpactCalculator.verifyBatchUninstallResult(
            requestedApps = listOf(app35d, app95d),
            isStillInstalled = { pkg -> pkg == "com.unused.35" }
        )
        assertEquals(1, removed.size)
        assertEquals("com.unused.95", removed.first().packageName)
        assertEquals(250_000_000L, freedBytes)
    }
}
