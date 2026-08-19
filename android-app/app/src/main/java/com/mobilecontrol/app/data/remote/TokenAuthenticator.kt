package com.mobilecontrol.app.data.remote

import android.util.Base64
import com.mobilecontrol.app.data.crypto.KeystoreManager
import com.mobilecontrol.app.data.local.TokenStore
import com.mobilecontrol.app.data.remote.dto.ChallengeRequestDto
import com.mobilecontrol.app.data.remote.dto.LoginRequestDto
import com.mobilecontrol.app.data.remote.dto.RefreshRequestDto
import com.mobilecontrol.app.di.AuthRefresh
import com.mobilecontrol.app.domain.model.ApiErrorCode
import com.mobilecontrol.app.domain.model.ApiException
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider

/** Codes that mean the server itself explicitly rejected this credential - the only cases where
 *  falling through to a full re-login (and, if that also fails this way, wiping the session and
 *  telling the user the admin revoked them) is actually justified. Everything else - a network
 *  exception, a 5xx, rate limiting, an unparseable body - is inconclusive: it means we simply
 *  couldn't get a straight answer from the server just now, not that the credential is dead. */
private val ApiErrorCode.isDefiniteRejection: Boolean
    get() = this in setOf(
        ApiErrorCode.AUTH_REQUIRED,
        ApiErrorCode.TOKEN_EXPIRED,
        ApiErrorCode.SESSION_REVOKED,
        ApiErrorCode.DEVICE_REVOKED,
        ApiErrorCode.CHALLENGE_INVALID,
        ApiErrorCode.SIGNATURE_INVALID,
        ApiErrorCode.PAIRING_INVALID,
        ApiErrorCode.PAIRING_EXPIRED,
    )

private sealed interface ReLoginResult {
    data class Success(val accessToken: String) : ReLoginResult
    /** The server explicitly rejected the challenge/login attempt. */
    data object Rejected : ReLoginResult
    /** Couldn't get a conclusive answer (network failure, 5xx, local Keystore hiccup, ...). */
    data object Inconclusive : ReLoginResult
}

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
        // "X-Device-Authorization", not "Authorization" - the latter now carries the portal-gate's
        // Basic Auth (see PortalKeyInterceptor), a completely independent credential this
        // Authenticator has nothing to do with.
        val failedToken = response.request.header("X-Device-Authorization")?.removePrefix("Bearer ")

        synchronized(refreshLock) {
            // Another thread may have already refreshed the token while this one was waiting for
            // the lock - reuse that result directly instead of rotating a second time.
            val currentToken = runBlocking { tokenStore.getAccessToken() }
            if (currentToken != null && currentToken != failedToken) {
                return response.request.newBuilder()
                    .header("X-Device-Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = runBlocking { tokenStore.getRefreshToken() } ?: return null

            val refreshResult = runBlocking {
                safeApiCall { apiServiceProvider.get().authRefresh(RefreshRequestDto(deviceId, refreshToken)) }
            }
            refreshResult.getOrNull()?.let { body ->
                val expiresAt = System.currentTimeMillis() + body.expiresIn * 1000
                runBlocking { tokenStore.saveTokens(body.accessToken, body.refreshToken, expiresAt) }
                return response.request.newBuilder()
                    .header("X-Device-Authorization", "Bearer ${body.accessToken}")
                    .build()
            }

            // Live-reported (2026-08-11): a flaky connection (dropped WiFi, a reverse-proxy hiccup,
            // ...) made this refresh call fail with a plain network exception or a 5xx - previously
            // treated exactly like an explicit "this token is invalid" rejection from the server, so
            // it fell straight through to attemptReLogin below, which failed the same way for the
            // same reason, and the app wiped a perfectly valid session and told the user the admin
            // had revoked their access. Only an error the server actually sent us back (a real HTTP
            // response carrying one of the codes in isDefiniteRejection) means the token is actually
            // dead; anything else is inconclusive and must leave the session untouched so the next
            // real request just retries normally.
            val refreshRejected = (refreshResult.exceptionOrNull() as? ApiException)?.errorCode?.isDefiniteRejection == true
            if (!refreshRejected) return null

            // The refresh token itself was rejected (rotated/reused, or simply stale) - confirmed
            // live as a real, permanently-stuck state: every subsequent request just repeated this
            // same failed refresh forever, with the UI silently showing empty cached data and no
            // error, no matter how long the user waited or how often they reopened the app. Rather
            // than give up immediately, fall back to a full challenge-response re-login using the
            // device's still-intact Keystore identity - the same mechanism pairing ends with, minus
            // the pairing step. If the device is still authorized server-side this silently
            // self-heals the session with no user action needed; only an explicit rejection from
            // *this* attempt too counts as a genuine revocation - see attemptReLogin.
            when (val reLogin = attemptReLogin(deviceId)) {
                is ReLoginResult.Success -> return response.request.newBuilder()
                    .header("X-Device-Authorization", "Bearer ${reLogin.accessToken}")
                    .build()
                ReLoginResult.Inconclusive -> return null
                ReLoginResult.Rejected -> {
                    runBlocking { tokenStore.clear() }
                    revocationNotifier.notify(RevocationReason.SESSION_REVOKED)
                    return null
                }
            }
        }
    }

    private fun attemptReLogin(deviceId: String): ReLoginResult {
        val api = apiServiceProvider.get()

        val challengeResult = runBlocking { safeApiCall { api.authChallenge(ChallengeRequestDto(deviceId)) } }
        val challenge = challengeResult.getOrElse { return it.toReLoginResult() }

        val signatureBase64 = runCatching {
            val nonceBytes = Base64.decode(challenge.nonce, Base64.NO_WRAP)
            val signatureBytes = runBlocking { keystoreManager.sign(nonceBytes) }
            Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        }.getOrNull() ?: return ReLoginResult.Inconclusive // local Keystore hiccup, not a server verdict

        val loginResult = runBlocking {
            safeApiCall { api.authLogin(LoginRequestDto(deviceId, challenge.challengeId, signatureBase64)) }
        }
        val tokens = loginResult.getOrElse { return it.toReLoginResult() }

        val expiresAt = System.currentTimeMillis() + tokens.expiresIn * 1000
        runBlocking { tokenStore.saveTokens(tokens.accessToken, tokens.refreshToken, expiresAt) }
        return ReLoginResult.Success(tokens.accessToken)
    }

    private fun Throwable.toReLoginResult(): ReLoginResult =
        if ((this as? ApiException)?.errorCode?.isDefiniteRejection == true) ReLoginResult.Rejected else ReLoginResult.Inconclusive

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
