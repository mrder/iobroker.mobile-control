package com.mobilecontrol.app.data.remote

import android.util.Base64
import com.mobilecontrol.app.data.crypto.KeystoreManager
import com.mobilecontrol.app.data.local.TokenStore
import com.mobilecontrol.app.data.remote.dto.ChallengeRequestDto
import com.mobilecontrol.app.data.remote.dto.LoginRequestDto
import com.mobilecontrol.app.data.remote.dto.RefreshRequestDto
import com.mobilecontrol.app.di.AuthRefresh
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider

/**
 * Handles 401s by rotating the refresh token. [apiServiceProvider] is a Dagger Provider (not a
 * direct ApiService) to break the dependency cycle: OkHttpClient -> Authenticator -> ApiService
 * -> Retrofit -> OkHttpClient. The provider is only resolved lazily when a 401 actually occurs.
 *
 * Deliberately the @AuthRefresh-qualified ApiService (its own dedicated OkHttpClient), NOT the
 * app's shared one - see NetworkModule.provideAuthRefreshOkHttpClient's doc for the deadlock this
 * avoids: this method's own runBlocking call runs on a thread from the calling client's
 * Dispatcher, and reusing that same client for the nested refresh call could need a Dispatcher
 * slot that only this very (blocked) thread could free.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val serverConfigHolder: ServerConfigHolder,
    private val keystoreManager: KeystoreManager,
    private val revocationNotifier: RevocationNotifier,
    @AuthRefresh private val apiServiceProvider: Provider<ApiService>,
) : Authenticator {

    /** Serializes every 401-handling attempt on this Authenticator instance (OkHttp may call it
     *  concurrently from multiple request threads, e.g. two calls 401ing around the same moment
     *  right after unlocking). Without this, two threads could each read the same not-yet-rotated
     *  refresh token and both try to rotate it - the backend's own token-family reuse-detection
     *  (SessionsService.rotate) correctly treats a second use of an already-rotated refresh token
     *  as theft/replay and revokes the *entire* session for it. That is a real, live-confirmed way
     *  a perfectly legitimate session got itself revoked. */
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null // already retried once, give up to avoid loops

        val deviceId = serverConfigHolder.deviceId ?: return null
        val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        synchronized(refreshLock) {
            // Another thread may have already refreshed the token while this one was waiting for
            // the lock - reuse that result directly instead of rotating a second time.
            val currentToken = runBlocking { tokenStore.getAccessToken() }
            if (currentToken != null && currentToken != failedToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = runBlocking { tokenStore.getRefreshToken() } ?: return null

            val refreshed = runCatching {
                runBlocking { apiServiceProvider.get().authRefresh(RefreshRequestDto(deviceId, refreshToken)) }
            }.getOrNull()

            val refreshedBody = if (refreshed != null && refreshed.isSuccessful) refreshed.body() else null
            if (refreshedBody != null) {
                val expiresAt = System.currentTimeMillis() + refreshedBody.expiresIn * 1000
                runBlocking { tokenStore.saveTokens(refreshedBody.accessToken, refreshedBody.refreshToken, expiresAt) }
                return response.request.newBuilder()
                    .header("Authorization", "Bearer ${refreshedBody.accessToken}")
                    .build()
            }

            // The refresh token itself was rejected (rotated/reused, or simply stale) - confirmed
            // live as a real, permanently-stuck state: every subsequent request just repeated this
            // same failed refresh forever, with the UI silently showing empty cached data and no
            // error, no matter how long the user waited or how often they reopened the app. Rather
            // than give up immediately, fall back to a full challenge-response re-login using the
            // device's still-intact Keystore identity - the same mechanism pairing ends with, minus
            // the pairing step. If the device is still authorized server-side this silently
            // self-heals the session with no user action needed; only if THIS also fails do we
            // treat it as a genuine revocation.
            val reLoginAccessToken = attemptReLogin(deviceId)
            if (reLoginAccessToken != null) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $reLoginAccessToken")
                    .build()
            }

            runBlocking { tokenStore.clear() }
            revocationNotifier.notify(RevocationReason.SESSION_REVOKED)
            return null
        }
    }

    private fun attemptReLogin(deviceId: String): String? {
        val api = apiServiceProvider.get()

        val challengeResponse = runCatching {
            runBlocking { api.authChallenge(ChallengeRequestDto(deviceId)) }
        }.getOrNull()
        if (challengeResponse == null || !challengeResponse.isSuccessful) return null
        val challenge = challengeResponse.body() ?: return null

        val signatureBase64 = runCatching {
            val nonceBytes = Base64.decode(challenge.nonce, Base64.NO_WRAP)
            val signatureBytes = runBlocking { keystoreManager.sign(nonceBytes) }
            Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        }.getOrNull() ?: return null

        val loginResponse = runCatching {
            runBlocking { api.authLogin(LoginRequestDto(deviceId, challenge.challengeId, signatureBase64)) }
        }.getOrNull()
        if (loginResponse == null || !loginResponse.isSuccessful) return null
        val tokens = loginResponse.body() ?: return null

        val expiresAt = System.currentTimeMillis() + tokens.expiresIn * 1000
        runBlocking { tokenStore.saveTokens(tokens.accessToken, tokens.refreshToken, expiresAt) }
        return tokens.accessToken
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
