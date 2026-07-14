package com.mobilecontrol.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class LogEntry(
    val timestamp: Long,
    val level: Level,
    val message: String,
) {
    enum class Level { INFO, WARN, ERROR }
}

interface DiagnosticsRepository {
    val recentLogs: StateFlow<List<LogEntry>>
    /** [message] must never contain tokens, secrets, or raw signatures - callers are responsible for redaction. */
    fun log(level: LogEntry.Level, message: String)
    fun clear()
}
