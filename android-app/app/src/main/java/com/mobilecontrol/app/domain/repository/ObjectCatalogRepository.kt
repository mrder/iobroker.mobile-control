package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import kotlinx.coroutines.flow.Flow

interface ObjectCatalogRepository {
    /** Cached catalog, immediately available offline. */
    fun observeCatalog(): Flow<List<ObjectCatalogItem>>

    /** Refreshes from network and updates the Room cache. */
    suspend fun refreshCatalog(): Result<Unit>
}
