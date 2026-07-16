package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HistoryResponseDto(
    val entries: List<HistoryEntryDto> = emptyList(),
)

@Serializable
data class HistoryEntryDto(
    // Same arbitrary-JSON-scalar shape as StateValueDto.value - see JsonValueConversion.kt.
    val value: JsonElement? = null,
    val timestamp: String,
)
