package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StatesResponseDto(
    // Server sends `null` for object ids it can't resolve (e.g. stale/unauthorized UUID) -
    // the value type MUST be nullable or decoding the whole response throws.
    val states: Map<String, StateValueDto?> = emptyMap(),
)

@Serializable
data class StateValueDto(
    val value: JsonElement? = null,
    val timestamp: String? = null,
    val lastChange: String? = null,
    val ack: Boolean = true,
)
