package com.mobilecontrol.app.data.repository

import android.util.Base64
import com.mobilecontrol.app.data.crypto.KeystoreManager
import com.mobilecontrol.app.data.local.SettingsDataStore
import com.mobilecontrol.app.data.local.TokenStore
import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.ServerConfigHolder
import com.mobilecontrol.app.data.remote.dto.ChallengeRequestDto
import com.mobilecontrol.app.data.remote.dto.LoginRequestDto
import com.mobilecontrol.app.data.remote.dto.RefreshRequestDto
import com.mobilecontrol.app.data.remote.safeApiCall
import com.mobilecontrol.app.domain.model.Session
import com.mobilecontrol.app.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val tokenStore: TokenStore,
    private val keystoreManager: KeystoreManager,
    private val serverConfigHolder: ServerConfigHolder,
    private val settingsDataStore: SettingsDataStore,
) : AuthRepository {

    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session

    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        bootstrapScope.launch { restoreSessionFromDisk() }
    }

    private suspend fun restoreSessionFromDisk() {
        val profile = settingsDataStore.observeDeviceProfile().first() ?: return
        serverConfigHolder.deviceId = profile.deviceId
        serverConfigHolder.instanceId = profile.instanceId
        serverConfigHolder.serverFingerprint = profile.serverFingerprint
        serverConfigHolder.setServerUrl(profile.serverUrl)

        val access = tokenStore.getAccessToken() ?: return
        val refresh = tokenStore.getRefreshToken() ?: return
        _session.value = Session(
            deviceId = profile.deviceId,
            accessToken = access,
            refreshToken = refresh,
            accessTokenExpiresAt = tokenStore.getAccessTokenExpiresAt(),
            userId = null,
            userName = null,
        )
    }

    override suspend fun login(deviceId: String): Result<Session> {
        val challengeResult = safeApiCall { apiService.authChallenge(ChallengeRequestDto(deviceId)) }
        val challenge = challengeResult.getOrElse { return Result.failure(it) }

        val nonceBytes = Base64.decode(challenge.nonce, Base64.NO_WRAP)
        val signatureBytes = keystoreManager.sign(nonceBytes)
        val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

        val loginResult = safeApiCall {
            apiService.authLogin(LoginRequestDto(deviceId, challenge.challengeId, signatureBase64))
        }
        val tokens = loginResult.getOrElse { return Result.failure(it) }

        val expiresAt = System.currentTimeMillis() + tokens.expiresIn * 1000
        tokenStore.saveTokens(tokens.accessToken, tokens.refreshToken, expiresAt)

        val session = Session(
            deviceId = deviceId,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            accessTokenExpiresAt = expiresAt,
            userId = tokens.user?.id,
            userName = tokens.user?.name,
        )
        _session.value = session
        return Result.success(session)
    }

    override suspend fun refresh(): Result<Session> {
        val deviceId = serverConfigHolder.deviceId ?: return Result.failure(IllegalStateException("Not paired"))
        val refreshToken = tokenStore.getRefreshToken() ?: return Result.failure(IllegalStateException("No refresh token"))

        val result = safeApiCall { apiService.authRefresh(RefreshRequestDto(deviceId, refreshToken)) }
        val tokens = result.getOrElse { return Result.failure(it) }

        val expiresAt = System.currentTimeMillis() + tokens.expiresIn * 1000
        tokenStore.saveTokens(tokens.accessToken, tokens.refreshToken, expiresAt)

        val current = _session.value
        val session = Session(
            deviceId = deviceId,
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            accessTokenExpiresAt = expiresAt,
            userId = tokens.user?.id ?: current?.userId,
            userName = tokens.user?.name ?: current?.userName,
        )
        _session.value = session
        return Result.success(session)
    }

    override suspend fun adoptSession(deviceId: String, accessToken: String, refreshToken: String, expiresIn: Long) {
        val expiresAt = System.currentTimeMillis() + expiresIn * 1000
        tokenStore.saveTokens(accessToken, refreshToken, expiresAt)
        serverConfigHolder.deviceId = deviceId
        _session.value = Session(
            deviceId = deviceId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            accessTokenExpiresAt = expiresAt,
            userId = null,
            userName = null,
        )
    }

    override suspend fun logout(notifyServer: Boolean) {
        // Best-effort server notification is intentionally omitted: there is no dedicated logout
        // endpoint in the API contract, and failing silently offline must not block local cleanup.
        tokenStore.clear()
        keystoreManager.deleteKeyPair()
        settingsDataStore.clearDeviceProfile()
        serverConfigHolder.deviceId = null
        _session.value = null
    }

    override suspend fun handleRevocation() {
        logout(notifyServer = false)
    }
}
