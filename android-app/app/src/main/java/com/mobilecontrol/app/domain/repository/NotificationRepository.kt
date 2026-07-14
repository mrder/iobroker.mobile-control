package com.mobilecontrol.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val timestamp: Long,
    val severity: Severity,
    val read: Boolean,
) {
    enum class Severity { INFO, WARNING, ERROR }
}

interface NotificationRepository {
    val notifications: StateFlow<List<AppNotification>>
    fun push(notification: AppNotification)
    fun markRead(id: String)
    fun clearAll()
}
