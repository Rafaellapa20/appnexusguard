package com.nexusguard.android.domain

import android.os.PowerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
