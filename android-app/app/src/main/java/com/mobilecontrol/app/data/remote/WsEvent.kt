package com.mobilecontrol.app.data.remote

import kotlinx.serialization.json.JsonElement

sealed interface WsEvent {
    data class StateUpdate(
        val objectId: String,
        val value: JsonElement?,
        val timestamp: String?,
        val lastChange: String?,
        val ack: Boolean,
    ) : WsEvent

    data class CommandResult(val commandId: String, val status: String) : WsEvent
    data class SessionRevoked(val reason: String?) : WsEvent
    data object PermissionsChanged : WsEvent
    data object Heartbeat : WsEvent

    data object Connected : WsEvent
    data class Disconnected(val willReconnect: Boolean) : WsEvent
}
