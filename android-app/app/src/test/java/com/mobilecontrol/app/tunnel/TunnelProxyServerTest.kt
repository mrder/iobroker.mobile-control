package com.mobilecontrol.app.tunnel

import java.net.Socket
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the live-reported (2026-07-31) bug: a shared TunnelProxyServer used to
 * track a single global approved target, so a second Tunnel-mode widget's start() silently
 * replaced the first's, and either widget's stop() could tear down whichever session happened to
 * be active - not necessarily its own. These tests run the real server against real loopback
 * sockets (same as production - androidx.webkit.ProxyController talks HTTP/1.1 to it exactly like
 * this), with the actual outbound adapter call replaced by a network-free OkHttp interceptor so no
 * real backend is needed - it just echoes back which token it received.
 */
class TunnelProxyServerTest {

    private var server: TunnelProxyServer? = null

    @After
    fun tearDown() {
        server?.stop()
    }

    private fun startServer(): TunnelProxyServer {
        val echoTokenClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val token = request.header("X-Tunnel-Token")
                Response.Builder()
                    .request(request)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("token=$token".toResponseBody(null))
                    .build()
            })
            .build()
        val srv = TunnelProxyServer(tunnelProxyUrl = "http://adapter.internal/api/v1/tunnel/proxy".toHttpUrl(), httpClient = echoTokenClient)
        server = srv
        return srv
    }

    /** Sends one raw HTTP-proxy-style request over a fresh loopback socket and returns the raw
     *  response text - the server always closes the connection after one response. */
    private fun sendRequest(port: Int, absoluteUri: String): String {
        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write("GET $absoluteUri HTTP/1.1\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            return socket.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
        }
    }

    @Test
    fun `two simultaneously registered embeds are each routed to their own target and token`() = runTest {
        val srv = startServer()
        val port = srv.start()
        srv.registerTarget("embed-a", "device-a.local", 80) { "token-a" }
        srv.registerTarget("embed-b", "device-b.local", 80) { "token-b" }

        val responseA = sendRequest(port, "http://device-a.local/status")
        val responseB = sendRequest(port, "http://device-b.local/status")

        assertTrue("expected 200 for embed-a, got: $responseA", responseA.startsWith("HTTP/1.1 200"))
        assertTrue(responseA.endsWith("token=token-a"))
        assertTrue("expected 200 for embed-b, got: $responseB", responseB.startsWith("HTTP/1.1 200"))
        assertTrue(responseB.endsWith("token=token-b"))
    }

    @Test
    fun `a request for an unregistered origin is rejected even while other embeds are active`() = runTest {
        val srv = startServer()
        val port = srv.start()
        srv.registerTarget("embed-a", "device-a.local", 80) { "token-a" }

        val response = sendRequest(port, "http://device-c.local/status")

        assertTrue(response.startsWith("HTTP/1.1 403"))
    }

    @Test
    fun `unregistering one embed does not affect another embed's still-active session`() = runTest {
        val srv = startServer()
        val port = srv.start()
        srv.registerTarget("embed-a", "device-a.local", 80) { "token-a" }
        srv.registerTarget("embed-b", "device-b.local", 80) { "token-b" }

        srv.unregisterTarget("embed-a")

        val responseA = sendRequest(port, "http://device-a.local/status")
        val responseB = sendRequest(port, "http://device-b.local/status")

        assertTrue("embed-a should now be rejected, got: $responseA", responseA.startsWith("HTTP/1.1 403"))
        assertTrue("embed-b must be unaffected, got: $responseB", responseB.startsWith("HTTP/1.1 200"))
        assertTrue(responseB.endsWith("token=token-b"))
    }

    @Test
    fun `hasTargets reflects registration state`() {
        val srv = startServer()
        assertEquals(false, srv.hasTargets)
        srv.registerTarget("embed-a", "device-a.local", 80) { "token-a" }
        assertEquals(true, srv.hasTargets)
        srv.unregisterTarget("embed-a")
        assertEquals(false, srv.hasTargets)
    }

    /** Live-reported (2026-08-06): two different embeds (e.g. "Habpanel" and "Energie") pointing
     *  at the exact same host:port - one physical device serving more than one dashboard under
     *  different paths - used to be treated as ambiguous and rejected outright, breaking every
     *  request to that shared origin (including a background socket.io long-poll that just kept
     *  repeating the same rejected request forever, matching the reported "starts loading but
     *  never finishes" symptom). Same origin is not actually ambiguous for forwarding purposes -
     *  see findTarget's own doc for why either token is equally valid here. */
    @Test
    fun `two embeds sharing the exact same origin do not reject each other`() = runTest {
        val srv = startServer()
        val port = srv.start()
        srv.registerTarget("embed-habpanel", "device-a.local", 80) { "token-habpanel" }
        srv.registerTarget("embed-energie", "device-a.local", 80) { "token-energie" }

        val response = sendRequest(port, "http://device-a.local/socket.io/?transport=polling")

        assertTrue("shared-origin request should be forwarded, not rejected, got: $response", response.startsWith("HTTP/1.1 200"))
    }

    /** A bare-path (origin-form) request is only unambiguous when every currently-registered
     *  target agrees on the same origin - if genuinely different origins are simultaneously
     *  active, there is no way to know which one a bare path was meant for, so it must still be
     *  rejected rather than guessed at (this is the one remaining case findTarget still rejects). */
    @Test
    fun `a bare-path request is rejected when distinct origins are simultaneously registered`() = runTest {
        val srv = startServer()
        val port = srv.start()
        srv.registerTarget("embed-a", "device-a.local", 80) { "token-a" }
        srv.registerTarget("embed-b", "device-b.local", 80) { "token-b" }

        Socket("127.0.0.1", port).use { socket ->
            socket.getOutputStream().write("GET /status HTTP/1.1\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            val response = socket.getInputStream().readBytes().toString(Charsets.ISO_8859_1)
            assertTrue("ambiguous bare-path request should be rejected, got: $response", response.startsWith("HTTP/1.1 403"))
        }
    }
}
