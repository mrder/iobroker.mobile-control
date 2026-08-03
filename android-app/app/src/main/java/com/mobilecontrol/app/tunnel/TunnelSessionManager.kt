package com.mobilecontrol.app.tunnel

import com.mobilecontrol.app.BuildConfig
import com.mobilecontrol.app.data.remote.ServerConfigHolder
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
 * Owns the shared Tunnel proxy for however many Tunnel-mode Web-Seite widgets are currently
 * active: requests a scoped tunnel token per embed (POST /api/v1/tunnel-token/{id}), and routes
 * WebView's traffic through a single local TunnelProxyServer via androidx.webkit.ProxyController -
 * which is process-wide, not per-WebView, so there is only ever one local proxy port, started the
 * moment the first widget needs it and stopped once the last one no longer does. Each active
 * embed gets its own registered target/token on that shared server (see TunnelProxyServer), so
 * more than one Tunnel-mode widget can be visible on the same dashboard at once without them
 * fighting over a single global target - confirmed live to be a real scenario (a dashboard's
 * widget count/arrangement can differ per SizeClass, so two devices showing the "same" dashboard
 * at different screen sizes don't necessarily have the same number of Tunnel widgets on screen
 * simultaneously).
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
    private class TokenState(var token: String? = null, var expiresAt: Long = 0)

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
    private val tokenStates = mutableMapOf<String, TokenState>()

    val isSupported: Boolean get() = WebViewProxyOverride.isSupported

    /**
     * Registers [embedId] (whose real target is [targetUrl]) on the shared tunnel server,
     * starting it first if this is the first active embed. Returns false without side effects if
     * the platform doesn't support proxy override, the target isn't plain http://, or a token
     * couldn't be obtained - callers are expected to fall back to today's direct-navigation
     * behavior in that case. Safe to call again for an already-active embed (e.g. a recomposition
     * with the same URL) - just re-registers its target and reuses its token if still fresh.
     */
    suspend fun start(embedId: String, targetUrl: String): Boolean = lock.withLock {
        if (!isSupported) return@withLock false
        val origin = targetUrl.toHttpUrlOrNull() ?: return@withLock false
        if (origin.scheme != "http") return@withLock false
        val adapterBase = serverConfigHolder.baseUrl ?: return@withLock false

        val state = tokenStates.getOrPut(embedId) { TokenState() }
        val fresh = state.token != null && System.currentTimeMillis() < state.expiresAt - REFRESH_MARGIN_MS
        if (!fresh && !refreshTokenLocked(embedId, state)) {
            tokenStates.remove(embedId)
            return@withLock false
        }

        var srv = server
        if (srv == null) {
            srv = TunnelProxyServer(
                tunnelProxyUrl = adapterBase.newBuilder().addPathSegments("api/v1/tunnel/proxy").build(),
                httpClient = httpClient,
            )
            val port = srv.start()
            server = srv
            WebViewProxyOverride.enable("127.0.0.1:$port")
        }
        srv.registerTarget(embedId, origin.host, origin.port) { ensureFreshToken(embedId) }
        true
    }

    /** Removes [embedId]'s target only - stops the shared server (and disables the WebView proxy
     *  override) once no embed needs it anymore, and never affects any other currently-active
     *  embed's own session. Safe to call even if [embedId] was never started. */
    suspend fun stop(embedId: String) = lock.withLock {
        tokenStates.remove(embedId)
        val srv = server ?: return@withLock
        srv.unregisterTarget(embedId)
        if (!srv.hasTargets) {
            srv.stop()
            server = null
            WebViewProxyOverride.disable()
        }
    }

    private suspend fun ensureFreshToken(embedId: String): String? = lock.withLock {
        val state = tokenStates[embedId] ?: return@withLock null
        val fresh = state.token != null && System.currentTimeMillis() < state.expiresAt - REFRESH_MARGIN_MS
        if (fresh) return@withLock state.token
        if (!refreshTokenLocked(embedId, state)) return@withLock null
        state.token
    }

    private suspend fun refreshTokenLocked(embedId: String, state: TokenState): Boolean {
        val result = urlEmbedRepository.requestTunnelToken(embedId).getOrNull() ?: return false
        state.token = result.token
        state.expiresAt = result.expiresAtEpochMs
        return true
    }
}
