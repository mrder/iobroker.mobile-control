package com.mobilecontrol.app.domain.model

data class LiveValue(
    val objectId: String,
    val value: Any?,
    val timestamp: Long,
    val lastChange: Long,
    val acknowledged: Boolean,
)

data class CommandResult(
    val commandId: String,
    val status: CommandStatus,
)

enum class CommandStatus {
    ACCEPTED,
    EXECUTED,
    CONFIRMED,
    TIMEOUT,
    REJECTED,
    BLOCKED,
    ;

    companion object {
        fun fromWireName(value: String): CommandStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TIMEOUT
    }

    val isTerminal: Boolean
        get() = this == CONFIRMED || this == TIMEOUT || this == REJECTED || this == BLOCKED
}
