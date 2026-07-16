package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.HistoryEntry

interface HistoryRepository {
    /**
     * Loads history entries for [objectId], newest and oldest bounded by [from]/[to] (ISO-8601,
     * server-defaulted when omitted), capped at [limit] entries (server default 500, max 2000).
     * No local/offline cache - history is loaded live on demand, there is no offline requirement
     * for it (unlike live state values, which are cached in Room).
     */
    suspend fun getHistory(
        objectId: String,
        from: String? = null,
        to: String? = null,
        limit: Int? = null,
    ): Result<List<HistoryEntry>>
}
