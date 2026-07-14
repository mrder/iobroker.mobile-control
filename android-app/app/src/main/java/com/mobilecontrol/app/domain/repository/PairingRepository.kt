package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.PairingQrPayload
import com.mobilecontrol.app.domain.model.PairingStatus

data class ClaimResult(val claimId: String, val status: PairingStatus)

data class PairingStatusResult(
    val status: PairingStatus,
    val deviceId: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresIn: Long?,
)

interface PairingRepository {
    suspend fun claim(payload: PairingQrPayload, deviceName: String, publicKeyBase64: String): Result<ClaimResult>
    suspend fun pollStatus(serverUrl: String, claimId: String): Result<PairingStatusResult>
}
