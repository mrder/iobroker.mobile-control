package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClaimRequestDto(
    val pairingId: String,
    val pairingSecret: String,
    val deviceName: String,
    val platform: String = "android",
    val appVersion: String,
    val publicKey: String,
)

@Serializable
data class ClaimResponseDto(
    val status: String,
    val claimId: String,
)

@Serializable
data class PairingStatusResponseDto(
    val status: String,
    val deviceId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Long? = null,
)
