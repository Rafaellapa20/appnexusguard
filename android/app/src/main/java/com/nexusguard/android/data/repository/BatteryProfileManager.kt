package com.nexusguard.android.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.nexusguard.android.data.local.HistoryEntity
import com.nexusguard.android.data.local.NexusGuardDao
import com.nexusguard.android.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryProfileManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val dao: NexusGuardDao
) {
    fun hasWriteSettingsPermission(): Boolean {
        return Settings.System.canWrite(context)
    }

    /**
     * Fase 2 (4): Deteta se o fabricante tem limite de carga nativo (Samsung One UI ou Pixel com Android 15+).
     */
    fun hasOemNativeChargeLimit(): Boolean {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val isSamsung = manufacturer.equals("samsung", ignoreCase = true)
        val isPixelAndroid15Plus = manufacturer.equals("google", ignoreCase = true) && Build.VERSION.SDK_INT >= 35
        return isSamsung || isPixelAndroid15Plus
    }

    /**
     * Fase 2 (3): Aplica o perfil «Poupança Nexus» (tempo limite do ecrã de 30 s e brilho automático),
     * guardando os valores anteriores em DataStore para permitir «Repor definições anteriores».
     */
    suspend fun activateNexusSaverProfile(isAutoTriggered: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteSettingsPermission()) return@withContext false
        val resolver = context.contentResolver
        return@withContext try {
            val prevTimeout = Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
            val prevBrightnessMode = Settings.System.getInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            preferencesRepository.saveNexusBatterySaverActivated(prevTimeout, prevBrightnessMode)

            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            )

            val origin = if (isAutoTriggered) "Ativado automaticamente" else "Ativado pelo utilizador"
            dao.insertHistory(
                HistoryEntity(
                    actionType = "BATTERY_PROFILE",
                    title = "Perfil Poupança Nexus ativado",
                    details = "$origin · Tempo limite do ecrã: 30 s · Brilho automático (valores anteriores guardados)",
                    timestampEpochMillis = System.currentTimeMillis()
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fase 2 (3): Repõe os valores anteriores de tempo limite do ecrã e modo de brilho guardados em DataStore.
     */
    suspend fun restorePreviousSettings(): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteSettingsPermission()) return@withContext false
        val prefs = preferencesRepository.preferencesFlow.first()
        val resolver = context.contentResolver
        return@withContext try {
            if (prefs.savedScreenTimeoutMs > 0) {
                Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, prefs.savedScreenTimeoutMs)
            }
            if (prefs.savedBrightnessMode >= 0) {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, prefs.savedBrightnessMode)
            }
            preferencesRepository.saveNexusBatterySaverRestored()

            dao.insertHistory(
                HistoryEntity(
                    actionType = "BATTERY_PROFILE",
                    title = "Perfil Poupança Nexus desativado",
                    details = "Definições anteriores de tempo limite do ecrã e brilho repostas com sucesso",
                    timestampEpochMillis = System.currentTimeMillis()
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
