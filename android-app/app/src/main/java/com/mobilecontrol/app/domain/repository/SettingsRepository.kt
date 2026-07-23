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

    suspend fun setPinHash(hash: String)
    suspend fun getPinHash(): String?
    suspend fun hasPin(): Boolean

    suspend fun clearCache()

    fun observeLastConnectionAt(): Flow<Long?>
    suspend fun setLastConnectionAt(epochMillis: Long)
}
