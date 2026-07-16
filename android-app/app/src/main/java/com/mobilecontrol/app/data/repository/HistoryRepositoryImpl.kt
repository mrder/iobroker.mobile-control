package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.parseIsoToEpochMillis
import com.mobilecontrol.app.data.remote.safeApiCall
import com.mobilecontrol.app.data.remote.toKotlinValue
import com.mobilecontrol.app.domain.model.HistoryEntry
import com.mobilecontrol.app.domain.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
) : HistoryRepository {

    override suspend fun getHistory(objectId: String, from: String?, to: String?, limit: Int?): Result<List<HistoryEntry>> {
        val result = safeApiCall { apiService.getHistory(id = objectId, from = from, to = to, limit = limit) }
        return result.map { body ->
            body.entries.map { entry ->
                HistoryEntry(
                    value = entry.value.toKotlinValue(),
                    timestampMillis = parseIsoToEpochMillis(entry.timestamp),
                )
            }
        }
    }
}
