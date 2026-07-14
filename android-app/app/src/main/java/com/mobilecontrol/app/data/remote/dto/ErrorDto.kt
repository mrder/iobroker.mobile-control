package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable

/** Expected shape of error response bodies: {"error": {"code": "WRITE_FORBIDDEN", "message": "..."}}. */
@Serializable
data class ErrorEnvelopeDto(
    val error: ErrorBodyDto? = null,
)

@Serializable
data class ErrorBodyDto(
    val code: String? = null,
    val message: String? = null,
)
