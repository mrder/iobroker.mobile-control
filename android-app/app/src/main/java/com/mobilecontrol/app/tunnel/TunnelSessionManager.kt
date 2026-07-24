package com.mobilecontrol.app.tunnel

import com.mobilecontrol.app.BuildConfig
import com.mobilecontrol.app.data.remote.ServerConfigHolder
import com.mobilecontrol.app.domain.model.TunnelToken
import com.mobilecontrol.app.domain.repository.UrlEmbedRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/** Refresh a token this long before it actually expires, so a long-open WebView session never
 *  hits a gap between "token about to expire" and "next request needs one". */
private const val REFRESH_MARGIN_MS = 2 * 60_000L

/**
 * Owns the single active Tunnel session for a Web-Seite widget in "Tunnel" mode: requests a
 * scoped tunnel token (POST /api/v1/tunnel-token/{id}), starts a local TunnelProxyServer bound to
 * the target's own origin, and points WebView at it via androidx.webkit.ProxyController - which
 * is process-wide, not per-WebView, so only one session is ever meaningful at a time (starting a
 * second one simply replaces the first).
 *
 * Deliberately uses its own plain OkHttpClient rather than the app's shared one: that shared
 * client's interceptors (DynamicBaseUrlInterceptor, AuthHeaderInterceptor) exist to talk to the
 * normal bearer-token API against the placeholder base URL - the tunnel already builds an
 * absolute URL itself and authorizes purely via X-Tunnel-Token, so reusing the shared client
 * would just risk attaching irrelevant machinery to a request it was never designed for.
 */
@Singleton
class TunnelSessionManager @Inject constructor(
    private val urlEmbedRepository: UrlEmbedRepository,
    private val serverConfigHolder: ServerConfigHolder,
) {
    private val lock = Mutex()
    private val httpClient = OkHttpClient.Builder()
        // Deliberately NOT callTimeout: that bounds the entire call including time spent waiting
        // for the response body, which is exactly wrong for a device UI's own live-data
        // connection (long-polling, held open for tens of seconds while it waits for the next
        // event server-side - normal, not stuck). A callTimeout cut those off mid-wait, which the
        // page's own client saw as a dropped connection and kept reconnecting - confirmed live.
        // readTimeout is the correct tool: it only fires on an actual stall (no bytes at all for
        // that long), not on a slow-but-alive held-open request.
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(50, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // BASIC level (method/URL/response code/content-length, no headers or body - the tunnel
        // token itself must never land in logcat) so a live tunnel session is actually
        // diagnosable via `adb logcat` tag "OkHttp", same convention as NetworkModule's client.
        .addInterceptor(HttpLoggingInterceptor().apply { level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE })
        .build()

    private var server: TunnelProxyServer? = null
    private var activeEmbedId: String? = null
    @Volatile private var currentToken: String? = null
    @Volatile private var currentTokenExpiresAt: Long = 0

    val isSupported: Boolean get() = WebViewProxyOverride.isSupported

    /**
     * Starts (or, if a different embed was active, restarts) a tunnel session for [embedId] whose
     * real target is [targetUrl]. Returns false without side effects if the platform doesn't
     * support proxy override, the target isn't plain http://, or a token couldn't be obtained -
     * callers are expected to fall back to today's direct-navigation behavior in that case.
     */
    suspend fun start(embedId: String, targetUrl: String): Boolean = lock.withLock {
        if (!isSupported) return@withLock false
        val origin = targetUrl.toHttpUrlOrNull() ?: return@withLock false
        if (origin.scheme != "http") return@withLock false

        if (activeEmbedId == embedId && server != null) {
            return@withLock true
        }
        stopLocked()

        val adapterBase = serverConfigHolder.baseUrl ?: return@withLock false
        // refreshToken() also populates currentToken/currentTokenExpiresAt as a side effect -
        // no separate assignment needed here.
        refreshToken(embedId) ?: return@withLock false
        val tunnelUrl = adapterBase.newBuilder().addPathSegments("api/v1/tunnel/proxy").build()

        val newServer = TunnelProxyServer(
            approvedHost = origin.host,
            approvedPort = origin.port,
            tunnelProxyUrl = tunnelUrl,
            httpClient = httpClient,
            tokenProvider = { ensureFreshToken(embedId) },
        )
        val port = newServer.start()
        WebViewProxyOverride.enable("127.0.0.1:$port")
        server = newServer
        activeEmbedId = embedId
        true
    }

    suspend fun stop() = lock.withLock { stopLocked() }

    private suspend fun stopLocked() {
        server?.stop()
        server = null
        activeEmbedId = null
        currentToken = null
        currentTokenExpiresAt = 0
        WebViewProxyOverride.disable()
    }

    private suspend fun ensureFreshToken(embedId: String): String? {
        val token = currentToken
        if (token != null && System.currentTimeMillis() < currentTokenExpiresAt - REFRESH_MARGIN_MS) {
            return token
        }
        return refreshToken(embedId)?.token
    }

    private suspend fun refreshToken(embedId: String): TunnelToken? =
        urlEmbedRepository.requestTunnelToken(embedId).getOrNull()?.also {
            currentToken = it.token
            currentTokenExpiresAt = it.expiresAtEpochMs
        }
}
