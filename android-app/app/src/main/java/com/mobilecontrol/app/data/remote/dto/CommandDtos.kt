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
    /** Set true after the user completed a DIALOG/BIOMETRIC/REAUTHENTICATE confirmation client-side. */
    val confirmed: Boolean? = null,
)

@Serializable
data class CommandResponseDto(
    val status: String,
)
