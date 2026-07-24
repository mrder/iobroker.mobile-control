package com.mobilecontrol.app.tunnel

import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Headers describing THIS leg (WebView<->local proxy) that must not be forwarded verbatim onto
 * the next one (local proxy<->adapter). "accept-encoding" belongs here too, for a subtler reason
 * than the others: OkHttp's BridgeInterceptor only auto-decompresses a gzip response when the
 * caller did NOT explicitly set Accept-Encoding on the request - WebView always sends one
 * ("gzip, deflate, br"), and forwarding it verbatim silently disabled OkHttp's transparent
 * decompression for the adapter call. If anything between here and the target compresses the
 * response (a reverse proxy in front of the adapter, for one - the common case for a publicly
 * reachable instance), the raw gzip bytes then got relayed straight to WebView with no
 * Content-Encoding header to explain them, which rendered as garbled text ("hieroglyphs") instead
 * of the page - confirmed live. Omitting the header lets OkHttp negotiate and decompress normally.
 */
private val HOP_BY_HOP_HEADERS = setOf("host", "connection", "proxy-connection", "content-length", "accept-encoding")

/**
 * A tiny HTTP/1.1 forward-proxy server bound to 127.0.0.1 on an ephemeral port, for exactly one
 * purpose: androidx.webkit.ProxyController points WebView at it for the lifetime of one
 * Tunnel-enabled Web-Seite widget session, so every request the embedded page makes - not just
 * its initial navigation - gets a chance to route through the adapter's tunnel instead of
 * needing the phone to actually reach the target on the LAN. See HttpProxyRequest for the parsing
 * side and TunnelController for the ProxyController/token-lifecycle side.
 *
 * Scoped hard to [approvedHost]/[approvedPort]: any request whose own target doesn't match is
 * rejected locally (403) before it ever reaches the network - defense in depth on top of the
 * backend independently re-deriving the target from the token itself and never trusting a
 * client-supplied host (see TunnelService/forwardTunnelRequest.kt).
 *
 * A CONNECT request (what a proxied https:// navigation looks like) is answered with a plain
 * error rather than attempted - see this class's own module doc in docs/TODO.md for why HTTPS
 * targets aren't supported by this tunnel design.
 */
class TunnelProxyServer(
    private val approvedHost: String,
    private val approvedPort: Int,
    private val tunnelProxyUrl: HttpUrl,
    private val httpClient: OkHttpClient,
    /** Returns the current tunnel token, refreshing it first if it's close to expiring - null if
     *  no valid token could be obtained (e.g. access was revoked). */
    private val tokenProvider: suspend () -> String?,
) {
    private var serverSocket: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Starts listening and returns the ephemeral port it bound to. */
    fun start(): Int {
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        scope.launch { acceptLoop(socket) }
        return socket.localPort
    }

    fun stop() {
        scope.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (currentCoroutineContext().isActive) {
            val client = try {
                socket.accept()
            } catch (io: IOException) {
                break
            }
            scope.launch { handleClient(client) }
        }
    }

    private suspend fun handleClient(client: Socket) {
        client.use { s ->
            val output = s.getOutputStream()
            try {
                val request = HttpProxyRequest.parse(s.getInputStream()) ?: return
                if (request.method == "CONNECT") {
                    writeSimpleResponse(output, 502, "HTTPS targets are not supported by the tunnel")
                    return
                }
                if (!request.matchesOrigin(approvedHost, approvedPort)) {
                    writeSimpleResponse(output, 403, "Only the approved target is tunneled")
                    return
                }
                val token = tokenProvider()
                if (token == null) {
                    writeSimpleResponse(output, 502, "No valid tunnel token")
                    return
                }
                val response = withContext(Dispatchers.IO) { forward(request, token) }
                response.use { writeResponse(output, it) }
            } catch (io: IOException) {
                // The client (WebView) closing the connection mid-request is routine, not an error
                // worth surfacing - nothing more to do once the socket is already gone.
            } catch (e: Exception) {
                runCatching { writeSimpleResponse(output, 502, "Tunnel error: ${e.message}") }
            }
        }
    }

    private fun forward(request: HttpProxyRequest, token: String): okhttp3.Response {
        val method = request.method
        val mediaType = request.contentType?.toMediaTypeOrNull()
        val body = when {
            method == "GET" || method == "HEAD" -> null
            request.body != null -> request.body.toRequestBody(mediaType)
            method == "POST" || method == "PUT" || method == "PATCH" -> ByteArray(0).toRequestBody(mediaType)
            else -> null
        }

        val builder = Request.Builder()
            .url(tunnelProxyUrl)
            .header("X-Tunnel-Token", token)
            .header("X-Tunnel-Path", request.pathAndQuery)
            // Explicitly asks every well-behaved hop (a reverse proxy in front of the adapter,
            // for one) not to compress this response at all, rather than relying on OkHttp's
            // gzip-only implicit auto-decompression - that only covers gzip, not e.g. Brotli, and
            // still requires Accept-Encoding to have been left untouched (see HOP_BY_HOP_HEADERS).
            .header("Accept-Encoding", "identity")
            .method(method, body)
        for ((name, value) in request.headers) {
            // Hop-by-hop headers describe THIS leg (WebView<->local proxy), not the one we're
            // about to make (local proxy<->adapter) - forwarding them verbatim would be wrong.
            if (!HOP_BY_HOP_HEADERS.any { it.equals(name, ignoreCase = true) }) {
                builder.header(name, value)
            }
        }
        return httpClient.newCall(builder.build()).execute()
    }

    private fun writeResponse(output: OutputStream, response: okhttp3.Response) {
        val bytes = response.body?.bytes() ?: ByteArray(0)
        val statusText = response.message.ifBlank { "OK" }
        output.write("HTTP/1.1 ${response.code} $statusText\r\n".toByteArray(Charsets.ISO_8859_1))
        response.header("Content-Type")?.let { output.write("Content-Type: $it\r\n".toByteArray(Charsets.ISO_8859_1)) }
        response.header("Cache-Control")?.let { output.write("Cache-Control: $it\r\n".toByteArray(Charsets.ISO_8859_1)) }
        response.header("Location")?.let { output.write("Location: $it\r\n".toByteArray(Charsets.ISO_8859_1)) }
        for (cookie in response.headers("Set-Cookie")) {
            output.write("Set-Cookie: $cookie\r\n".toByteArray(Charsets.ISO_8859_1))
        }
        // This proxy never keeps a connection alive across requests - one accept() per socket,
        // always closed afterwards - so every response is honestly Connection: close, and the
        // client (WebView) simply opens a fresh loopback connection for its next request. Fine
        // for a purely local hop; not worth the bookkeeping keep-alive would add.
        output.write("Content-Length: ${bytes.size}\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write("Connection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun writeSimpleResponse(output: OutputStream, status: Int, message: String) {
        val body = message.toByteArray(Charsets.UTF_8)
        output.write("HTTP/1.1 $status Tunnel\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write("Content-Type: text/plain; charset=utf-8\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write("Content-Length: ${body.size}\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write("Connection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }
}
