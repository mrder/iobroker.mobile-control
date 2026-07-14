package com.mobilecontrol.app.domain.model

data class PairingQrPayload(
    val version: Int,
    val serverUrl: String,
    val pairingId: String,
    val pairingSecret: String,
    val expiresAt: String,
    val serverFingerprint: String,
    val instanceId: String,
) {
    fun isExpired(nowEpochMillis: Long): Boolean {
        val expiry = runCatching { java.time.OffsetDateTime.parse(expiresAt) }.getOrNull()
            ?: return false // defensive: if the timestamp can't be parsed, don't block pairing on it alone
        return expiry.toInstant().toEpochMilli() <= nowEpochMillis
    }
}
