package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Outgoing

@Serializable
data class WsSubscribeDto(
    val type: String = "subscribe",
    val objectIds: List<String>,
)

@Serializable
data class WsUnsubscribeDto(
    val type: String = "unsubscribe",
    val objectIds: List<String>,
)

// Incoming - parsed in two steps: first read `type` generically, then decode the specific shape.

@Serializable
data class WsEnvelopeDto(
    val type: String,
)

@Serializable
data class WsStateUpdateDto(
    val type: String = "state_update",
    val objectId: String,
    val value: JsonElement? = null,
    val timestamp: String? = null,
    val lastChange: String? = null,
    val ack: Boolean = true,
)

@Serializable
data class WsCommandResultDto(
    val type: String = "command_result",
    val commandId: String,
    val status: String,
)

@Serializable
data class WsSessionRevokedDto(
    val type: String = "session_revoked",
    val reason: String? = null,
)

@Serializable
data class WsPermissionsChangedDto(
    val type: String = "permissions_changed",
)
