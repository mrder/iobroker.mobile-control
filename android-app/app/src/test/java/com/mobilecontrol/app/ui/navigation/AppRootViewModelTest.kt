package com.mobilecontrol.app.ui.navigation

import com.mobilecontrol.app.data.remote.RevocationNotifier
import com.mobilecontrol.app.domain.model.Dashboard
import com.mobilecontrol.app.domain.model.DashboardLayout
import com.mobilecontrol.app.domain.model.DeviceProfile
import com.mobilecontrol.app.domain.model.Session
import com.mobilecontrol.app.domain.model.SizeClass
import com.mobilecontrol.app.domain.model.ThemeMode
import com.mobilecontrol.app.domain.repository.AuthRepository
import com.mobilecontrol.app.domain.repository.ConnectionState
import com.mobilecontrol.app.domain.repository.DashboardRepository
import com.mobilecontrol.app.domain.repository.SettingsRepository
import com.mobilecontrol.app.domain.repository.StateRepository
import com.mobilecontrol.app.domain.model.LiveValue
import com.mobilecontrol.app.push.PushServiceController
import com.mobilecontrol.app.ui.lock.AppLockManager
import com.mobilecontrol.app.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun testDashboard(id: String): Dashboard = Dashboard(
    id = id,
    name = id,
    revision = 0,
    layouts = SizeClass.entries.map { DashboardLayout(it, it.defaultColumns, emptyList()) },
)

private class FakeSettingsRepository : SettingsRepository {
    val deviceProfile = MutableStateFlow<DeviceProfile?>(null)
    val appLockEnabled = MutableStateFlow(true)
    val pushNotificationsEnabled = MutableStateFlow(false)
    var pinHash: String? = null

    override fun observeDeviceProfile(): Flow<DeviceProfile?> = deviceProfile
    override suspend fun saveDeviceProfile(profile: DeviceProfile) { deviceProfile.value = profile }
    override suspend fun clearDeviceProfile() { deviceProfile.value = null }

    override fun observeAppLockEnabled(): Flow<Boolean> = appLockEnabled
    override suspend fun setAppLockEnabled(enabled: Boolean) { appLockEnabled.value = enabled }

    override fun observeBiometricEnabled(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setBiometricEnabled(enabled: Boolean) {}

    override suspend fun setPinHash(hash: String) { pinHash = hash }
    override suspend fun getPinHash(): String? = pinHash
    override suspend fun hasPin(): Boolean = pinHash != null

    override suspend fun clearCache() {}

    override fun observeLastConnectionAt(): Flow<Long?> = MutableStateFlow(null)
    override suspend fun setLastConnectionAt(epochMillis: Long) {}

    override fun observeThemeMode(): Flow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)
    override suspend fun setThemeMode(mode: ThemeMode) {}

    override fun observePushNotificationsEnabled(): Flow<Boolean> = pushNotificationsEnabled
    override suspend fun setPushNotificationsEnabled(enabled: Boolean) { pushNotificationsEnabled.value = enabled }

    override suspend fun getLastAlarmCatchUpAt(): Long = 0L
    override suspend fun setLastAlarmCatchUpAt(epochMillis: Long) {}
}

private class FakePushServiceController : PushServiceController {
    var startCalls = 0
    var stopCalls = 0
    override fun start() { startCalls++ }
    override fun stop() { stopCalls++ }
}

private class FakeAuthRepository : AuthRepository {
    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session
    fun setSession(session: Session?) { _session.value = session }

    var revocationHandled = false

    override suspend fun login(deviceId: String): Result<Session> = Result.failure(UnsupportedOperationException())
    override suspend fun refresh(): Result<Session> = Result.failure(UnsupportedOperationException())
    override suspend fun adoptSession(deviceId: String, accessToken: String, refreshToken: String, expiresIn: Long) {}
    override suspend fun logout(notifyServer: Boolean) { _session.value = null }
    override suspend fun handleRevocation() { revocationHandled = true }
}

private class FakeStateRepository : StateRepository {
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val liveValues: StateFlow<Map<String, LiveValue>> = MutableStateFlow(emptyMap())
    var connectCalls = 0

    override suspend fun fetchInitialStates(objectIds: List<String>): Result<Unit> = Result.success(Unit)
    override fun subscribe(objectIds: Set<String>) {}
    override fun unsubscribe(objectIds: Set<String>) {}
    override fun connect() { connectCalls++ }
    override fun disconnect() {}
}

private class FakeDashboardRepository(
    private val dashboards: List<Dashboard> = emptyList(),
    private var startDashboardId: String? = null,
) : DashboardRepository {
    override fun observeDashboards(): Flow<List<Dashboard>> = MutableStateFlow(dashboards)
    override suspend fun refreshDashboards(): Result<Unit> = Result.success(Unit)
    override suspend fun getDashboard(id: String): Dashboard? = dashboards.firstOrNull { it.id == id }
    override suspend fun createDashboard(dashboard: Dashboard): Result<Dashboard> = Result.success(dashboard)
    override suspend fun updateDashboard(dashboard: Dashboard): Result<Dashboard> = Result.success(dashboard)
    override suspend fun deleteDashboard(id: String): Result<Unit> = Result.success(Unit)
    override suspend fun setStartDashboard(id: String) { startDashboardId = id }
    override suspend fun getStartDashboardId(): String? = startDashboardId
}

