package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Outgoing

/** Must be the first message sent after the socket opens - the server (RealtimeGateway.handleAuth)
 *  does not read any query parameter on the upgrade request, only this message; it force-closes the
 *  connection after AUTH_TIMEOUT_MS (5s) if it never arrives. timestamp/nonce/signature mirror the
 *  same per-request signature every REST call carries (see RequestSigningInterceptor) - signature
 *  is over "WS\n/ws/v1\nTIMESTAMP\nNONCE\nSHA256(accessToken)", verified against this device's
 *  Keystore public key just like a REST request's body. */
@Serializable
data class WsAuthDto(
    val type: String = "auth",
    val accessToken: String,
    val timestamp: String,
    val nonce: String,
    val signature: String,
)

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
