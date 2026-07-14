package com.mobilecontrol.app.data.remote

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

fun parseIsoToEpochMillis(value: String?, fallback: Long = System.currentTimeMillis()): Long {
    if (value.isNullOrBlank()) return fallback
    return try {
        Instant.parse(value).toEpochMilli()
    } catch (_: DateTimeParseException) {
        try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            fallback
        }
    }
}
