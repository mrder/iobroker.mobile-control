package com.mobilecontrol.app.data.repository

import android.util.Base64
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
import java.security.MessageDigest
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

        return safeApiCall(
            onRawResponse = { response -> captureCertificatePin(response.raw()) },
        ) {
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
        return safeApiCall(
            onRawResponse = { response -> captureCertificatePin(response.raw()) },
        ) { apiService.pairingStatus(claimId) }.map {
            PairingStatusResult(
                status = PairingStatus.fromWireName(it.status),
                deviceId = it.deviceId,
                accessToken = it.accessToken,
                refreshToken = it.refreshToken,
                expiresIn = it.expiresIn,
            )
        }
    }

    /**
     * Trust-on-first-use: captures the SPKI SHA-256 pin of whatever TLS certificate this exact
     * pairing conversation actually saw live, the one and only moment a hard pin can legitimately
     * be established without a separate out-of-band channel - the QR code's own fingerprint check
     * (ServerFingerprintChecker) plus the admin manually approving the device in the admin UI are
     * this moment's real trust anchor. Never overwrites an already-captured pin - captured exactly
     * once per pairing. No-ops for a plain-http deployment (VPN-only, no handshake to read).
     */
    private fun captureCertificatePin(rawResponse: okhttp3.Response) {
        if (serverConfigHolder.certificatePin != null) return
        val certificate = rawResponse.handshake?.peerCertificates?.firstOrNull() ?: return
        val spkiHash = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded)
        serverConfigHolder.certificatePin = "sha256/" + Base64.encodeToString(spkiHash, Base64.NO_WRAP)
    }
}
