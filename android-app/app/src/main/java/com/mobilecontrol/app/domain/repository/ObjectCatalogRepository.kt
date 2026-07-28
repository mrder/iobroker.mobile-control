package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import kotlinx.coroutines.flow.Flow

interface ObjectCatalogRepository {
    /** Cached catalog, immediately available offline. */
    fun observeCatalog(): Flow<List<ObjectCatalogItem>>

    /** Folder id (dot-joined path prefix) -> display name, for building a readable object tree
     *  (see buildObjectTree). Backed by Room (FolderNameEntity), same as [observeCatalog] - it used
     *  to be in-memory only, which live-confirmed as a real problem: it reset to empty on every
     *  process start and stayed empty for the whole session if the very next refresh happened to
     *  fail (e.g. a connectivity hiccup), making a correctly-working name-resolution feature look
     *  like it had regressed back to raw ids. Persisting it means a failed refresh just keeps
     *  showing the last known names, consistent with how the rest of the catalog behaves offline. */
    fun observeFolderNames(): Flow<Map<String, String>>

    /** Refreshes from network and updates the Room cache. */
    suspend fun refreshCatalog(): Result<Unit>
}
