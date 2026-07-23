package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.AlarmEvent

interface AlarmEventRepository {
    /** GET /api/v1/alarm-events?since= - alarm transitions recorded after [sinceEpochMillis]. */
    suspend fun listSince(sinceEpochMillis: Long): Result<List<AlarmEvent>>
}
