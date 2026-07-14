package com.mobilecontrol.app.ui.widgets

/** Visual/interaction state of a single dashboard widget instance, independent of its type. */
sealed interface WidgetState {
    data object Loading : WidgetState
    data class Live(val value: Any?, val lastChangeMillis: Long) : WidgetState
    data class Stale(val value: Any?, val lastChangeMillis: Long) : WidgetState
    data object Offline : WidgetState
    data object NoPermission : WidgetState
    data object ObjectMissing : WidgetState
    data class CommandPending(val value: Any?) : WidgetState
    data class CommandConfirmed(val value: Any?) : WidgetState
    data class CommandFailed(val value: Any?, val reason: String? = null) : WidgetState
}

/** Value shown is "stale" once older than this, while the connection is otherwise fine. */
const val STALE_THRESHOLD_MS = 5 * 60 * 1000L
