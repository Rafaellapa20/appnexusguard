package com.nexusguard.android.flavor

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import com.nexusguard.android.domain.ForceStopBackendType
import com.nexusguard.android.domain.HibernationAppStepStatus
import com.nexusguard.android.domain.HibernationCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.util.concurrent.TimeUnit

object HibernationFlavorDelegate {

    const val IS_HIBERNATION_SUPPORTED: Boolean = true
    const val SHIZUKU_PACKAGE_NAME: String = "moe.shizuku.privileged.api"

    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isShizukuRunningAndAuthorized(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun isShizukuRunningWithoutPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.isPreV11()) return false
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestShizukuPermission(requestCode: Int = 1101): Boolean {
        return try {
            if (!Shizuku.pingBinder()) return false
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
            Shizuku.requestPermission(requestCode)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return NexusAccessibilityService.isRunning(context)
    }

    fun detectActiveBackend(context: Context, isScreenInteractive: Boolean = true): ForceStopBackendType {
        val interactive = runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isInteractive ?: isScreenInteractive
        }.getOrDefault(isScreenInteractive)

        return HibernationCalculator.selectBestBackend(
            shizukuAvailableAndPermitted = isShizukuRunningAndAuthorized(),
            accessibilityServiceEnabled = isAccessibilityServiceEnabled(context),
            isScreenInteractive = interactive
        )
    }

    fun cancelOngoingBatch() {
        NexusAccessibilityService.requestCancelCurrentBatch()
    }

    suspend fun executeHibernation(
        context: Context,
        packages: List<String>,
        backend: ForceStopBackendType = detectActiveBackend(context),
        onProgress: (index: Int, total: Int, packageName: String, outcome: HibernationAppStepStatus) -> Unit = { _, _, _, _ -> }
    ): Map<String, HibernationAppStepStatus> = withContext(Dispatchers.IO) {
        if (packages.isEmpty()) return@withContext emptyMap()

        when (backend) {
            ForceStopBackendType.SHIZUKU -> executeViaShizuku(packages, onProgress)
            ForceStopBackendType.ACCESSIBILITY -> NexusAccessibilityService.executeBatch(packages, onProgress)
            ForceStopBackendType.BASIC -> executeViaBasicKill(context, packages, onProgress)
            ForceStopBackendType.NONE_PLAY -> emptyMap()
        }
    }

    private fun executeViaShizuku(
        packages: List<String>,
        onProgress: (index: Int, total: Int, packageName: String, outcome: HibernationAppStepStatus) -> Unit
    ): Map<String, HibernationAppStepStatus> {
        val results = LinkedHashMap<String, HibernationAppStepStatus>()
        val total = packages.size

        packages.forEachIndexed { idx, pkg ->
            val outcome = runShizukuForceStop(pkg)
            results[pkg] = outcome
            onProgress(idx + 1, total, pkg, outcome)
        }
        return results
    }

    private fun runShizukuForceStop(packageName: String): HibernationAppStepStatus {
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }

            val process = newProcessMethod.invoke(
                null,
                arrayOf("am", "force-stop", packageName),
                null,
                null
            ) as? ShizukuRemoteProcess ?: return HibernationAppStepStatus.FAILED

            val finished = process.waitFor(3L, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                HibernationAppStepStatus.FAILED
            } else if (process.exitValue() == 0) {
                HibernationAppStepStatus.STOPPED
            } else {
                HibernationAppStepStatus.FAILED
            }
        } catch (_: Throwable) {
            HibernationAppStepStatus.FAILED
        }
    }

    private fun executeViaBasicKill(
        context: Context,
        packages: List<String>,
        onProgress: (index: Int, total: Int, packageName: String, outcome: HibernationAppStepStatus) -> Unit
    ): Map<String, HibernationAppStepStatus> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return packages.associateWith { HibernationAppStepStatus.FAILED }

        val results = LinkedHashMap<String, HibernationAppStepStatus>()
        val total = packages.size

        packages.forEachIndexed { idx, pkg ->
            val outcome = try {
                am.killBackgroundProcesses(pkg)
                HibernationAppStepStatus.STOPPED
            } catch (_: Exception) {
                HibernationAppStepStatus.FAILED
            }
            results[pkg] = outcome
            onProgress(idx + 1, total, pkg, outcome)
        }
        return results
    }
}
