package com.mobilecontrol.app.ui.lock

import com.mobilecontrol.app.domain.model.DeviceProfile
import com.mobilecontrol.app.domain.model.ThemeMode
import com.mobilecontrol.app.domain.repository.SettingsRepository
import com.mobilecontrol.app.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private class FakeSettingsRepository : SettingsRepository {
    private val _deviceProfile = MutableStateFlow<DeviceProfile?>(null)
    private val _appLockEnabled = MutableStateFlow(true)
    private val _biometricEnabled = MutableStateFlow(false)
    private val _lastConnectionAt = MutableStateFlow<Long?>(null)
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    private val _pushNotificationsEnabled = MutableStateFlow(false)

    var pinHash: String? = null
    var clearCacheCalled = false
    var failedPinAttempts = 0
    var pinLockoutUntil = 0L

    override fun observeDeviceProfile(): Flow<DeviceProfile?> = _deviceProfile
    override suspend fun saveDeviceProfile(profile: DeviceProfile) {
        _deviceProfile.value = profile
    }
    override suspend fun clearDeviceProfile() {
        _deviceProfile.value = null
    }

    override fun observeAppLockEnabled(): Flow<Boolean> = _appLockEnabled
    override suspend fun setAppLockEnabled(enabled: Boolean) {
        _appLockEnabled.value = enabled
    }

    override fun observeBiometricEnabled(): Flow<Boolean> = _biometricEnabled
    override suspend fun setBiometricEnabled(enabled: Boolean) {
        _biometricEnabled.value = enabled
    }

    override suspend fun setPinHash(hash: String) {
        pinHash = hash
    }
    override suspend fun getPinHash(): String? = pinHash
    override suspend fun hasPin(): Boolean = pinHash != null

    override suspend fun getFailedPinAttempts(): Int = failedPinAttempts
    override suspend fun setFailedPinAttempts(count: Int) {
        failedPinAttempts = count
    }
    override suspend fun getPinLockoutUntil(): Long = pinLockoutUntil
    override suspend fun setPinLockoutUntil(epochMillis: Long) {
        pinLockoutUntil = epochMillis
    }

    override suspend fun clearCache() {
        clearCacheCalled = true
    }

    override fun observeLastConnectionAt(): Flow<Long?> = _lastConnectionAt
    override suspend fun setLastConnectionAt(epochMillis: Long) {
        _lastConnectionAt.value = epochMillis
    }

    override fun observeThemeMode(): Flow<ThemeMode> = _themeMode
    override suspend fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    override fun observePushNotificationsEnabled(): Flow<Boolean> = _pushNotificationsEnabled
    override suspend fun setPushNotificationsEnabled(enabled: Boolean) {
        _pushNotificationsEnabled.value = enabled
    }

    override suspend fun getLastAlarmCatchUpAt(): Long = 0L
    override suspend fun setLastAlarmCatchUpAt(epochMillis: Long) {}
}

class LockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `setupPin stores a hashed pin (not the raw pin) and unlocks the app`() = runTest {
        val repo = FakeSettingsRepository()
        val lockManager = AppLockManager()
        val viewModel = LockViewModel(repo, lockManager)

        viewModel.setupPin("1234")

