package com.mobilecontrol.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobilecontrol.app.domain.model.DeviceProfile
import com.mobilecontrol.app.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "mobile_control_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val INSTANCE_ID = stringPreferencesKey("instance_id")
        val SERVER_URL = stringPreferencesKey("server_url")
        val SERVER_FINGERPRINT = stringPreferencesKey("server_fingerprint")
        val PAIRED_AT = longPreferencesKey("paired_at")

        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val START_DASHBOARD_ID = stringPreferencesKey("start_dashboard_id")
        val LAST_CONNECTION_AT = longPreferencesKey("last_connection_at")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val PUSH_NOTIFICATIONS_ENABLED = booleanPreferencesKey("push_notifications_enabled")
        val LAST_ALARM_CATCH_UP_AT = longPreferencesKey("last_alarm_catch_up_at")
    }

    fun observeDeviceProfile(): Flow<DeviceProfile?> = context.dataStore.data.map { prefs ->
        val deviceId = prefs[Keys.DEVICE_ID] ?: return@map null
        DeviceProfile(
            deviceId = deviceId,
            deviceName = prefs[Keys.DEVICE_NAME].orEmpty(),
            instanceId = prefs[Keys.INSTANCE_ID].orEmpty(),
            serverUrl = prefs[Keys.SERVER_URL].orEmpty(),
            serverFingerprint = prefs[Keys.SERVER_FINGERPRINT].orEmpty(),
            pairedAt = prefs[Keys.PAIRED_AT] ?: 0L,
        )
    }

    suspend fun saveDeviceProfile(profile: DeviceProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_ID] = profile.deviceId
            prefs[Keys.DEVICE_NAME] = profile.deviceName
            prefs[Keys.INSTANCE_ID] = profile.instanceId
            prefs[Keys.SERVER_URL] = profile.serverUrl
            prefs[Keys.SERVER_FINGERPRINT] = profile.serverFingerprint
            prefs[Keys.PAIRED_AT] = profile.pairedAt
        }
    }

    suspend fun clearDeviceProfile() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.DEVICE_ID)
            prefs.remove(Keys.DEVICE_NAME)
            prefs.remove(Keys.INSTANCE_ID)
            prefs.remove(Keys.SERVER_URL)
            prefs.remove(Keys.SERVER_FINGERPRINT)
            prefs.remove(Keys.PAIRED_AT)
            prefs.remove(Keys.START_DASHBOARD_ID)
        }
    }

    fun observeAppLockEnabled(): Flow<Boolean> = context.dataStore.data.map { it[Keys.APP_LOCK_ENABLED] ?: true }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.APP_LOCK_ENABLED] = enabled }
    }

    fun observeBiometricEnabled(): Flow<Boolean> = context.dataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    fun observeStartDashboardId(): Flow<String?> = context.dataStore.data.map { it[Keys.START_DASHBOARD_ID] }

    suspend fun setStartDashboardId(id: String) {
        context.dataStore.edit { it[Keys.START_DASHBOARD_ID] = id }
    }

    suspend fun getStartDashboardId(): String? = observeStartDashboardId().first()

    suspend fun setLastConnectionAt(epochMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_CONNECTION_AT] = epochMillis }
    }

    fun observeLastConnectionAt(): Flow<Long?> = context.dataStore.data.map { it[Keys.LAST_CONNECTION_AT] }

    fun observeThemeMode(): Flow<ThemeMode> = context.dataStore.data.map { ThemeMode.fromWireName(it[Keys.THEME_MODE]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.wireName }
    }

    fun observePushNotificationsEnabled(): Flow<Boolean> = context.dataStore.data.map { it[Keys.PUSH_NOTIFICATIONS_ENABLED] ?: false }

    suspend fun setPushNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PUSH_NOTIFICATIONS_ENABLED] = enabled }
    }

    /** Watermark for AlarmMonitor's catch-up fetch (GET /alarm-events?since=) - 0 means "never
     *  caught up before", so the very first run asks for everything the server still retains. */
    suspend fun getLastAlarmCatchUpAt(): Long = context.dataStore.data.map { it[Keys.LAST_ALARM_CATCH_UP_AT] ?: 0L }.first()

    suspend fun setLastAlarmCatchUpAt(epochMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_ALARM_CATCH_UP_AT] = epochMillis }
    }
}
