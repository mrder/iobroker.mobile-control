package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.domain.repository.AppNotification
import com.mobilecontrol.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepositoryImpl @Inject constructor() : NotificationRepository {

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    override val notifications: StateFlow<List<AppNotification>> = _notifications

    override fun push(notification: AppNotification) {
        _notifications.value = listOf(notification) + _notifications.value.take(MAX_ENTRIES - 1)
    }

    override fun markRead(id: String) {
        _notifications.value = _notifications.value.map { if (it.id == id) it.copy(read = true) else it }
    }

    override fun clearAll() {
        _notifications.value = emptyList()
    }

    private companion object {
        const val MAX_ENTRIES = 100
    }
}
