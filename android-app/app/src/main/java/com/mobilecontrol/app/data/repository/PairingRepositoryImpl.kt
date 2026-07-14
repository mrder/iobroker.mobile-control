package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.BuildConfig
import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.ServerConfigHolder
import com.mobilecontrol.app.data.remote.dto.ClaimRequestDto
import com.mobilecontrol.app.data.remote.safeApiCall
import com.mobilecontrol.app.domain.model.PairingQrPayload
import com.mobilecontrol.app.domain.model.PairingStatus
import com.mobilecontrol.app.domain.repository.ClaimResult
import com.mobilecontrol.app.domain.repository.PairingRepository
import com.mobilecontrol.app.domain.repository.PairingStatusResult
import javax.inject.Inject

class PairingRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val serverConfigHolder: ServerConfigHolder,
) : PairingRepository {

    override suspend fun claim(
        payload: PairingQrPayload,
        deviceName: String,
        publicKeyBase64: String,
    ): Result<ClaimResult> {
        serverConfigHolder.setServerUrl(payload.serverUrl)
        serverConfigHolder.serverFingerprint = payload.serverFingerprint
        serverConfigHolder.instanceId = payload.instanceId

        return safeApiCall {
            apiService.claimPairing(
                ClaimRequestDto(
                    pairingId = payload.pairingId,
                    pairingSecret = payload.pairingSecret,
                    deviceName = deviceName,
                    appVersion = BuildConfig.VERSION_NAME,
                    publicKey = publicKeyBase64,
                ),
            )
        }.map { ClaimResult(claimId = it.claimId, status = PairingStatus.fromWireName(it.status)) }
    }

    override suspend fun pollStatus(serverUrl: String, claimId: String): Result<PairingStatusResult> {
        if (serverConfigHolder.baseUrl == null) {
            serverConfigHolder.setServerUrl(serverUrl)
        }
        return safeApiCall { apiService.pairingStatus(claimId) }.map {
            PairingStatusResult(
                status = PairingStatus.fromWireName(it.status),
                deviceId = it.deviceId,
                accessToken = it.accessToken,
                refreshToken = it.refreshToken,
                expiresIn = it.expiresIn,
            )
        }
    }
}
