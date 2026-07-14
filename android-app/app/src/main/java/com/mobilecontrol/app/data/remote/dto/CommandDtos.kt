package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CommandRequestDto(
    val commandId: String,
    val objectId: String,
    val value: JsonElement?,
    val timestamp: String,
    val nonce: String,
)

@Serializable
data class CommandResponseDto(
    val status: String,
)
