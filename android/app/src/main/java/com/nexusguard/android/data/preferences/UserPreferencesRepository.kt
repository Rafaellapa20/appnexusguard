package com.nexusguard.android.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    val activeChargeMaxTempDeci: Int = 0,
    val scheduledCleanFrequencyDays: Int = 7,
    val scheduledCleanHourOfDay: Int = 3,
    val autoCleanEmptyFolders: Boolean = true,
    val autoPurgeExpiredTrash: Boolean = true,
    val autoClearOwnCache: Boolean = true,
    val junkOver1GbAlertEnabled: Boolean = true,
    val newAppBatteryAlertEnabled: Boolean = true,
    val lastSmartAlertEpochMillis: Long = 0L,
    val accessibilityDisclosureAccepted: Boolean = false,
    val autoHibernationEnabled: Boolean = false,
    val autoHibernationActivatedAtMillis: Long = 0L,
    val autoHibernateOnScreenOff: Boolean = true,
    val autoHibernatePeriodicHours: Int = 6,
    val autoHibernateBeforeGameBoost: Boolean = true,
    val autoHibernateTop10BatteryOnly: Boolean = true,
    val neverClosePackagesCsv: String = "",
    val alwaysHibernatePackagesCsv: String = ""
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
        val SCHEDULED_CLEAN_FREQ_DAYS = intPreferencesKey("scheduled_clean_freq_days")
        val SCHEDULED_CLEAN_HOUR = intPreferencesKey("scheduled_clean_hour")
        val AUTO_CLEAN_EMPTY_FOLDERS = booleanPreferencesKey("auto_clean_empty_folders")
        val AUTO_PURGE_EXPIRED_TRASH = booleanPreferencesKey("auto_purge_expired_trash")
        val AUTO_CLEAR_OWN_CACHE = booleanPreferencesKey("auto_clear_own_cache")
        val JUNK_OVER_1GB_ALERT = booleanPreferencesKey("junk_over_1gb_alert")
        val NEW_APP_BATTERY_ALERT = booleanPreferencesKey("new_app_battery_alert")
        val LAST_SMART_ALERT_MILLIS = longPreferencesKey("last_smart_alert_millis")
        val ACCESSIBILITY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("accessibility_disclosure_accepted")
        val AUTO_HIBERNATION_ENABLED = booleanPreferencesKey("auto_hibernation_enabled")
        val AUTO_HIBERNATION_ACTIVATED_MILLIS = longPreferencesKey("auto_hibernation_activated_millis")
        val AUTO_HIBERNATE_ON_SCREEN_OFF = booleanPreferencesKey("auto_hibernate_on_screen_off")
        val AUTO_HIBERNATE_PERIODIC_HOURS = intPreferencesKey("auto_hibernate_periodic_hours")
        val AUTO_HIBERNATE_BEFORE_GAME_BOOST = booleanPreferencesKey("auto_hibernate_before_game_boost")
        val AUTO_HIBERNATE_TOP10_ONLY = booleanPreferencesKey("auto_hibernate_top10_only")
        val NEVER_CLOSE_PACKAGES_CSV = stringPreferencesKey("never_close_packages_csv")
        val ALWAYS_HIBERNATE_PACKAGES_CSV = stringPreferencesKey("always_hibernate_packages_csv")
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
            activeChargeMaxTempDeci = prefs[Keys.ACTIVE_CHARGE_MAX_TEMP_DECI] ?: 0,
            scheduledCleanFrequencyDays = prefs[Keys.SCHEDULED_CLEAN_FREQ_DAYS] ?: 7,
            scheduledCleanHourOfDay = prefs[Keys.SCHEDULED_CLEAN_HOUR] ?: 3,
            autoCleanEmptyFolders = prefs[Keys.AUTO_CLEAN_EMPTY_FOLDERS] ?: true,
            autoPurgeExpiredTrash = prefs[Keys.AUTO_PURGE_EXPIRED_TRASH] ?: true,
            autoClearOwnCache = prefs[Keys.AUTO_CLEAR_OWN_CACHE] ?: true,
            junkOver1GbAlertEnabled = prefs[Keys.JUNK_OVER_1GB_ALERT] ?: true,
            newAppBatteryAlertEnabled = prefs[Keys.NEW_APP_BATTERY_ALERT] ?: true,
            lastSmartAlertEpochMillis = prefs[Keys.LAST_SMART_ALERT_MILLIS] ?: 0L,
            accessibilityDisclosureAccepted = prefs[Keys.ACCESSIBILITY_DISCLOSURE_ACCEPTED] ?: false,
            autoHibernationEnabled = prefs[Keys.AUTO_HIBERNATION_ENABLED] ?: false,
            autoHibernationActivatedAtMillis = prefs[Keys.AUTO_HIBERNATION_ACTIVATED_MILLIS] ?: 0L,
            autoHibernateOnScreenOff = prefs[Keys.AUTO_HIBERNATE_ON_SCREEN_OFF] ?: true,
            autoHibernatePeriodicHours = prefs[Keys.AUTO_HIBERNATE_PERIODIC_HOURS] ?: 6,
            autoHibernateBeforeGameBoost = prefs[Keys.AUTO_HIBERNATE_BEFORE_GAME_BOOST] ?: true,
            autoHibernateTop10BatteryOnly = prefs[Keys.AUTO_HIBERNATE_TOP10_ONLY] ?: true,
            neverClosePackagesCsv = prefs[Keys.NEVER_CLOSE_PACKAGES_CSV] ?: "",
            alwaysHibernatePackagesCsv = prefs[Keys.ALWAYS_HIBERNATE_PACKAGES_CSV] ?: ""
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

    suspend fun setScheduledCleanSchedule(frequencyDays: Int, hourOfDay: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEDULED_CLEAN_FREQ_DAYS] = frequencyDays
            prefs[Keys.SCHEDULED_CLEAN_HOUR] = hourOfDay.coerceIn(0, 23)
        }
    }

    suspend fun setScheduledAutoCleanOptions(
        emptyFolders: Boolean,
        expiredTrash: Boolean,
        ownCache: Boolean
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_CLEAN_EMPTY_FOLDERS] = emptyFolders
            prefs[Keys.AUTO_PURGE_EXPIRED_TRASH] = expiredTrash
            prefs[Keys.AUTO_CLEAR_OWN_CACHE] = ownCache
        }
    }

    suspend fun setJunkOver1GbAlertEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.JUNK_OVER_1GB_ALERT] = enabled }
    }

    suspend fun setNewAppBatteryAlertEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.NEW_APP_BATTERY_ALERT] = enabled }
    }

    suspend fun setLastSmartAlertSent(epochMillis: Long) {
        dataStore.edit { prefs -> prefs[Keys.LAST_SMART_ALERT_MILLIS] = epochMillis }
    }

    suspend fun setAccessibilityDisclosureAccepted(accepted: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ACCESSIBILITY_DISCLOSURE_ACCEPTED] = accepted }
    }

    suspend fun setAutoHibernationEnabled(enabled: Boolean, nowMillis: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_HIBERNATION_ENABLED] = enabled
            if (enabled && (prefs[Keys.AUTO_HIBERNATION_ACTIVATED_MILLIS] ?: 0L) == 0L) {
                prefs[Keys.AUTO_HIBERNATION_ACTIVATED_MILLIS] = nowMillis
            } else if (!enabled) {
                prefs[Keys.AUTO_HIBERNATION_ACTIVATED_MILLIS] = 0L
            }
        }
    }

    suspend fun setAutoHibernationConfig(
        onScreenOff: Boolean,
        periodicHours: Int,
        beforeGameBoost: Boolean,
        top10BatteryOnly: Boolean
    ) {
        val validHours = when (periodicHours) {
            12 -> 12
            24 -> 24
            else -> 6
        }
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_HIBERNATE_ON_SCREEN_OFF] = onScreenOff
            prefs[Keys.AUTO_HIBERNATE_PERIODIC_HOURS] = validHours
            prefs[Keys.AUTO_HIBERNATE_BEFORE_GAME_BOOST] = beforeGameBoost
            prefs[Keys.AUTO_HIBERNATE_TOP10_ONLY] = top10BatteryOnly
        }
    }

    suspend fun toggleNeverClosePackage(packageName: String) {
        dataStore.edit { prefs ->
            val current = (prefs[Keys.NEVER_CLOSE_PACKAGES_CSV] ?: "")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            prefs[Keys.NEVER_CLOSE_PACKAGES_CSV] = current.sorted().joinToString(",")
        }
    }

    suspend fun toggleAlwaysHibernatePackage(packageName: String) {
        dataStore.edit { prefs ->
            val current = (prefs[Keys.ALWAYS_HIBERNATE_PACKAGES_CSV] ?: "")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            prefs[Keys.ALWAYS_HIBERNATE_PACKAGES_CSV] = current.sorted().joinToString(",")
        }
    }
}
