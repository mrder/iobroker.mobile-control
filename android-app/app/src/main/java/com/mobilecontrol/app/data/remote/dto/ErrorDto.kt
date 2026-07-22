package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable

/** Actual shape of error response bodies (see ApiError.toBody() in src/lib/errors.ts):
 *  {"error": "WRITE_FORBIDDEN", "message": "..."} - `error` is the flat code string itself,
 *  not a nested object. A previous version of this DTO expected a nested {"code": ...} object,
 *  which meant deserialization always failed and silently fell back to guessing the error code
 *  from the raw HTTP status alone (see mapHttpError in ApiCallExecutor.kt) for every single
 *  error response the app has ever received. */
@Serializable
data class ErrorEnvelopeDto(
    val error: String? = null,
    val message: String? = null,
)
