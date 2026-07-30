package com.mobilecontrol.app.data.remote

import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches the portal-gate's HTTP Basic Auth header (see the backend's
 * createPortalGateMiddleware) to every single request this app makes - REST and the WebSocket
 * upgrade alike, since both go through the same OkHttpClient's interceptor chain. Deliberately
 * unconditional: unlike RequestSigningInterceptor there is no "skip these paths" list, because
 * the portal gate itself has no such list either (its one exemption, GET /portal-key, is only
 * for the bootstrap case - see AuthRepositoryImpl - and still needs this same header like
 * everything else once a key is actually known).
 *
 * Uses the standard "Authorization" header - deliberately NOT the same header the device's own
 * Bearer token travels on (that one moved to "X-Device-Authorization", see AuthHeaderInterceptor)
 * since HTTP only allows one canonical Authorization value per request and these are two
 * independent, stacked credentials.
 */
class PortalKeyInterceptor @Inject constructor(
    private val serverConfigHolder: ServerConfigHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val portalKey = serverConfigHolder.portalKey ?: return chain.proceed(request)
        val authenticated = request.newBuilder()
            .header("Authorization", Credentials.basic(PORTAL_USERNAME, portalKey))
            .build()
        return chain.proceed(authenticated)
    }

    private companion object {
        // Basic Auth requires *some* username - the server only ever checks the password (see
        // createPortalGateMiddleware), this value is arbitrary and carries no meaning.
        const val PORTAL_USERNAME = "device"
    }
}