        assertNotEquals("1234", repo.pinHash)
        assertEquals(64, repo.pinHash?.length) // hex-encoded SHA-256
        assertFalse(lockManager.isLocked.value)
    }

    @Test
    fun `verifyPin with the correct pin unlocks, clears wrongPin, and returns true`() = runTest {
        val repo = FakeSettingsRepository()
        val lockManager = AppLockManager()
        val viewModel = LockViewModel(repo, lockManager)

        viewModel.setupPin("1234")
        lockManager.lock() // simulate re-lock (e.g. app backgrounded) before verifying

        val result = viewModel.verifyPin("1234")

        assertTrue(result)
        assertFalse(lockManager.isLocked.value)
        assertFalse(viewModel.wrongPin.value)
    }

    @Test
    fun `verifyPin with an incorrect pin sets wrongPin, stays locked, and returns false`() = runTest {
        val repo = FakeSettingsRepository()
        val lockManager = AppLockManager()
        val viewModel = LockViewModel(repo, lockManager)

        viewModel.setupPin("1234")
        lockManager.lock()

        val result = viewModel.verifyPin("0000")

        assertFalse(result)
        assertTrue(lockManager.isLocked.value)
        assertTrue(viewModel.wrongPin.value)
    }

    @Test
    fun `verifyPin when no pin was ever set sets wrongPin instead of crashing`() = runTest {
        val repo = FakeSettingsRepository()
        val lockManager = AppLockManager()
        val viewModel = LockViewModel(repo, lockManager)

        val result = viewModel.verifyPin("1234")

        assertFalse(result)
        assertNull(repo.pinHash)
        assertTrue(viewModel.wrongPin.value)
        assertTrue(lockManager.isLocked.value)
    }

    @Test
    fun `verifyPin locks out for 10 minutes after 3 consecutive wrong attempts`() = runTest {
        val repo = FakeSettingsRepository()
        val viewModel = LockViewModel(repo, AppLockManager())
        viewModel.setupPin("1234")

        viewModel.verifyPin("0000")
        viewModel.verifyPin("0000")
        assertEquals(0L, repo.pinLockoutUntil) // not locked out yet after 2 attempts

        val thirdResult = viewModel.verifyPin("0000")

        assertFalse(thirdResult)
        assertTrue(repo.pinLockoutUntil > System.currentTimeMillis())
        assertTrue(repo.pinLockoutUntil <= System.currentTimeMillis() + 10 * 60_000L)
    }

    @Test
    fun `verifyPin rejects even the correct pin while locked out, without resetting the lockout`() = runTest {
        val repo = FakeSettingsRepository()
        val viewModel = LockViewModel(repo, AppLockManager())
        viewModel.setupPin("1234")
        val lockoutUntil = System.currentTimeMillis() + 5 * 60_000L
        repo.pinLockoutUntil = lockoutUntil

        val result = viewModel.verifyPin("1234") // correct pin, but still within the lockout window

        assertFalse(result)
        assertEquals(lockoutUntil, repo.pinLockoutUntil)
    }

    @Test
    fun `a fresh LockViewModel instance honors a lockout persisted before it was created`() = runTest {
        val repo = FakeSettingsRepository()
        val lockoutUntil = System.currentTimeMillis() + 5 * 60_000L
        repo.pinLockoutUntil = lockoutUntil

        val viewModel = LockViewModel(repo, AppLockManager())
        advanceUntilIdle() // let the init block's load of the persisted lockout complete

        assertEquals(lockoutUntil, viewModel.lockoutUntil.value)
    }

    @Test
    fun `onBiometricSuccess unlocks directly without going through the pin path`() {
        val repo = FakeSettingsRepository()
        val lockManager = AppLockManager()
        val viewModel = LockViewModel(repo, lockManager)

        assertTrue(lockManager.isLocked.value)
        viewModel.onBiometricSuccess()

        assertFalse(lockManager.isLocked.value)
        assertFalse(viewModel.wrongPin.value)
    }

    @Test
    fun `setBiometricEnabled forwards to the repository and biometricEnabled reflects it`() = runTest {
        val repo = FakeSettingsRepository()
        val viewModel = LockViewModel(repo, AppLockManager())
        val collector = launch { viewModel.biometricEnabled.collect {} }
        advanceUntilIdle() // let the collector actually subscribe so stateIn's WhileSubscribed sharing starts

        viewModel.setBiometricEnabled(true)
        advanceUntilIdle()

        assertTrue(viewModel.biometricEnabled.value)
        collector.cancel()
    }

    @Test
    fun `hasPin delegates to the repository`() = runTest {
        val repo = FakeSettingsRepository()
        val viewModel = LockViewModel(repo, AppLockManager())

        assertFalse(viewModel.hasPin())
        viewModel.setupPin("9999")
        assertTrue(viewModel.hasPin())
    }
}
