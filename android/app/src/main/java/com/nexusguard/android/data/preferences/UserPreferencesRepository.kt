package com.nexusguard.android.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val taskFinishedNotificationEnabled: Boolean = true
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
            taskFinishedNotificationEnabled = prefs[Keys.TASK_FINISHED_NOTIFICATION] ?: true
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DARK_THEME_ENABLED] = enabled
        }
    }

    suspend fun setWeeklyScanEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.WEEKLY_SCAN_ENABLED] = enabled
        }
    }

    suspend fun setStorageAlertEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.STORAGE_ALERT_ENABLED] = enabled
        }
    }

    suspend fun setThermalAlertEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.THERMAL_ALERT_ENABLED] = enabled
        }
    }

    suspend fun setBatteryAlertEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.BATTERY_ALERT_ENABLED] = enabled
        }
    }

    suspend fun setTaskFinishedNotificationEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.TASK_FINISHED_NOTIFICATION] = enabled
        }
    }
}
