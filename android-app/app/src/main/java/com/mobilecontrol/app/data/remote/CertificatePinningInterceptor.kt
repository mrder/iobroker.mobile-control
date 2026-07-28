package com.mobilecontrol.app.data.remote

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Trust-on-first-use certificate pinning: once [ServerConfigHolder.certificatePin] is set
 * (captured live during pairing - see PairingRepositoryImpl.captureCertificatePin), every
 * subsequent connection's leaf certificate must match it exactly, on top of the platform's normal
 * CA trust check. Protects against a certificate that's technically CA-trusted but isn't actually
 * the paired server's (a compromised/coerced CA, or a MITM proxy with its own root installed on
 * the device) - the one gap plain HTTPS alone doesn't close.
 *
 * Deliberately NOT OkHttp's built-in CertificatePinner: that requires a fixed hostname pattern and
 * pin set at OkHttpClient-build time, but here the server host/pin are only known after pairing
 * (same reasoning as DynamicBaseUrlInterceptor for the base URL itself). This runs after the TLS
 * handshake for the connection already completed - the same point CertificatePinner itself checks
 * internally - but throwing here still guarantees no response content ever reaches the rest of the
 * app for a connection that didn't pass.
 *
 * A null pin (not yet captured, or a plain-http/VPN-only deployment with no TLS to pin at all) is
 * a legitimate unenforced state, not a bypass - see ServerConfigHolder's own doc.
 */
class CertificatePinningInterceptor @Inject constructor(
    private val serverConfigHolder: ServerConfigHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val expectedPin = serverConfigHolder.certificatePin ?: return response

        val certificate = response.handshake?.peerCertificates?.firstOrNull()
            ?: return response // no TLS handshake on this connection (plain http) - nothing to check
        val actualPin = "sha256/" +
            Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded), Base64.NO_WRAP)

        if (actualPin != expectedPin) {
            response.close()
            throw IOException("Server certificate pin mismatch - refusing to trust this connection")
        }
        return response
    }
}
