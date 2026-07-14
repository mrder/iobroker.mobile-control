package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.data.local.AppDatabase
import com.mobilecontrol.app.data.local.SettingsDataStore
import com.mobilecontrol.app.data.local.TokenStore
import com.mobilecontrol.app.domain.model.DeviceProfile
import com.mobilecontrol.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val tokenStore: TokenStore,
    private val appDatabase: AppDatabase,
) : SettingsRepository {

    override fun observeDeviceProfile(): Flow<DeviceProfile?> = settingsDataStore.observeDeviceProfile()

    override suspend fun saveDeviceProfile(profile: DeviceProfile) = settingsDataStore.saveDeviceProfile(profile)

    override suspend fun clearDeviceProfile() = settingsDataStore.clearDeviceProfile()

    override fun observeAppLockEnabled(): Flow<Boolean> = settingsDataStore.observeAppLockEnabled()

    override suspend fun setAppLockEnabled(enabled: Boolean) = settingsDataStore.setAppLockEnabled(enabled)

    override fun observeBiometricEnabled(): Flow<Boolean> = settingsDataStore.observeBiometricEnabled()

    override suspend fun setBiometricEnabled(enabled: Boolean) = settingsDataStore.setBiometricEnabled(enabled)

    override suspend fun setPinHash(hash: String) = tokenStore.savePinHash(hash)

    override suspend fun getPinHash(): String? = tokenStore.getPinHash()

    override suspend fun hasPin(): Boolean = tokenStore.getPinHash() != null

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        appDatabase.clearAllTables()
    }

    override fun observeLastConnectionAt(): Flow<Long?> = settingsDataStore.observeLastConnectionAt()

    override suspend fun setLastConnectionAt(epochMillis: Long) = settingsDataStore.setLastConnectionAt(epochMillis)
}
