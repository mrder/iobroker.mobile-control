package com.mobilecontrol.app.ui.widgets

import androidx.lifecycle.ViewModel
import com.mobilecontrol.app.domain.repository.AppNotification
import com.mobilecontrol.app.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Thin DI shim so [AlarmWidget] (a plain @Composable, not tied to a screen-level ViewModel) can
 * reach [NotificationRepository] via `hiltViewModel()` - same pattern as HistoryWidgetViewModel.
 * Unlike that one, this DOES hold mutable state (per-objectId last-known-active flag and the
 * acknowledged set), but that's still safe to share across every AlarmWidget instance on a
 * dashboard: the state is keyed by objectId, so multiple alarm widgets never collide.
 */
@HiltViewModel
class AlarmWidgetViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : ViewModel() {

    private val lastKnownActive = mutableMapOf<String, Boolean>()

    private val _acknowledged = MutableStateFlow<Set<String>>(emptySet())
    val acknowledged: StateFlow<Set<String>> = _acknowledged

    /**
     * Called on every recomposition where the widget has a live/stale value. Pushes an in-app
     * notification only on an actual true/false *transition* - "Entwarnung" (active -> inactive)
     * as an INFO notification, a new alarm (inactive -> active, or first-ever observation of an
     * already-active alarm) as an ERROR notification. The very first observation while already
     * active does NOT count as a "new" transition (nothing to compare against yet), but does
     * clear any stale acknowledgement from a previous app session.
     */
    fun observe(objectId: String, title: String, active: Boolean) {
        val previous = lastKnownActive.put(objectId, active)
        if (previous == active) return

        if (active) {
            _acknowledged.update { it - objectId }
        }
        if (previous == null) return // first observation this session - not a real transition

        val notification = if (active) {
            AppNotification(
                id = UUID.randomUUID().toString(),
                title = "Alarm aktiv",
                body = title,
                timestamp = System.currentTimeMillis(),
                severity = AppNotification.Severity.ERROR,
                read = false,
            )
        } else {
            AppNotification(
                id = UUID.randomUUID().toString(),
                title = "Entwarnung",
                body = title,
                timestamp = System.currentTimeMillis(),
                severity = AppNotification.Severity.INFO,
                read = false,
            )
        }
        notificationRepository.push(notification)
    }

    /** "Quittieren": mutes the widget's alert styling while the alarm is still active. Purely
     * local UI state - not every alarm source has a companion server-side ack state, so this
     * does not attempt to write anything back to the server (see android-app/README.md). */
    fun acknowledge(objectId: String) {
        _acknowledged.update { it + objectId }
    }
}
