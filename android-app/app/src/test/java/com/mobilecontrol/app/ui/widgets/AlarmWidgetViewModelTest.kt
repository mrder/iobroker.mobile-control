package com.mobilecontrol.app.ui.widgets

import com.mobilecontrol.app.domain.repository.AppNotification
import com.mobilecontrol.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeNotificationRepository : NotificationRepository {
    val pushed = mutableListOf<AppNotification>()
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = _notifications

    override fun push(notification: AppNotification) {
        pushed.add(notification)
        _notifications.value = listOf(notification) + _notifications.value
    }

    override fun markRead(id: String) {
        _notifications.value = _notifications.value.map { if (it.id == id) it.copy(read = true) else it }
    }

    override fun clearAll() {
        _notifications.value = emptyList()
    }
}

class AlarmWidgetViewModelTest {

    @Test
    fun `the first observation of an inactive alarm pushes no notification`() {
        val repo = FakeNotificationRepository()
        val viewModel = AlarmWidgetViewModel(repo)

        viewModel.observe("obj1", "Rauchmelder", active = false)

        assertTrue(repo.pushed.isEmpty())
    }

    @Test
    fun `the first observation of an ALREADY active alarm does not push (not a transition)`() {
        val repo = FakeNotificationRepository()
        val viewModel = AlarmWidgetViewModel(repo)

        viewModel.observe("obj1", "Rauchmelder", active = true)

        assertTrue(repo.pushed.isEmpty())
    }

    @Test
    fun `a false-to-true transition pushes an ERROR notification`() {
        val repo = FakeNotificationRepository()
        val viewModel = AlarmWidgetViewModel(repo)

        viewModel.observe("obj1", "Rauchmelder", active = false)
        viewModel.observe("obj1", "Rauchmelder", active = true)

        assertEquals(1, repo.pushed.size)
        assertEquals(AppNotification.Severity.ERROR, repo.pushed[0].severity)
        assertEquals("Alarm aktiv", repo.pushed[0].title)
    }

    @Test
    fun `a true-to-false transition pushes an INFO Entwarnung notification`() {
        val repo = FakeNotificationRepository()
        val viewModel = AlarmWidgetViewModel(repo)

        viewModel.observe("obj1", "Rauchmelder", active = true)
        viewModel.observe("obj1", "Rauchmelder", active = false)

        assertEquals(1, repo.pushed.size)
        assertEquals(AppNotification.Severity.INFO, repo.pushed[0].severity)
        assertEquals("Entwarnung", repo.pushed[0].title)
    }

    @Test
    fun `repeated observations with no change push nothing more`() {
        val repo = FakeNotificationRepository()
        val viewModel = AlarmWidgetViewModel(repo)

        viewModel.observe("obj1", "Rauchmelder", active = true)
        viewModel.observe("obj1", "Rauchmelder", active = true)
        viewModel.observe("obj1", "Rauchmelder", active = true)

        assertTrue(repo.pushed.isEmpty())
    }

    @Test
    fun `acknowledge mutes the alarm until the next new alarm event`() {
        val repo = FakeNotificationRepository()
        val viewModel = AlarmWidgetViewModel(repo)

        viewModel.observe("obj1", "Rauchmelder", active = true)
        viewModel.acknowledge("obj1")
        assertTrue("obj1" in viewModel.acknowledged.value)

        // a fresh alarm (cleared, then triggered again) resets the acknowledgement
        viewModel.observe("obj1", "Rauchmelder", active = false)
        viewModel.observe("obj1", "Rauchmelder", active = true)
        assertFalse("obj1" in viewModel.acknowledged.value)
    }

    @Test
    fun `alarms for different objectIds do not interfere with each other`() {
        val repo = FakeNotificationRepository()
        val viewModel = AlarmWidgetViewModel(repo)

        viewModel.observe("smoke", "Rauchmelder", active = false)
        viewModel.observe("water", "Wassermelder", active = false)
        viewModel.observe("smoke", "Rauchmelder", active = true)

        assertEquals(1, repo.pushed.size)
        assertEquals("Rauchmelder", repo.pushed[0].body)
    }
}
