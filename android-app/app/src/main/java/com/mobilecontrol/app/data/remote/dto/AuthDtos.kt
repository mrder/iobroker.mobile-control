package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeRequestDto(val deviceId: String)

@Serializable
data class ChallengeResponseDto(
    val challengeId: String,
    val nonce: String,
    val expiresAt: String,
)

@Serializable
data class LoginRequestDto(
    val deviceId: String,
    val challengeId: String,
    val signature: String,
)

@Serializable
data class RefreshRequestDto(
    val deviceId: String,
    val refreshToken: String,
)

@Serializable
data class UserDto(
    val id: String,
    val name: String,
)

@Serializable
data class TokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto? = null,
)
