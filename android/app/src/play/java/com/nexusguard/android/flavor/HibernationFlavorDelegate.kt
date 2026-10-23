package com.nexusguard.android.flavor

import android.content.Context
import com.nexusguard.android.domain.ForceStopBackendType
import com.nexusguard.android.domain.HibernationAppStepStatus

object HibernationFlavorDelegate {

    const val IS_HIBERNATION_SUPPORTED: Boolean = false
    const val SHIZUKU_PACKAGE_NAME: String = "moe.shizuku.privileged.api"

    fun isShizukuInstalled(context: Context): Boolean = false

    fun isShizukuRunningAndAuthorized(): Boolean = false

    fun isShizukuRunningWithoutPermission(): Boolean = false

    fun requestShizukuPermission(requestCode: Int = 1101): Boolean = false

    fun isAccessibilityServiceEnabled(context: Context): Boolean = false

    fun detectActiveBackend(context: Context, isScreenInteractive: Boolean = true): ForceStopBackendType =
        ForceStopBackendType.NONE_PLAY

    fun cancelOngoingBatch() = Unit

    suspend fun executeHibernation(
        context: Context,
        packages: List<String>,
        backend: ForceStopBackendType = ForceStopBackendType.NONE_PLAY,
        onProgress: (index: Int, total: Int, packageName: String, outcome: HibernationAppStepStatus) -> Unit = { _, _, _, _ -> }
    ): Map<String, HibernationAppStepStatus> = emptyMap()
}
