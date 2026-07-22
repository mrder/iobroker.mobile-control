package com.mobilecontrol.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecontrol.app.data.crypto.FingerprintCheckResult
import com.mobilecontrol.app.data.crypto.KeystoreManager
import com.mobilecontrol.app.data.crypto.ServerFingerprintChecker
import com.mobilecontrol.app.data.local.DeviceNameProvider
import com.mobilecontrol.app.domain.model.ApiErrorCode
import com.mobilecontrol.app.domain.model.ApiException
import com.mobilecontrol.app.domain.model.DeviceProfile
import com.mobilecontrol.app.domain.model.PairingQrPayload
import com.mobilecontrol.app.domain.model.PairingStatus
import com.mobilecontrol.app.domain.repository.AuthRepository
import com.mobilecontrol.app.domain.repository.PairingRepository
import com.mobilecontrol.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class OnboardingUiState(
    val qrPayload: PairingQrPayload? = null,
    val qrError: String? = null,
    val fingerprintResult: FingerprintCheckResult? = null,
    val fingerprintChecking: Boolean = false,
    val userAcceptedMismatch: Boolean = false,
    val deviceName: String = "",
    val keyGenerated: Boolean = false,
    val claimId: String? = null,
    val pairingStatus: PairingStatus? = null,
    val pairingError: String? = null,
    val isPolling: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val keystoreManager: KeystoreManager,
    private val pairingRepository: PairingRepository,
    private val fingerprintChecker: ServerFingerprintChecker,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    deviceNameProvider: DeviceNameProvider,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(OnboardingUiState(deviceName = deviceNameProvider.suggestedName()))
    val uiState: StateFlow<OnboardingUiState> = _uiState

    private var pollingJob: Job? = null

    fun onQrCodeScanned(rawContent: String) {
        val payload = try {
            json.decodeFromString(PairingQrPayload.serializer(), rawContent)
        } catch (ex: SerializationException) {
            _uiState.update { it.copy(qrError = "QR-Code konnte nicht gelesen werden: ${ex.message}") }
            return
        }
        if (payload.isExpired(System.currentTimeMillis())) {
            _uiState.update { it.copy(qrError = "Der Pairing-Code ist abgelaufen. Bitte neu scannen.") }
            return
        }
        _uiState.update { it.copy(qrPayload = payload, qrError = null) }
    }

    fun checkServer() {
        val payload = _uiState.value.qrPayload ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(fingerprintChecking = true) }
            val result = fingerprintChecker.check(payload.serverUrl, payload.serverFingerprint)
            _uiState.update { it.copy(fingerprintChecking = false, fingerprintResult = result) }
        }
    }

    fun acceptFingerprintMismatch() {
        _uiState.update { it.copy(userAcceptedMismatch = true) }
    }

    fun setDeviceName(name: String) {
        _uiState.update { it.copy(deviceName = name) }
    }

    /** Generates the Keystore key pair and immediately claims the pairing, then starts status polling. */
    fun generateKeyAndClaim() {
        val payload = _uiState.value.qrPayload ?: return
        viewModelScope.launch {
            val publicKeyBase64 = keystoreManager.generateKeyPair()
            _uiState.update { it.copy(keyGenerated = true) }

            val claimResult = pairingRepository.claim(payload, _uiState.value.deviceName, publicKeyBase64)
            claimResult.fold(
                onSuccess = { claim ->
                    _uiState.update { it.copy(claimId = claim.claimId, pairingStatus = claim.status, pairingError = null) }
                    startPolling(payload.serverUrl, claim.claimId)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(pairingError = claimErrorMessage(error)) }
                },
            )
        }
    }

    /** Turns a claim() failure into text a user can actually act on - previously this always
     *  showed the raw "HTTP 410" (etc.) from ApiException's message, which was both because
     *  ApiErrorCode was missing PAIRING_EXPIRED/PAIRING_INVALID entirely (fell through to
     *  UNKNOWN) and because mapHttpError() never produced a human message in the first place. */
    private fun claimErrorMessage(error: Throwable): String {
        return when ((error as? ApiException)?.errorCode) {
            ApiErrorCode.PAIRING_EXPIRED ->
                "Die Kopplungsanfrage ist abgelaufen. Bitte scanne den QR-Code erneut."
            ApiErrorCode.PAIRING_INVALID ->
                "Der Pairing-Code ist ungültig oder wurde bereits verwendet. Bitte scanne einen neuen QR-Code."
            ApiErrorCode.RATE_LIMITED ->
                "Zu viele Versuche. Bitte kurz warten und erneut versuchen."
            ApiErrorCode.SERVER_UNAVAILABLE ->
                "Der Server ist gerade nicht erreichbar. Bitte später erneut versuchen."
            else -> error.message ?: "Kopplung fehlgeschlagen"
        }
    }

    private fun startPolling(serverUrl: String, claimId: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            _uiState.update { it.copy(isPolling = true) }
            val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val result = pairingRepository.pollStatus(serverUrl, claimId)
                result.fold(
                    onSuccess = { status ->
                        _uiState.update { it.copy(pairingStatus = status.status) }
                        when (status.status) {
                            PairingStatus.APPROVED -> {
                                onApproved(status.deviceId, status.accessToken, status.refreshToken, status.expiresIn)
                                return@launch
                            }
                            PairingStatus.REJECTED, PairingStatus.EXPIRED -> return@launch
                            else -> Unit
                        }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(pairingError = error.message) }
                    },
                )
                delay(POLL_INTERVAL_MS)
            }
            if (_uiState.value.pairingStatus != PairingStatus.APPROVED) {
                _uiState.update { it.copy(isPolling = false, pairingError = "timeout") }
            }
        }
    }

    private suspend fun onApproved(deviceId: String?, accessToken: String?, refreshToken: String?, expiresIn: Long?) {
        val payload = _uiState.value.qrPayload ?: return
        if (deviceId == null) {
            _uiState.update { it.copy(isPolling = false, pairingError = "Server lieferte keine Geräte-ID") }
            return
        }

        settingsRepository.saveDeviceProfile(
            DeviceProfile(
                deviceId = deviceId,
                deviceName = _uiState.value.deviceName,
                instanceId = payload.instanceId,
                serverUrl = payload.serverUrl,
                serverFingerprint = payload.serverFingerprint,
                pairedAt = System.currentTimeMillis(),
            ),
        )

        if (accessToken != null && refreshToken != null && expiresIn != null) {
            authRepository.adoptSession(deviceId, accessToken, refreshToken, expiresIn)
        } else {
            // Defensive path: server approved but did not include tokens inline - do the full
            // challenge/response login explicitly.
            authRepository.login(deviceId)
        }
        _uiState.update { it.copy(isPolling = false) }
    }

    fun cancelPolling() {
        pollingJob?.cancel()
        _uiState.update { it.copy(isPolling = false) }
    }

    fun retryAfterRejectionOrTimeout() {
        _uiState.value = OnboardingUiState()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
