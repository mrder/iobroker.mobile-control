package com.mobilecontrol.app.data.remote

import com.mobilecontrol.app.data.local.TokenStore
import com.mobilecontrol.app.data.remote.dto.RefreshRequestDto
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
 */
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val serverConfigHolder: ServerConfigHolder,
    private val apiServiceProvider: Provider<ApiService>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null // already retried once, give up to avoid loops

        val deviceId = serverConfigHolder.deviceId ?: return null
        val refreshToken = runBlocking { tokenStore.getRefreshToken() } ?: return null

        val refreshed = runCatching {
            runBlocking { apiServiceProvider.get().authRefresh(RefreshRequestDto(deviceId, refreshToken)) }
        }.getOrNull() ?: return null

        if (!refreshed.isSuccessful) return null
        val body = refreshed.body() ?: return null

        val expiresAt = System.currentTimeMillis() + body.expiresIn * 1000
        runBlocking { tokenStore.saveTokens(body.accessToken, body.refreshToken, expiresAt) }

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${body.accessToken}")
            .build()
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
