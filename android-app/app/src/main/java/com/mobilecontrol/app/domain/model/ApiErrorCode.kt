package com.mobilecontrol.app.domain.model

/** Mirrors the server's error code contract 1:1 so UI layers can branch on a closed set. */
enum class ApiErrorCode {
    AUTH_REQUIRED,
    TOKEN_EXPIRED,
    DEVICE_REVOKED,
    SESSION_REVOKED,
    OBJECT_NOT_FOUND,
    READ_FORBIDDEN,
    WRITE_FORBIDDEN,
    VALUE_INVALID,
    CONFIRMATION_REQUIRED,
    LOCAL_ONLY,
    RATE_LIMITED,
    COMMAND_TIMEOUT,
    REVISION_CONFLICT,
    SERVER_UNAVAILABLE,
    UNKNOWN,
    ;

    companion object {
        fun fromWireName(value: String?): ApiErrorCode =
            value?.let { name -> entries.firstOrNull { it.name == name } } ?: UNKNOWN
    }

    val requiresReauth: Boolean
        get() = this == AUTH_REQUIRED || this == TOKEN_EXPIRED

    val requiresFullLogout: Boolean
        get() = this == DEVICE_REVOKED || this == SESSION_REVOKED
}

/** Thrown by repositories/remote data sources to carry a typed [ApiErrorCode] up to the UI layer. */
class ApiException(
    val errorCode: ApiErrorCode,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: errorCode.name, cause)
