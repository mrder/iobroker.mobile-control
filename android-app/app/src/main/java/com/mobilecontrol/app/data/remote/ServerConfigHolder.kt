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

    fun setServerUrl(rawUrl: String): Boolean {
        val parsed = rawUrl.toHttpUrlOrNull() ?: return false
        baseUrl = if (parsed.encodedPath.endsWith("/")) parsed else parsed.newBuilder().addPathSegment("").build()
        return true
    }

    companion object {
        const val PLACEHOLDER_BASE_URL = "https://placeholder.invalid/"
    }
}
