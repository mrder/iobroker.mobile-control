package com.mobilecontrol.app.domain.model

data class DeviceProfile(
    val deviceId: String,
    val deviceName: String,
    val instanceId: String,
    val serverUrl: String,
    val serverFingerprint: String,
    val pairedAt: Long,
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
