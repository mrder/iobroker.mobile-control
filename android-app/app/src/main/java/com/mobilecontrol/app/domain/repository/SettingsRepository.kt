package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.DeviceProfile
import com.mobilecontrol.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeDeviceProfile(): Flow<DeviceProfile?>
    suspend fun saveDeviceProfile(profile: DeviceProfile)
    suspend fun clearDeviceProfile()

    fun observeAppLockEnabled(): Flow<Boolean>
    suspend fun setAppLockEnabled(enabled: Boolean)

    fun observeBiometricEnabled(): Flow<Boolean>
    suspend fun setBiometricEnabled(enabled: Boolean)

    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)

    fun observePushNotificationsEnabled(): Flow<Boolean>
    suspend fun setPushNotificationsEnabled(enabled: Boolean)

    /** Watermark for AlarmMonitor's catch-up fetch (GET /alarm-events?since=) - 0 means "never
     *  caught up before", so the very first run asks for everything the server still retains. */
    suspend fun getLastAlarmCatchUpAt(): Long
    suspend fun setLastAlarmCatchUpAt(epochMillis: Long)

    suspend fun setPinHash(hash: String)
    suspend fun getPinHash(): String?
    suspend fun hasPin(): Boolean

    suspend fun clearCache()

    fun observeLastConnectionAt(): Flow<Long?>
    suspend fun setLastConnectionAt(epochMillis: Long)
}
