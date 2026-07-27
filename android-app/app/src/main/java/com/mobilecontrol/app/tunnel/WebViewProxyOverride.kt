package com.mobilecontrol.app.tunnel

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Thin suspend wrapper around androidx.webkit.ProxyController - the platform API that actually
 * routes every WebView's network traffic (process-wide, not per-instance - there is no per-
 * WebView override) through TunnelSessionManager's local TunnelProxyServer. Isolated here so
 * callers don't deal with the raw Executor/Runnable-callback API directly.
 *
 * [isSupported] can be false on some WebView provider versions - callers must check it and fall
 * back to today's direct-navigation behavior rather than assume Tunnel mode always works.
 */
object WebViewProxyOverride {
    val isSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)

    private val immediateExecutor = Executor { command -> command.run() }

    /** The platform callback is bounded here on purpose: confirmed live on a Fire tablet that it
     *  can simply never fire on some WebView provider builds despite [isSupported] reporting
     *  true, which left a caller's suspendCancellableCoroutine parked forever - and since the
     *  original caller (WebPageWidget) awaited this before showing the resolved page at all, the
     *  whole widget got stuck on its loading state permanently. A timeout can't distinguish "the
     *  callback will never come" from "just slow", but treating either the same way (proceed
     *  without the tunnel) is strictly better than hanging forever either way. */
    private const val CALLBACK_TIMEOUT_MS = 5_000L

    suspend fun enable(localProxyHostPort: String) {
        if (!isSupported) return
        withTimeoutOrNull(CALLBACK_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val config = ProxyConfig.Builder().addProxyRule(localProxyHostPort).build()
                ProxyController.getInstance().setProxyOverride(config, immediateExecutor) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    suspend fun disable() {
        if (!isSupported) return
        withTimeoutOrNull(CALLBACK_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                ProxyController.getInstance().clearProxyOverride(immediateExecutor) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }
}
