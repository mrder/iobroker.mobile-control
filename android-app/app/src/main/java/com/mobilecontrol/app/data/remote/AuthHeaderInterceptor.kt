package com.mobilecontrol.app.data.remote

import com.mobilecontrol.app.data.local.TokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

private val UNAUTHENTICATED_PATHS = listOf(
    "pairing/claim",
    "pairing/status",
    "auth/challenge",
    "auth/login",
    "auth/refresh",
)

/** Attaches the current access token as a bearer header, skipping the endpoints that precede authentication. */
class AuthHeaderInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (UNAUTHENTICATED_PATHS.any { path.contains(it) }) {
            return chain.proceed(request)
        }
        val token = runBlocking { tokenStore.getAccessToken() }
        val newRequest = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        return chain.proceed(newRequest)
    }
}
