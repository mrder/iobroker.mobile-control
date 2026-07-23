package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AlarmEventDto(
    val objectId: String,
    val value: Boolean,
    val timestamp: String,
)

@Serializable
data class AlarmEventListResponseDto(
    val events: List<AlarmEventDto> = emptyList(),
)
