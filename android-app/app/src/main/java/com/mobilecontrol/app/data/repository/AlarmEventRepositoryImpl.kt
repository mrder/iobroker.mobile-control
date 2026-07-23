package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.parseIsoToEpochMillis
import com.mobilecontrol.app.data.remote.safeApiCall
import com.mobilecontrol.app.domain.model.AlarmEvent
import com.mobilecontrol.app.domain.repository.AlarmEventRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmEventRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
) : AlarmEventRepository {

    override suspend fun listSince(sinceEpochMillis: Long): Result<List<AlarmEvent>> =
        safeApiCall { apiService.getAlarmEvents(sinceEpochMillis) }.map { dto ->
            dto.events.map { AlarmEvent(it.objectId, it.value, parseIsoToEpochMillis(it.timestamp)) }
        }
}
