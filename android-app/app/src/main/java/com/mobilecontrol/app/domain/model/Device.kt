package com.mobilecontrol.app.domain.model

data class DeviceProfile(
    val deviceId: String,
    val deviceName: String,
    val instanceId: String,
    val serverUrl: String,
    val serverFingerprint: String,
    val pairedAt: Long,
    /** SPKI SHA-256 pin ("sha256/base64...") of the TLS certificate observed live during pairing -
     *  null for a plain-http deployment (VPN-only, nothing to pin) or if pairing happened to
     *  capture no handshake. See CertificatePinningInterceptor. */
    val certificatePin: String? = null,
)

enum class PairingStatus {
    WAITING_FOR_APPROVAL,
    APPROVED,
    REJECTED,
    EXPIRED,
    UNKNOWN,
    ;

    companion object {
        fun fromWireName(value: String): PairingStatus = when (value) {
            "waiting_for_approval" -> WAITING_FOR_APPROVAL
            "approved" -> APPROVED
            "rejected" -> REJECTED
            "expired" -> EXPIRED
            else -> UNKNOWN
        }
    }
}
