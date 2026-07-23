package com.mobilecontrol.app.ui.settings

import com.mobilecontrol.app.BuildConfig
import com.mobilecontrol.app.domain.model.DeviceProfile
import com.mobilecontrol.app.domain.model.Session
import com.mobilecontrol.app.domain.model.ThemeMode
import com.mobilecontrol.app.domain.repository.AuthRepository
import com.mobilecontrol.app.domain.repository.DiagnosticsRepository
import com.mobilecontrol.app.domain.repository.LogEntry
import com.mobilecontrol.app.domain.repository.SettingsRepository
import com.mobilecontrol.app.push.PushServiceController
import com.mobilecontrol.app.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeSettingsRepository : SettingsRepository {
    val deviceProfile = MutableStateFlow<DeviceProfile?>(null)
    val appLockEnabled = MutableStateFlow(true)
    val biometricEnabled = MutableStateFlow(false)
    val lastConnectionAt = MutableStateFlow<Long?>(null)
    val themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val pushNotificationsEnabled = MutableStateFlow(false)
    var clearCacheCalled = false

    override fun observeDeviceProfile(): Flow<DeviceProfile?> = deviceProfile
    override suspend fun saveDeviceProfile(profile: DeviceProfile) { deviceProfile.value = profile }
    override suspend fun clearDeviceProfile() { deviceProfile.value = null }

    override fun observeAppLockEnabled(): Flow<Boolean> = appLockEnabled
    override suspend fun setAppLockEnabled(enabled: Boolean) { appLockEnabled.value = enabled }

    override fun observeBiometricEnabled(): Flow<Boolean> = biometricEnabled
    override suspend fun setBiometricEnabled(enabled: Boolean) { biometricEnabled.value = enabled }

    override suspend fun setPinHash(hash: String) {}
    override suspend fun getPinHash(): String? = null
    override suspend fun hasPin(): Boolean = false

    override suspend fun clearCache() { clearCacheCalled = true }

    override fun observeLastConnectionAt(): Flow<Long?> = lastConnectionAt
    override suspend fun setLastConnectionAt(epochMillis: Long) { lastConnectionAt.value = epochMillis }

    override fun observeThemeMode(): Flow<ThemeMode> = themeMode
    override suspend fun setThemeMode(mode: ThemeMode) { themeMode.value = mode }

    override fun observePushNotificationsEnabled(): Flow<Boolean> = pushNotificationsEnabled
    override suspend fun setPushNotificationsEnabled(enabled: Boolean) { pushNotificationsEnabled.value = enabled }

    override suspend fun getLastAlarmCatchUpAt(): Long = 0L
    override suspend fun setLastAlarmCatchUpAt(epochMillis: Long) {}
}

private class FakeAuthRepository : AuthRepository {
    private val _session = MutableStateFlow<Session?>(
        Session("dev-1", "access", "refresh", Long.MAX_VALUE, "user-1", "Alice"),
    )
    override val session: StateFlow<Session?> = _session

    var logoutCalls = mutableListOf<Boolean>()
    var revocationHandled = false

    override suspend fun login(deviceId: String): Result<Session> = Result.success(_session.value!!)
    override suspend fun refresh(): Result<Session> = Result.success(_session.value!!)
    override suspend fun adoptSession(deviceId: String, accessToken: String, refreshToken: String, expiresIn: Long) {}
    override suspend fun logout(notifyServer: Boolean) {
        logoutCalls.add(notifyServer)
        _session.value = null
    }
    override suspend fun handleRevocation() { revocationHandled = true }
}

private class FakeDiagnosticsRepository : DiagnosticsRepository {
    private val _recentLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    override val recentLogs: StateFlow<List<LogEntry>> = _recentLogs

    override fun log(level: LogEntry.Level, message: String) {
        _recentLogs.value = _recentLogs.value + LogEntry(0L, level, message)
    }
    override fun clear() { _recentLogs.value = emptyList() }
}

