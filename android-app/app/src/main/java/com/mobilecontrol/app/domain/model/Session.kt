package com.mobilecontrol.app.domain.model

data class Session(
    val deviceId: String,
    val accessToken: String,
    val refreshToken: String,
    /** Absolute epoch millis when the access token expires, derived from expiresIn at receipt time. */
    val accessTokenExpiresAt: Long,
    val userId: String?,
    val userName: String?,
)

data class UserInfo(
    val id: String,
    val name: String,
)
