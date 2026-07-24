package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import kotlinx.coroutines.flow.Flow

interface ObjectCatalogRepository {
    /** Cached catalog, immediately available offline. */
    fun observeCatalog(): Flow<List<ObjectCatalogItem>>

    /** Folder id (dot-joined path prefix) -> display name, for building a readable object tree
     *  (see buildObjectTree). Deliberately in-memory only, not part of the Room cache: it's a
     *  cosmetic label, not data anyone needs offline, and adding a whole second persisted table
     *  (with its own migration) for it isn't worth it - offline/before-first-refresh simply falls
     *  back to the raw id segment, same as before this existed. */
    fun observeFolderNames(): Flow<Map<String, String>>

    /** Refreshes from network and updates the Room cache. */
    suspend fun refreshCatalog(): Result<Unit>
}
