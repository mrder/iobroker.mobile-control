package com.mobilecontrol.app.ui.notifications

import com.mobilecontrol.app.domain.repository.AppNotification
import com.mobilecontrol.app.domain.repository.NotificationRepository
import com.mobilecontrol.app.util.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun notification(id: String, read: Boolean = false): AppNotification = AppNotification(
    id = id,
    title = "Titel $id",
    body = "Body $id",
    timestamp = 0L,
    severity = AppNotification.Severity.INFO,
    read = read,
)

private class FakeNotificationRepository(seed: List<AppNotification> = emptyList()) : NotificationRepository {
    private val _notifications = MutableStateFlow(seed)
    override val notifications: StateFlow<List<AppNotification>> = _notifications

    val markReadCalls = mutableListOf<String>()
    var clearAllCalls = 0

    override fun push(notification: AppNotification) {
        _notifications.value = listOf(notification) + _notifications.value
    }

    override fun markRead(id: String) {
        markReadCalls.add(id)
        _notifications.value = _notifications.value.map { if (it.id == id) it.copy(read = true) else it }
    }

    override fun clearAll() {
        clearAllCalls++
        _notifications.value = emptyList()
    }
}

class NotificationsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `notifications exposes the repository's live list directly, without needing an active collector`() {
        val repo = FakeNotificationRepository(listOf(notification("a"), notification("b")))
        val viewModel = NotificationsViewModel(repo)

        assertEquals(listOf("a", "b"), viewModel.notifications.value.map { it.id })
    }

    @Test
    fun `markRead marks only the targeted notification, leaving others untouched`() = runTest {
        val repo = FakeNotificationRepository(listOf(notification("a"), notification("b")))
        val viewModel = NotificationsViewModel(repo)

        viewModel.markRead("a")
        advanceUntilIdle()

        assertEquals(listOf("a"), repo.markReadCalls)
        val byId = viewModel.notifications.value.associateBy { it.id }
        assertTrue(byId.getValue("a").read)
        assertTrue(!byId.getValue("b").read)
    }

    @Test
    fun `clearAll empties the notification list via the repository`() = runTest {
        val repo = FakeNotificationRepository(listOf(notification("a")))
        val viewModel = NotificationsViewModel(repo)

        viewModel.clearAll()
        advanceUntilIdle()

        assertEquals(1, repo.clearAllCalls)
        assertTrue(viewModel.notifications.value.isEmpty())
    }
}
