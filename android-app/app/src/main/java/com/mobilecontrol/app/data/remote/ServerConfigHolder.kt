package com.mobilecontrol.app.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server URL is only known at runtime (scanned from a QR code), so Retrofit is built once
 * against [PLACEHOLDER_BASE_URL] and [DynamicBaseUrlInterceptor] rewrites every request onto the
 * real, currently-known base URL held here. Kept as a plain volatile field (not a Flow) because
 * OkHttp interceptors run synchronously off the main thread.
 */
@Singleton
class ServerConfigHolder @Inject constructor() {

    @Volatile
    var baseUrl: HttpUrl? = null
        private set

    @Volatile
    var serverFingerprint: String? = null

    @Volatile
    var deviceId: String? = null

    @Volatile
    var instanceId: String? = null

    /** SPKI SHA-256 pin of the server's TLS certificate, captured live during pairing - see
     *  CertificatePinningInterceptor. Null means "not yet captured" or "plain-http deployment,
     *  nothing to pin" - both are legitimate, unenforced states, not a security regression. */
    @Volatile
    var certificatePin: String? = null

    /** Shared secret required (via HTTP Basic Auth) on every request to the server - see
     *  PortalKeyInterceptor. Learned from the pairing QR payload for a new pairing, or fetched
     *  once via the /portal-key bootstrap endpoint for a device paired before this existed. Null
     *  means "not yet known" - requests go out without the header and get a 401 until it's set. */
    @Volatile
    var portalKey: String? = null

    fun setServerUrl(rawUrl: String): Boolean {
        val parsed = rawUrl.toHttpUrlOrNull() ?: return false
        baseUrl = if (parsed.encodedPath.endsWith("/")) parsed else parsed.newBuilder().addPathSegment("").build()
        return true
    }

    companion object {
        const val PLACEHOLDER_BASE_URL = "https://placeholder.invalid/"
    }
}
