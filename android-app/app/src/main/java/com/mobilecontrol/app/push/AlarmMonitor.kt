package com.mobilecontrol.app.push

import com.mobilecontrol.app.domain.model.LiveValue
import com.mobilecontrol.app.domain.repository.AlarmEventRepository
import com.mobilecontrol.app.domain.repository.AppNotification
import com.mobilecontrol.app.domain.repository.NotificationRepository
import com.mobilecontrol.app.domain.repository.ObjectCatalogRepository
import com.mobilecontrol.app.domain.repository.SettingsRepository
import com.mobilecontrol.app.domain.repository.StateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates alarm notifications for [PushConnectionService]: subscribes to every alarm-role
 * catalog object (suggestedWidgets contains "alarm", same convention the server's suggestWidgets
 * uses) so the shared realtime connection (owned by [StateRepository]) receives their updates
 * regardless of whether a dashboard widget currently displays them, posts a system + in-app
 * notification the moment one goes active, and on start catches up on whatever the backend
 * recorded while this device was disconnected (GET /api/v1/alarm-events?since=, see
 * AlarmEventsService's docs server-side).
 *
 * Deliberately depends only on interfaces (StateRepository/SettingsRepository), not the concrete
 * WebSocket client or DataStore directly, so this stays unit-testable with fakes - it detects a
 * "went active" transition itself by diffing consecutive [StateRepository.liveValues] snapshots,
 * rather than needing the raw WS event stream.
 *
 * Does not open/close the realtime connection itself - that's [StateRepository]'s job, already
 * shared with the rest of the app. This only subscribes to the additional alarm object ids.
 */
@Singleton
class AlarmMonitor @Inject constructor(
    private val objectCatalogRepository: ObjectCatalogRepository,
    private val stateRepository: StateRepository,
    private val alarmEventRepository: AlarmEventRepository,
    private val notificationRepository: NotificationRepository,
    private val settingsRepository: SettingsRepository,
    private val systemNotifier: SystemNotifier,
) {
    private var scope: CoroutineScope? = null

    fun start() {
        if (scope != null) return
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope
        newScope.launch { catchUp() }
        newScope.launch { subscribeAndObserve() }
    }

    fun stop() {
        scope?.cancel()
        scope = null
    }

    /** internal (not private) so tests can call it directly without going through start()'s own
     *  CoroutineScope - see AlarmMonitorTest. */
    internal suspend fun catchUp() {
        val since = settingsRepository.getLastAlarmCatchUpAt()
        val now = System.currentTimeMillis()
        objectCatalogRepository.refreshCatalog()
        val catalog = objectCatalogRepository.observeCatalog().first()
        alarmEventRepository.listSince(since).onSuccess { events ->
            for (event in events) {
                val name = catalog.firstOrNull { it.id == event.objectId }?.name ?: event.objectId
                notify(event.objectId, name, event.timestampMs)
            }
        }
        // Advance the watermark to "now" regardless of fetch success/failure - a failed fetch
        // will simply be retried with the same window next time this runs, rather than silently
        // losing track of what "since" should mean.
        settingsRepository.setLastAlarmCatchUpAt(now)
    }

    internal suspend fun subscribeAndObserve() {
        val catalog = objectCatalogRepository.observeCatalog().first()
        val alarmNames = catalog.filter { "alarm" in it.suggestedWidgets }.associate { it.id to it.name }
        if (alarmNames.isEmpty()) return
        stateRepository.subscribe(alarmNames.keys)

        val lastKnownActive = mutableMapOf<String, Boolean>()
        stateRepository.liveValues.collect { values ->
            for (alarm in newlyActiveAlarms(alarmNames, values, lastKnownActive)) {
                notify(alarm.objectId, alarm.name, alarm.timestampMs)
            }
        }
    }

    private fun notify(objectId: String, name: String, timestampMs: Long) {
        val title = "Alarm: $name"
        val body = "$name ist aktiv geworden."
        notificationRepository.push(
            AppNotification(
                id = UUID.randomUUID().toString(),
                title = title,
                body = body,
                timestamp = timestampMs,
                severity = AppNotification.Severity.ERROR,
                read = false,
            ),
        )
        systemNotifier.notifyAlarm(objectId, title, body, timestampMs)
    }
}

internal data class NewlyActiveAlarm(val objectId: String, val name: String, val timestampMs: Long)

/**
 * Pure "which alarms just went active" decision, extracted out of [AlarmMonitor.subscribeAndObserve]
 * purely for unit-testability (no Flow/coroutines involved). [lastKnownActive] is mutated in place
 * to record the new state for the next call, same as a fold's accumulator.
 */
internal fun newlyActiveAlarms(
    alarmNames: Map<String, String>,
    values: Map<String, LiveValue>,
    lastKnownActive: MutableMap<String, Boolean>,
): List<NewlyActiveAlarm> {
    val result = mutableListOf<NewlyActiveAlarm>()
    for ((objectId, name) in alarmNames) {
        val isActive = values[objectId]?.value == true
        val wasActive = lastKnownActive[objectId] == true
        if (isActive && !wasActive) {
            result.add(NewlyActiveAlarm(objectId, name, values[objectId]?.lastChange ?: System.currentTimeMillis()))
        }
        lastKnownActive[objectId] = isActive
    }
    return result
}
