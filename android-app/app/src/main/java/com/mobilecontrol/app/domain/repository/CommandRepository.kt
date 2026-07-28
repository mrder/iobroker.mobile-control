package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.CommandStatus
import kotlinx.coroutines.flow.StateFlow

interface CommandRepository {
    /** Pending/recent commands keyed by commandId, for widgets to render PENDING/CONFIRMED/FAILED overlays. */
    val commandStates: StateFlow<Map<String, CommandStatus>>

    /**
     * Almost always resolves to [Result.success] with a trackable commandId - even when the
     * underlying POST to the server fails, since that failure is itself recorded as a terminal
     * REJECTED/BLOCKED entry in [commandStates] under the returned id, so callers must register the
     * id in their own objectId->commandId map unconditionally to see it. Only reserved for a
     * genuinely unrecoverable case (e.g. the id couldn't be allocated at all) would this return
     * [Result.failure]; callers should not treat failure to send as "nothing to show".
     */
    suspend fun sendCommand(objectId: String, value: Any?, confirmed: Boolean = false): Result<String>
}
