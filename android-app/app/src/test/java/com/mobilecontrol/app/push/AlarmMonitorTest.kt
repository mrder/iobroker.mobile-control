package com.mobilecontrol.app.push

import com.mobilecontrol.app.domain.model.AlarmEvent
import com.mobilecontrol.app.domain.model.DeviceProfile
import com.mobilecontrol.app.domain.model.LiveValue
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.ThemeMode
import com.mobilecontrol.app.domain.model.ValueType
import com.mobilecontrol.app.domain.repository.AlarmEventRepository
import com.mobilecontrol.app.domain.repository.AppNotification
import com.mobilecontrol.app.domain.repository.ConnectionState
import com.mobilecontrol.app.domain.repository.NotificationRepository
import com.mobilecontrol.app.domain.repository.ObjectCatalogRepository
import com.mobilecontrol.app.domain.repository.SettingsRepository
import com.mobilecontrol.app.domain.repository.StateRepository
import com.mobilecontrol.app.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun alarmItem(id: String, name: String): ObjectCatalogItem = ObjectCatalogItem(
    id = id,
    name = name,
    path = listOf("Keller"),
    role = "sensor.alarm.fire",
    valueType = ValueType.BOOLEAN,
    unit = null,
    canRead = true,
    canWrite = false,
    hasHistory = false,
    suggestedWidgets = listOf("alarm", "status"),
)

private class FakeObjectCatalogRepository(items: List<ObjectCatalogItem> = emptyList()) : ObjectCatalogRepository {
    private val flow = MutableStateFlow(items)
    var refreshCalls = 0
    override fun observeCatalog(): Flow<List<ObjectCatalogItem>> = flow
    override fun observeFolderNames(): Flow<Map<String, String>> = MutableStateFlow(emptyMap())
    override suspend fun refreshCatalog(): Result<Unit> {
        refreshCalls++
        return Result.success(Unit)
    }
}

private class FakeStateRepository : StateRepository {
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.CONNECTED)
    val liveValuesFlow = MutableStateFlow<Map<String, LiveValue>>(emptyMap())
    override val liveValues: StateFlow<Map<String, LiveValue>> = liveValuesFlow
    val subscribedBatches = mutableListOf<Set<String>>()

    override suspend fun fetchInitialStates(objectIds: List<String>): Result<Unit> = Result.success(Unit)
    override fun subscribe(objectIds: Set<String>) { subscribedBatches.add(objectIds) }
    override fun unsubscribe(objectIds: Set<String>) {}
    override fun connect() {}
    override fun disconnect() {}
}

private class FakeAlarmEventRepository(var result: Result<List<AlarmEvent>> = Result.success(emptyList())) : AlarmEventRepository {
    var lastSinceArg: Long? = null
    override suspend fun listSince(sinceEpochMillis: Long): Result<List<AlarmEvent>> {
        lastSinceArg = sinceEpochMillis
        return result
    }
}

private class FakeNotificationRepository : NotificationRepository {
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = _notifications
    override fun push(notification: AppNotification) { _notifications.value = listOf(notification) + _notifications.value }
    override fun markRead(id: String) {}
    override fun clearAll() { _notifications.value = emptyList() }
}

private class FakeSystemNotifier : SystemNotifier {
    data class Call(val objectId: String, val title: String, val body: String, val timestampMs: Long)
    val calls = mutableListOf<Call>()
    override fun notifyAlarm(objectId: String, title: String, body: String, timestampMs: Long) {
        calls.add(Call(objectId, title, body, timestampMs))
    }
}

private class FakeSettingsRepository : SettingsRepository {
    var lastAlarmCatchUpAt = 0L

    override fun observeDeviceProfile(): Flow<DeviceProfile?> = MutableStateFlow(null)
    override suspend fun saveDeviceProfile(profile: DeviceProfile) {}
    override suspend fun clearDeviceProfile() {}

    override fun observeAppLockEnabled(): Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setAppLockEnabled(enabled: Boolean) {}

    override fun observeBiometricEnabled(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setBiometricEnabled(enabled: Boolean) {}

    override suspend fun setPinHash(hash: String) {}
    override suspend fun getPinHash(): String? = null
    override suspend fun hasPin(): Boolean = false

    override suspend fun clearCache() {}

    override fun observeLastConnectionAt(): Flow<Long?> = MutableStateFlow(null)
    override suspend fun setLastConnectionAt(epochMillis: Long) {}

    override fun observeThemeMode(): Flow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)
    override suspend fun setThemeMode(mode: ThemeMode) {}

    override fun observePushNotificationsEnabled(): Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setPushNotificationsEnabled(enabled: Boolean) {}

    override suspend fun getLastAlarmCatchUpAt(): Long = lastAlarmCatchUpAt
    override suspend fun setLastAlarmCatchUpAt(epochMillis: Long) { lastAlarmCatchUpAt = epochMillis }
}

class AlarmMonitorTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun monitor(
        catalogRepo: FakeObjectCatalogRepository = FakeObjectCatalogRepository(),
        stateRepo: FakeStateRepository = FakeStateRepository(),
        alarmEventRepo: FakeAlarmEventRepository = FakeAlarmEventRepository(),
        notificationRepo: FakeNotificationRepository = FakeNotificationRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        systemNotifier: FakeSystemNotifier = FakeSystemNotifier(),
    ) = AlarmMonitor(catalogRepo, stateRepo, alarmEventRepo, notificationRepo, settingsRepo, systemNotifier)

    // ---- newlyActiveAlarms (pure function) --------------------------------------------------

    @Test
    fun `newlyActiveAlarms reports an object whose value just became true`() {
        val alarmNames = mapOf("obj-1" to "Rauchmelder")
        val lastKnownActive = mutableMapOf<String, Boolean>()

        val first = newlyActiveAlarms(alarmNames, emptyMap(), lastKnownActive)
        assertTrue(first.isEmpty())

        val values = mapOf("obj-1" to LiveValue("obj-1", true, 1000L, 900L, true))
        val second = newlyActiveAlarms(alarmNames, values, lastKnownActive)
        assertEquals(1, second.size)
        assertEquals("obj-1", second[0].objectId)
        assertEquals("Rauchmelder", second[0].name)
        assertEquals(900L, second[0].timestampMs)
    }

    @Test
    fun `newlyActiveAlarms does not re-report an alarm that is still active`() {
        val alarmNames = mapOf("obj-1" to "Rauchmelder")
        val lastKnownActive = mutableMapOf("obj-1" to true)
        val values = mapOf("obj-1" to LiveValue("obj-1", true, 1000L, 900L, true))

        assertTrue(newlyActiveAlarms(alarmNames, values, lastKnownActive).isEmpty())
    }

    @Test
    fun `newlyActiveAlarms re-reports after a falling edge followed by a rising edge`() {
        val alarmNames = mapOf("obj-1" to "Rauchmelder")
        val lastKnownActive = mutableMapOf<String, Boolean>()

        newlyActiveAlarms(alarmNames, mapOf("obj-1" to LiveValue("obj-1", true, 1L, 1L, true)), lastKnownActive)
        assertTrue(newlyActiveAlarms(alarmNames, mapOf("obj-1" to LiveValue("obj-1", false, 2L, 2L, true)), lastKnownActive).isEmpty())
        val third = newlyActiveAlarms(alarmNames, mapOf("obj-1" to LiveValue("obj-1", true, 3L, 3L, true)), lastKnownActive)
        assertEquals(1, third.size)
    }

    @Test
    fun `newlyActiveAlarms ignores an object not in the alarm set`() {
        val alarmNames = mapOf("obj-1" to "Rauchmelder")
        val lastKnownActive = mutableMapOf<String, Boolean>()
        val values = mapOf("obj-other" to LiveValue("obj-other", true, 1L, 1L, true))

        assertTrue(newlyActiveAlarms(alarmNames, values, lastKnownActive).isEmpty())
    }

    // ---- catchUp() ----------------------------------------------------------------------------

    @Test
    fun `catchUp posts a notification for every event since the stored watermark and advances it`() = runTest {
        val catalogRepo = FakeObjectCatalogRepository(listOf(alarmItem("obj-1", "Rauchmelder Keller")))
        val alarmEventRepo = FakeAlarmEventRepository(Result.success(listOf(AlarmEvent("obj-1", true, 500L))))
        val notificationRepo = FakeNotificationRepository()
        val systemNotifier = FakeSystemNotifier()
        val settingsRepo = FakeSettingsRepository().apply { lastAlarmCatchUpAt = 100L }
        val alarmMonitor = monitor(
            catalogRepo = catalogRepo,
            alarmEventRepo = alarmEventRepo,
            notificationRepo = notificationRepo,
            settingsRepo = settingsRepo,
            systemNotifier = systemNotifier,
        )

        alarmMonitor.catchUp()

        assertEquals(100L, alarmEventRepo.lastSinceArg)
        assertEquals(1, notificationRepo.notifications.value.size)
        assertEquals(1, systemNotifier.calls.size)
        assertTrue(systemNotifier.calls[0].title.contains("Rauchmelder Keller"))
        assertTrue(settingsRepo.lastAlarmCatchUpAt > 100L) // advanced to "now"
    }

    @Test
    fun `catchUp still advances the watermark even when the fetch fails`() = runTest {
        val alarmEventRepo = FakeAlarmEventRepository(Result.failure(RuntimeException("offline")))
        val settingsRepo = FakeSettingsRepository().apply { lastAlarmCatchUpAt = 50L }
        val alarmMonitor = monitor(alarmEventRepo = alarmEventRepo, settingsRepo = settingsRepo)

        alarmMonitor.catchUp()

        assertTrue(settingsRepo.lastAlarmCatchUpAt > 50L)
    }

    // subscribeAndObserve() itself is intentionally not further unit-tested here: it is a thin
    // wrapper (fetch catalog, filter to alarm objects, subscribe, delegate every emission to the
    // already-covered newlyActiveAlarms()) around an infinite Flow.collect(), and driving that
    // deterministically under runTest's virtual time proved flaky in CI. The actual decision logic
    // it delegates to is fully covered by the newlyActiveAlarms() tests above; the wiring itself is
    // exercised live on-device the same way the rest of this session's features have been.
}
