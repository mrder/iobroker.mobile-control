package com.mobilecontrol.app.tunnel

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun stream(raw: String): ByteArrayInputStream = ByteArrayInputStream(raw.toByteArray(Charsets.ISO_8859_1))

class HttpProxyRequestTest {

    @Test
    fun `parses a simple GET request line and headers`() {
        val raw = "GET http://192.168.1.40:8097/relay/0?turn=on HTTP/1.1\r\n" +
            "Host: 192.168.1.40:8097\r\n" +
            "Accept: text/html\r\n" +
            "\r\n"

        val request = HttpProxyRequest.parse(stream(raw))

        assertEquals("GET", request?.method)
        assertEquals("http://192.168.1.40:8097/relay/0?turn=on", request?.requestUri)
        assertEquals("text/html", request?.headers?.firstOrNull { it.first == "Accept" }?.second)
        assertNull(request?.body)
    }

    @Test
    fun `lowercases neither method casing input nor header names, but uppercases the method`() {
        val raw = "get http://host/ HTTP/1.1\r\n\r\n"
        val request = HttpProxyRequest.parse(stream(raw))
        assertEquals("GET", request?.method)
    }

    @Test
    fun `reads exactly Content-Length bytes as the body, even with extra headers after it`() {
        val body = "on=true"
        val raw = "POST http://192.168.1.40/relay/0 HTTP/1.1\r\n" +
            "Content-Type: application/x-www-form-urlencoded\r\n" +
            "Content-Length: ${body.length}\r\n" +
            "\r\n" +
            body

        val request = HttpProxyRequest.parse(stream(raw))

        assertEquals("POST", request?.method)
        assertEquals(body, request?.body?.toString(Charsets.ISO_8859_1))
        assertEquals("application/x-www-form-urlencoded", request?.contentType)
    }

    @Test
    fun `pathAndQuery strips scheme and host from an absolute-URI request line`() {
        val raw = "GET http://192.168.1.40:8097/status?x=1&y=2 HTTP/1.1\r\n\r\n"
        val request = HttpProxyRequest.parse(stream(raw))
        assertEquals("/status?x=1&y=2", request?.pathAndQuery)
    }

    @Test
    fun `pathAndQuery on a bare-path request-target passes it through unchanged`() {
        val raw = "GET /status HTTP/1.1\r\n\r\n"
        val request = HttpProxyRequest.parse(stream(raw))
        assertEquals("/status", request?.pathAndQuery)
    }

    @Test
    fun `matchesOrigin accepts the exact approved host and port, rejects anything else`() {
        val raw = "GET http://192.168.1.40:8097/x HTTP/1.1\r\n\r\n"
        val request = HttpProxyRequest.parse(stream(raw))!!

        assertTrue(request.matchesOrigin("192.168.1.40", 8097))
        assertFalse(request.matchesOrigin("192.168.1.40", 80))
        assertFalse(request.matchesOrigin("evil.example.com", 8097))
    }

    @Test
    fun `matchesOrigin is case-insensitive for the host`() {
        val raw = "GET http://MyDevice.local:80/x HTTP/1.1\r\n\r\n"
        val request = HttpProxyRequest.parse(stream(raw))!!
        assertTrue(request.matchesOrigin("mydevice.local", 80))
    }

    @Test
    fun `returns null for an empty stream (client disconnected before sending anything)`() {
        assertNull(HttpProxyRequest.parse(stream("")))
    }

    @Test
    fun `a malformed request line without a second token returns null`() {
        assertNull(HttpProxyRequest.parse(stream("garbage\r\n\r\n")))
    }

    @Test
    fun `ignores a header line with no colon rather than throwing`() {
        val raw = "GET http://host/ HTTP/1.1\r\nnot-a-header-line\r\nAccept: text/plain\r\n\r\n"
        val request = HttpProxyRequest.parse(stream(raw))
        assertEquals(1, request?.headers?.size)
        assertEquals("text/plain", request?.headers?.first()?.second)
    }
}