private fun session() = Session("dev-1", "access", "refresh", Long.MAX_VALUE, "user-1", "Alice")
private fun profile() = DeviceProfile("dev-1", "Pixel", "inst-1", "https://iobroker.local", "fp", 0L)

class AppRootViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        authRepo: FakeAuthRepository = FakeAuthRepository(),
        stateRepo: FakeStateRepository = FakeStateRepository(),
        dashboardRepo: FakeDashboardRepository = FakeDashboardRepository(),
        pushServiceController: FakePushServiceController = FakePushServiceController(),
    ) = AppRootViewModel(settingsRepo, authRepo, stateRepo, dashboardRepo, pushServiceController, AppLockManager(), RevocationNotifier())

    @Test
    fun `resolveStartDestination is ONBOARDING when no device is paired yet`() = runTest {
        val vm = viewModel()

        assertEquals(StartDestination.ONBOARDING, vm.resolveStartDestination())
    }

    @Test
    fun `resolveStartDestination is ONBOARDING when a profile exists but the session was cleared`() = runTest {
        val settingsRepo = FakeSettingsRepository().apply { deviceProfile.value = profile() }
        val vm = viewModel(settingsRepo = settingsRepo) // authRepo session stays null

        assertEquals(StartDestination.ONBOARDING, vm.resolveStartDestination())
    }

    @Test
    fun `resolveStartDestination unlocks and returns START when app lock is disabled`() = runTest {
        val settingsRepo = FakeSettingsRepository().apply {
            deviceProfile.value = profile()
            appLockEnabled.value = false
        }
        val authRepo = FakeAuthRepository().apply { setSession(session()) }
        val vm = viewModel(settingsRepo = settingsRepo, authRepo = authRepo)

        val destination = vm.resolveStartDestination()

        assertEquals(StartDestination.START, destination)
        val collector = launch { vm.isLocked.collect {} }
        advanceUntilIdle()
        assertFalse(vm.isLocked.value)
        collector.cancel()
    }

    @Test
    fun `resolveStartDestination is LOCK_VERIFY when app lock is on and a pin already exists`() = runTest {
        val settingsRepo = FakeSettingsRepository().apply {
            deviceProfile.value = profile()
            appLockEnabled.value = true
            pinHash = "existing-hash"
        }
        val authRepo = FakeAuthRepository().apply { setSession(session()) }
        val vm = viewModel(settingsRepo = settingsRepo, authRepo = authRepo)

        assertEquals(StartDestination.LOCK_VERIFY, vm.resolveStartDestination())
    }

    @Test
    fun `resolveStartDestination is LOCK_SETUP when app lock is on and no pin exists yet`() = runTest {
        val settingsRepo = FakeSettingsRepository().apply {
            deviceProfile.value = profile()
            appLockEnabled.value = true
        }
        val authRepo = FakeAuthRepository().apply { setSession(session()) }
        val vm = viewModel(settingsRepo = settingsRepo, authRepo = authRepo)

        assertEquals(StartDestination.LOCK_SETUP, vm.resolveStartDestination())
    }

    @Test
    fun `connectRealtime delegates straight to the state repository`() {
        val stateRepo = FakeStateRepository()
        val vm = viewModel(stateRepo = stateRepo)

        vm.connectRealtime()

        assertEquals(1, stateRepo.connectCalls)
    }

    @Test
    fun `handleRevocation clears the session server-side and re-locks the app`() = runTest {
        val authRepo = FakeAuthRepository().apply { setSession(session()) }
        val vm = viewModel(authRepo = authRepo)
        vm.appLockManager.unlock()

        vm.handleRevocation()
        advanceUntilIdle()

        assertTrue(authRepo.revocationHandled)
        assertTrue(vm.appLockManager.isLocked.value)
    }

    @Test
    fun `ensurePushServiceMatchesSetting starts the service when push notifications were previously enabled`() = runTest {
        val settingsRepo = FakeSettingsRepository().apply { pushNotificationsEnabled.value = true }
        val pushController = FakePushServiceController()
        val vm = viewModel(settingsRepo = settingsRepo, pushServiceController = pushController)

        vm.ensurePushServiceMatchesSetting()

        assertEquals(1, pushController.startCalls)
        assertEquals(0, pushController.stopCalls)
    }

    @Test
    fun `ensurePushServiceMatchesSetting does nothing when push notifications are disabled`() = runTest {
        val pushController = FakePushServiceController()
        val vm = viewModel(pushServiceController = pushController) // default: disabled

        vm.ensurePushServiceMatchesSetting()

        assertEquals(0, pushController.startCalls)
        assertEquals(0, pushController.stopCalls)
    }

    @Test
    fun `startDashboardId prefers the explicit start dashboard, falling back to the first dashboard otherwise`() = runTest {
        val withStart = viewModel(dashboardRepo = FakeDashboardRepository(listOf(testDashboard("a"), testDashboard("b")), startDashboardId = "b"))
        assertEquals("b", withStart.startDashboardId())

        val withoutStart = viewModel(dashboardRepo = FakeDashboardRepository(listOf(testDashboard("a"), testDashboard("b")), startDashboardId = null))
        assertEquals("a", withoutStart.startDashboardId())
    }
}