private class FakePushServiceController : PushServiceController {
    var startCalls = 0
    var stopCalls = 0
    override fun start() { startCalls++ }
    override fun stop() { stopCalls++ }
}

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `appVersion and apiVersion are read straight from BuildConfig`() {
        val viewModel = SettingsViewModel(FakeSettingsRepository(), FakeAuthRepository(), FakeDiagnosticsRepository(), FakePushServiceController())

        assertEquals(BuildConfig.VERSION_NAME, viewModel.appVersion)
        assertEquals(BuildConfig.API_VERSION, viewModel.apiVersion)
    }

    @Test
    fun `uiState combines device profile, lock flags and logs into a single snapshot`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val profile = DeviceProfile("dev-1", "Pixel", "inst-1", "https://iobroker.local", "fp", 123L)
        settingsRepo.deviceProfile.value = profile
        settingsRepo.appLockEnabled.value = false
        settingsRepo.biometricEnabled.value = true
        val diagnosticsRepo = FakeDiagnosticsRepository()
        diagnosticsRepo.log(LogEntry.Level.WARN, "test entry")

        val viewModel = SettingsViewModel(settingsRepo, FakeAuthRepository(), diagnosticsRepo, FakePushServiceController())
        val collector = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(profile, state.deviceProfile)
        assertEquals(false, state.appLockEnabled)
        assertEquals(true, state.biometricEnabled)
        assertEquals(1, state.logs.size)
        collector.cancel()
    }

    @Test
    fun `setAppLockEnabled forwards the flag to the repository`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val viewModel = SettingsViewModel(settingsRepo, FakeAuthRepository(), FakeDiagnosticsRepository(), FakePushServiceController())

        viewModel.setAppLockEnabled(false)
        advanceUntilIdle()

        assertEquals(false, settingsRepo.appLockEnabled.value)
    }

    @Test
    fun `setBiometricEnabled forwards the flag to the repository`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val viewModel = SettingsViewModel(settingsRepo, FakeAuthRepository(), FakeDiagnosticsRepository(), FakePushServiceController())

        viewModel.setBiometricEnabled(true)
        advanceUntilIdle()

        assertEquals(true, settingsRepo.biometricEnabled.value)
    }

    @Test
    fun `setPushNotificationsEnabled forwards the flag to the repository and starts the push service`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val pushController = FakePushServiceController()
        val viewModel = SettingsViewModel(settingsRepo, FakeAuthRepository(), FakeDiagnosticsRepository(), pushController)

        viewModel.setPushNotificationsEnabled(true)
        advanceUntilIdle()

        assertEquals(true, settingsRepo.pushNotificationsEnabled.value)
        assertEquals(1, pushController.startCalls)
        assertEquals(0, pushController.stopCalls)
    }

    @Test
    fun `setPushNotificationsEnabled(false) persists the flag and stops the push service`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val pushController = FakePushServiceController()
        val viewModel = SettingsViewModel(settingsRepo, FakeAuthRepository(), FakeDiagnosticsRepository(), pushController)

        viewModel.setPushNotificationsEnabled(false)
        advanceUntilIdle()

        assertEquals(false, settingsRepo.pushNotificationsEnabled.value)
        assertEquals(0, pushController.startCalls)
        assertEquals(1, pushController.stopCalls)
    }

    @Test
    fun `setThemeMode forwards the mode to the repository`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val viewModel = SettingsViewModel(settingsRepo, FakeAuthRepository(), FakeDiagnosticsRepository(), FakePushServiceController())

        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, settingsRepo.themeMode.value)
    }

    @Test
    fun `clearCache delegates to the repository`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val viewModel = SettingsViewModel(settingsRepo, FakeAuthRepository(), FakeDiagnosticsRepository(), FakePushServiceController())

        viewModel.clearCache()
        advanceUntilIdle()

        assertTrue(settingsRepo.clearCacheCalled)
    }

    @Test
    fun `logout notifies the server, writes an audit log entry and then invokes the callback`() = runTest {
        val authRepo = FakeAuthRepository()
        val diagnosticsRepo = FakeDiagnosticsRepository()
        val viewModel = SettingsViewModel(FakeSettingsRepository(), authRepo, diagnosticsRepo, FakePushServiceController())
        var onDoneCalled = false

        viewModel.logout { onDoneCalled = true }
        advanceUntilIdle()

        assertEquals(listOf(true), authRepo.logoutCalls)
        assertTrue(diagnosticsRepo.recentLogs.value.any { it.level == LogEntry.Level.INFO })
        assertTrue(onDoneCalled)
    }
}
