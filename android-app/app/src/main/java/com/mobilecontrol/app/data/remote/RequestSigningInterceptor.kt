package com.mobilecontrol.app.data.remote

import android.util.Base64
import com.mobilecontrol.app.data.crypto.KeystoreManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject

private const val API_V1_PREFIX = "/api/v1"
private const val TUNNEL_PROXY_PATH = "tunnel/proxy"

/**
 * Signs every authenticated request with the device's Keystore key, mirroring
 * createSignatureMiddleware/verifyRequestSignature on the backend exactly - see that file's own
 * doc for the canonical string format and why path is anchored at "/api/v1" (a reverse proxy in
 * front of the adapter may add/strip its own prefix, so the client can't just sign the full
 * request URL path). Without this, a leaked bearer token alone would be enough to replay a
 * request via curl/Postman.
 *
 * Skips the same pre-auth endpoints AuthHeaderInterceptor does (nothing to sign with yet, or not
 * required server-side either), plus /tunnel/proxy specifically: that one is authorized by its
 * own short-lived per-embed token, not the device bearer/signature scheme at all, and a single
 * tunneled page load can burst dozens of sub-resource requests - signing every one with the
 * hardware-backed Keystore key would add real, pointless latency to page loads.
 */
class RequestSigningInterceptor @Inject constructor(
    private val keystoreManager: KeystoreManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (UNAUTHENTICATED_PATHS.any { path.contains(it) } || path.contains(TUNNEL_PROXY_PATH)) {
            return chain.proceed(request)
        }

        val signedPath = apiV1Path(path)
        val bodyBytes = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readByteArray()
        } ?: ByteArray(0)
        val bodyHashHex = MessageDigest.getInstance("SHA-256").digest(bodyBytes).toHexString()

        val timestamp = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val canonical = "${request.method.uppercase()}\n$signedPath\n$timestamp\n$nonce\n$bodyHashHex"
        val signatureBytes = runBlocking { keystoreManager.sign(canonical.toByteArray(Charsets.UTF_8)) }
        val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

        val signedRequest = request.newBuilder()
            .header("X-Signature-Timestamp", timestamp)
            .header("X-Signature-Nonce", nonce)
            .header("X-Signature", signatureBase64)
            .build()
        return chain.proceed(signedRequest)
    }
}

private fun apiV1Path(fullPath: String): String {
    val index = fullPath.indexOf(API_V1_PREFIX)
    return if (index >= 0) fullPath.substring(index) else fullPath
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
