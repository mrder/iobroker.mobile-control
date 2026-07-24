package com.mobilecontrol.app.tunnel

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

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

    suspend fun enable(localProxyHostPort: String) {
        if (!isSupported) return
        suspendCancellableCoroutine { cont ->
            val config = ProxyConfig.Builder().addProxyRule(localProxyHostPort).build()
            ProxyController.getInstance().setProxyOverride(config, immediateExecutor) {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    suspend fun disable() {
        if (!isSupported) return
        suspendCancellableCoroutine { cont ->
            ProxyController.getInstance().clearProxyOverride(immediateExecutor) {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }
}
