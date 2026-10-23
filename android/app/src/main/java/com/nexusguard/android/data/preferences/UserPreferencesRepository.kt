package com.nexusguard.android.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class UserPreferencesState(
    val isLoaded: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val darkThemeEnabled: Boolean = true,
    val weeklyScanEnabled: Boolean = true,
    val storageAlertEnabled: Boolean = true,
    val thermalAlertEnabled: Boolean = true,
    val batteryAlertEnabled: Boolean = true,
    val taskFinishedNotificationEnabled: Boolean = true,
    val lastConfirmedUpdateCode: Int = 0,
    val nexusBatterySaverActive: Boolean = false,
    val savedScreenTimeoutMs: Int = -1,
    val savedBrightnessMode: Int = -1,
    val autoBatterySaverEnabled: Boolean = false,
    val autoBatterySaverThresholdPercent: Int = 20,
    val chargeLimitAlarmEnabled: Boolean = false,
    val chargeLimitTargetPercent: Int = 80,
    val activeChargeStartEpochMillis: Long = 0L,
    val activeChargeStartPercent: Int = 0,
    val activeChargeMaxTempDeci: Int = 0
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val WEEKLY_SCAN_ENABLED = booleanPreferencesKey("weekly_scan_enabled")
        val STORAGE_ALERT_ENABLED = booleanPreferencesKey("storage_alert_enabled")
        val THERMAL_ALERT_ENABLED = booleanPreferencesKey("thermal_alert_enabled")
        val BATTERY_ALERT_ENABLED = booleanPreferencesKey("battery_alert_enabled")
        val TASK_FINISHED_NOTIFICATION = booleanPreferencesKey("task_finished_notification")
        val LAST_CONFIRMED_UPDATE_CODE = intPreferencesKey("last_confirmed_update_code")
        val NEXUS_BATTERY_SAVER_ACTIVE = booleanPreferencesKey("nexus_battery_saver_active")
        val SAVED_SCREEN_TIMEOUT_MS = intPreferencesKey("saved_screen_timeout_ms")
        val SAVED_BRIGHTNESS_MODE = intPreferencesKey("saved_brightness_mode")
        val AUTO_BATTERY_SAVER_ENABLED = booleanPreferencesKey("auto_battery_saver_enabled")
        val AUTO_BATTERY_SAVER_THRESHOLD = intPreferencesKey("auto_battery_saver_threshold")
        val CHARGE_LIMIT_ALARM_ENABLED = booleanPreferencesKey("charge_limit_alarm_enabled")
        val CHARGE_LIMIT_TARGET_PERCENT = intPreferencesKey("charge_limit_target_percent")
        val ACTIVE_CHARGE_START_MILLIS = longPreferencesKey("active_charge_start_millis")
        val ACTIVE_CHARGE_START_PERCENT = intPreferencesKey("active_charge_start_percent")
        val ACTIVE_CHARGE_MAX_TEMP_DECI = intPreferencesKey("active_charge_max_temp_deci")
    }

    val preferencesFlow: Flow<UserPreferencesState> = dataStore.data.map { prefs ->
        UserPreferencesState(
            isLoaded = true,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            darkThemeEnabled = prefs[Keys.DARK_THEME_ENABLED] ?: true,
            weeklyScanEnabled = prefs[Keys.WEEKLY_SCAN_ENABLED] ?: true,
            storageAlertEnabled = prefs[Keys.STORAGE_ALERT_ENABLED] ?: true,
            thermalAlertEnabled = prefs[Keys.THERMAL_ALERT_ENABLED] ?: true,
            batteryAlertEnabled = prefs[Keys.BATTERY_ALERT_ENABLED] ?: true,
            taskFinishedNotificationEnabled = prefs[Keys.TASK_FINISHED_NOTIFICATION] ?: true,
            lastConfirmedUpdateCode = prefs[Keys.LAST_CONFIRMED_UPDATE_CODE] ?: 0,
            nexusBatterySaverActive = prefs[Keys.NEXUS_BATTERY_SAVER_ACTIVE] ?: false,
            savedScreenTimeoutMs = prefs[Keys.SAVED_SCREEN_TIMEOUT_MS] ?: -1,
            savedBrightnessMode = prefs[Keys.SAVED_BRIGHTNESS_MODE] ?: -1,
            autoBatterySaverEnabled = prefs[Keys.AUTO_BATTERY_SAVER_ENABLED] ?: false,
            autoBatterySaverThresholdPercent = prefs[Keys.AUTO_BATTERY_SAVER_THRESHOLD] ?: 20,
            chargeLimitAlarmEnabled = prefs[Keys.CHARGE_LIMIT_ALARM_ENABLED] ?: false,
            chargeLimitTargetPercent = prefs[Keys.CHARGE_LIMIT_TARGET_PERCENT] ?: 80,
            activeChargeStartEpochMillis = prefs[Keys.ACTIVE_CHARGE_START_MILLIS] ?: 0L,
            activeChargeStartPercent = prefs[Keys.ACTIVE_CHARGE_START_PERCENT] ?: 0,
            activeChargeMaxTempDeci = prefs[Keys.ACTIVE_CHARGE_MAX_TEMP_DECI] ?: 0
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.DARK_THEME_ENABLED] = enabled }
    }

    suspend fun setWeeklyScanEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.WEEKLY_SCAN_ENABLED] = enabled }
    }

    suspend fun setStorageAlertEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.STORAGE_ALERT_ENABLED] = enabled }
    }

    suspend fun setThermalAlertEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.THERMAL_ALERT_ENABLED] = enabled }
    }

    suspend fun setBatteryAlertEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.BATTERY_ALERT_ENABLED] = enabled }
    }

    suspend fun setTaskFinishedNotificationEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.TASK_FINISHED_NOTIFICATION] = enabled }
    }

    suspend fun setLastConfirmedUpdateCode(versionCode: Int) {
        dataStore.edit { prefs -> prefs[Keys.LAST_CONFIRMED_UPDATE_CODE] = versionCode }
    }

    suspend fun saveNexusBatterySaverActivated(previousTimeoutMs: Int, previousBrightnessMode: Int) {
        dataStore.edit { prefs ->
            if (!(prefs[Keys.NEXUS_BATTERY_SAVER_ACTIVE] ?: false)) {
                prefs[Keys.SAVED_SCREEN_TIMEOUT_MS] = previousTimeoutMs
                prefs[Keys.SAVED_BRIGHTNESS_MODE] = previousBrightnessMode
            }
            prefs[Keys.NEXUS_BATTERY_SAVER_ACTIVE] = true
        }
    }

    suspend fun saveNexusBatterySaverRestored() {
        dataStore.edit { prefs ->
            prefs[Keys.NEXUS_BATTERY_SAVER_ACTIVE] = false
        }
    }

    suspend fun setAutoBatterySaver(enabled: Boolean, thresholdPercent: Int = 20) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_BATTERY_SAVER_ENABLED] = enabled
            prefs[Keys.AUTO_BATTERY_SAVER_THRESHOLD] = thresholdPercent.coerceIn(10, 50)
        }
    }

    suspend fun setChargeLimitAlarm(enabled: Boolean, targetPercent: Int = 80) {
        val validTarget = when (targetPercent) {
            85 -> 85
            90 -> 90
            else -> 80
        }
        dataStore.edit { prefs ->
            prefs[Keys.CHARGE_LIMIT_ALARM_ENABLED] = enabled
            prefs[Keys.CHARGE_LIMIT_TARGET_PERCENT] = validTarget
        }
    }

    suspend fun updateActiveChargingSession(startMillis: Long, startPercent: Int, maxTempDeci: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_CHARGE_START_MILLIS] = startMillis
            prefs[Keys.ACTIVE_CHARGE_START_PERCENT] = startPercent
            prefs[Keys.ACTIVE_CHARGE_MAX_TEMP_DECI] = maxTempDeci
        }
    }

    suspend fun clearActiveChargingSession() {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_CHARGE_START_MILLIS] = 0L
            prefs[Keys.ACTIVE_CHARGE_START_PERCENT] = 0
            prefs[Keys.ACTIVE_CHARGE_MAX_TEMP_DECI] = 0
        }
    }
}
