package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.domain.repository.DiagnosticsRepository
import com.mobilecontrol.app.domain.repository.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticsRepositoryImpl @Inject constructor() : DiagnosticsRepository {

    private val _recentLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    override val recentLogs: StateFlow<List<LogEntry>> = _recentLogs

    override fun log(level: LogEntry.Level, message: String) {
        val redacted = redact(message)
        val updated = (_recentLogs.value + LogEntry(System.currentTimeMillis(), level, redacted)).takeLast(MAX_ENTRIES)
        _recentLogs.value = updated
    }

    override fun clear() {
        _recentLogs.value = emptyList()
    }

    /** Defense in depth: strip anything that looks like a bearer token or long opaque secret, even though
     * callers are expected to never pass raw tokens/signatures into [log] in the first place. */
    private fun redact(message: String): String =
        message
            .replace(Regex("Bearer\\s+[A-Za-z0-9\\-_.]+"), "Bearer [redacted]")
            .replace(Regex("[A-Za-z0-9+/_=\\-]{32,}"), "[redacted]")

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
