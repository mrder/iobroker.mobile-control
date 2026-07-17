package com.mobilecontrol.app.domain.repository

interface CameraRepository {
    /**
     * Fetches the current snapshot image for [objectId] from GET /api/v1/objects/{id}/snapshot.
     * No local/offline cache, same reasoning as [HistoryRepository] - a camera snapshot is only
     * ever meaningful freshly fetched.
     */
    suspend fun fetchSnapshot(objectId: String): Result<ByteArray>
}
