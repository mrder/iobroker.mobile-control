package com.mobilecontrol.app.data.crypto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

sealed interface FingerprintCheckResult {
    data class Match(val fingerprint: String) : FingerprintCheckResult
    data class Mismatch(val expected: String, val actual: String) : FingerprintCheckResult
    data class Failure(val message: String) : FingerprintCheckResult
}

@Serializable
private data class ServerInfoDto(val fingerprint: String)

/**
 * The backend does not terminate TLS itself - it expects a VPN or reverse proxy in front of it
 * for remote access (see PairingService's serverFingerprint comment on the backend side), so
 * there is no real TLS certificate to pin against here. An earlier version of this class tried
 * to compare the QR code's fingerprint against the leaf TLS certificate's SPKI hash from a live
 * handshake - that compared two fundamentally different values (a certificate hash vs. a hash of
 * the adapter's JWT signing secret) and was guaranteed to mismatch on every single pairing,
 * behind plain HTTP (no handshake at all) or any HTTPS reverse proxy alike.
 *
 * Instead, this fetches the same identity value live from the server's own unauthenticated
 * /api/v1/server/info and compares it directly against the QR code's copy - both sides compute
 * it the same way, so a mismatch still reliably flags "this is not the server that issued this
 * QR code" (e.g. a stale QR from before the adapter's secret was regenerated, or a genuinely
 * different server), even though it isn't a certificate pin. Surfaced as an explicit, dismissable
 * warning rather than a hard abort, per the spec's "warn instead of hard-block" fallback
 * requirement.
 */
@Singleton
class ServerFingerprintChecker @Inject constructor() {

    private val client = OkHttpClient.Builder().build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(serverUrl: String, expectedFingerprint: String): FingerprintCheckResult = withContext(Dispatchers.IO) {
        try {
            val infoUrl = serverUrl.trimEnd('/') + "/api/v1/server/info"
            val request = Request.Builder().url(infoUrl).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string()
            response.close()
            if (!response.isSuccessful || body.isNullOrBlank()) {
                FingerprintCheckResult.Failure("Server nicht erreichbar (HTTP ${response.code})")
            } else {
                val actual = json.decodeFromString(ServerInfoDto.serializer(), body).fingerprint
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
}
