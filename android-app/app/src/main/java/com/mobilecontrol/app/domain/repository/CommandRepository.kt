package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.CommandStatus
import kotlinx.coroutines.flow.StateFlow

interface CommandRepository {
    /** Pending/recent commands keyed by commandId, for widgets to render PENDING/CONFIRMED/FAILED overlays. */
    val commandStates: StateFlow<Map<String, CommandStatus>>

    suspend fun sendCommand(objectId: String, value: Any?): Result<String>
}
