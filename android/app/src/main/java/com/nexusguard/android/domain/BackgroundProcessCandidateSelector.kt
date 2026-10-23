package com.nexusguard.android.domain

data class BackgroundAppCandidateInput(
    val packageName: String,
    val hasUnstoppedForegroundService: Boolean,
    val hasActivityInLast3Hours: Boolean,
    val lastForegroundResumeMillis: Long,
    val isSelfApp: Boolean = false,
    val isImeKeyboard: Boolean = false,
    val isLauncher: Boolean = false,
    val isSystemApp: Boolean = false,
    val isActiveMusicOrNavApp: Boolean = false
)

object BackgroundProcessCandidateSelector {
    private const val THIRTY_MINUTES_MS = 30L * 60_000L

    /**
     * Fase 0: Seleciona apenas as apps candidatas honestas para killBackgroundProcesses:
     * - Com FOREGROUND_SERVICE_START sem FOREGROUND_SERVICE_STOP ou com atividade nas últimas 3 h;
     * - Sem uso em primeiro plano há mais de 30 min;
     * - Excluindo a própria app, teclado, launcher, apps do sistema e apps de música/navegação ativas.
     */
    fun selectCandidates(
        inputs: List<BackgroundAppCandidateInput>,
        nowMillis: Long
    ): List<String> {
        return inputs.filter { item ->
            if (item.isSelfApp ||
                item.isImeKeyboard ||
                item.isLauncher ||
                item.isSystemApp ||
                item.isActiveMusicOrNavApp
            ) {
                return@filter false
            }
            val notUsedInForegroundRecently = item.lastForegroundResumeMillis <= 0L ||
                (nowMillis - item.lastForegroundResumeMillis) > THIRTY_MINUTES_MS

            val hasBackgroundFootprint = item.hasUnstoppedForegroundService || item.hasActivityInLast3Hours
            notUsedInForegroundRecently && hasBackgroundFootprint
        }.map { it.packageName }
    }
}
