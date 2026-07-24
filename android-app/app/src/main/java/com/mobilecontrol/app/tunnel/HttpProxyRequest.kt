package com.mobilecontrol.app.tunnel

import java.io.ByteArrayOutputStream
import java.io.InputStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One parsed HTTP/1.1 request as received by [TunnelProxyServer] from WebView (acting as an
 * HTTP proxy client, per androidx.webkit.ProxyController - see that class's own docs). WebView
 * sends the request-target in absolute-URI form for a proxied request ("GET http://host:port/path
 * HTTP/1.1"), not the relative-path form a normal web server sees - [requestUri] is exactly that,
 * so [matchesOrigin] and [pathAndQuery] can tell the difference between "this is genuinely a
 * request to the one approved target" and anything else.
 *
 * Deliberately minimal, not a general HTTP parser: request bodies are read only via
 * Content-Length (chunked transfer-encoding on the WebView->proxy hop is not supported - out of
 * scope for the simple device-UI forms/toggle endpoints this tunnel targets; see docs/TODO.md).
 */
data class HttpProxyRequest(
    val method: String,
    val requestUri: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?,
) {
    val contentType: String?
        get() = headers.firstOrNull { it.first.equals("content-type", ignoreCase = true) }?.second

    /** Everything after the origin (path + optional "?query") - what the tunnel's X-Tunnel-Path
     *  header carries, since the backend derives the origin itself from the approved UrlEmbed and
     *  must never trust a client-supplied host (see TunnelService/forwardTunnelRequest). */
    val pathAndQuery: String
        get() {
            val url = requestUri.toHttpUrlOrNull() ?: return if (requestUri.startsWith("/")) requestUri else "/$requestUri"
            val query = url.encodedQuery
            return url.encodedPath + (if (query != null) "?$query" else "")
        }

    /** True if this request's own target host:port is exactly the one approved origin - the
     *  proxy's own defense-in-depth check, on top of the backend re-enforcing the same thing. An
     *  origin-form request-target (no scheme/host, just a bare path - not what a real HTTP proxy
     *  client sends, but tolerated) is treated as already scoped to the current connection and
     *  passed through, matching how a browser configured with a proxy never actually emits one. */
    fun matchesOrigin(host: String, port: Int): Boolean {
        val url = requestUri.toHttpUrlOrNull() ?: return true
        return url.host.equals(host, ignoreCase = true) && url.port == port
    }

    companion object {
        private const val MAX_HEADER_LINES = 200
        private const val MAX_BODY_BYTES = 20 * 1024 * 1024

        /** Reads exactly one HTTP/1.1 request from [input] - request line, headers, and body (if
         *  Content-Length is present) - or null if the stream closed before a request line
         *  arrived (the normal case for a client that simply disconnects). */
        fun parse(input: InputStream): HttpProxyRequest? {
            val requestLine = readLine(input) ?: return null
            if (requestLine.isBlank()) return null
            val parts = requestLine.split(' ', limit = 3)
            if (parts.size < 2) return null
            val method = parts[0].uppercase()
            val uri = parts[1]

            val headers = mutableListOf<Pair<String, String>>()
            while (headers.size < MAX_HEADER_LINES) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                headers.add(line.substring(0, idx).trim() to line.substring(idx + 1).trim())
            }

            val contentLength = headers
                .firstOrNull { it.first.equals("content-length", ignoreCase = true) }
                ?.second
                ?.toIntOrNull()
                ?.coerceIn(0, MAX_BODY_BYTES)
            val body = if (contentLength != null && contentLength > 0) readExactly(input, contentLength) else null

            return HttpProxyRequest(method, uri, headers, body)
        }

        private fun readLine(input: InputStream): String? {
            val buffer = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) {
                    return if (buffer.size() == 0) null else buffer.toString(Charsets.ISO_8859_1.name())
                }
                if (b == '\n'.code) {
                    val bytes = buffer.toByteArray()
                    val len = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
                    return String(bytes, 0, len, Charsets.ISO_8859_1)
                }
                buffer.write(b)
            }
        }

        private fun readExactly(input: InputStream, length: Int): ByteArray {
            val buffer = ByteArray(length)
            var read = 0
            while (read < length) {
                val n = input.read(buffer, read, length - read)
                if (n == -1) break
                read += n
            }
            return if (read == length) buffer else buffer.copyOf(read)
        }
    }
}
