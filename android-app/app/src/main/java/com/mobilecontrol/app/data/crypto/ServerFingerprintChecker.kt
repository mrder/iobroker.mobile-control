package com.mobilecontrol.app.data.crypto

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FingerprintCheckResult {
    data class Match(val fingerprint: String) : FingerprintCheckResult
    data class Mismatch(val expected: String, val actual: String) : FingerprintCheckResult
    data class Failure(val message: String) : FingerprintCheckResult
}

/**
 * Simplified certificate pinning for MVP: a full dynamic OkHttp CertificatePinner would hard-abort
 * the TLS handshake on mismatch, which forecloses the "warn the user, let them decide" UX the spec
 * asks for as a fallback. Instead we let the handshake complete against the platform trust store,
 * then compare the leaf certificate's SPKI SHA-256 fingerprint (same "sha256/BASE64" format OkHttp's
 * CertificatePinner uses) against the value embedded in the QR code, and surface any mismatch as an
 * explicit warning before pairing proceeds. This is weaker than true pinning (a MITM with a
 * CA-trusted cert would pass silently) - a real CertificatePinner is a good follow-up.
 */
@Singleton
class ServerFingerprintChecker @Inject constructor() {

    private val plainClient = OkHttpClient.Builder().build()

    suspend fun check(serverUrl: String, expectedFingerprint: String): FingerprintCheckResult {
        return try {
            val request = Request.Builder().url(serverUrl).head().build()
            val response = plainClient.newCall(request).execute()
            val leafCert = response.handshake?.peerCertificates?.firstOrNull() as? X509Certificate
            response.close()
            if (leafCert == null) {
                FingerprintCheckResult.Failure("Kein TLS-Zertifikat erhalten")
            } else {
                val actual = spkiFingerprint(leafCert)
                if (actual.equals(expectedFingerprint, ignoreCase = true)) {
                    FingerprintCheckResult.Match(actual)
                } else {
                    FingerprintCheckResult.Mismatch(expectedFingerprint, actual)
                }
            }
        } catch (ex: Exception) {
            FingerprintCheckResult.Failure(ex.message ?: "Verbindung fehlgeschlagen")
        }
    }

    private fun spkiFingerprint(cert: X509Certificate): String {
        val spki = cert.publicKey.encoded
        val digest = MessageDigest.getInstance("SHA-256").digest(spki)
        return "sha256/" + Base64.encodeToString(digest, Base64.NO_WRAP)
    }
}
