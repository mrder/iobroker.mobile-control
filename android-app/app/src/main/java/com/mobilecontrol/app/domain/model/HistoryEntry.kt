package com.mobilecontrol.app.domain.model

/** A single historized value sample for an object, as returned by GET /api/v1/history. */
data class HistoryEntry(
    val value: Any?,
    val timestampMillis: Long,
)
