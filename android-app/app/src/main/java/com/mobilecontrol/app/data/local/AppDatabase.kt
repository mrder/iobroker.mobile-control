package com.mobilecontrol.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mobilecontrol.app.data.local.dao.CatalogDao
import com.mobilecontrol.app.data.local.dao.DashboardDao
import com.mobilecontrol.app.data.local.dao.FolderNameDao
import com.mobilecontrol.app.data.local.dao.StateCacheDao
import com.mobilecontrol.app.data.local.entity.CatalogObjectEntity
import com.mobilecontrol.app.data.local.entity.DashboardEntity
import com.mobilecontrol.app.data.local.entity.FolderNameEntity
import com.mobilecontrol.app.data.local.entity.StateCacheEntity

@Database(
    entities = [CatalogObjectEntity::class, DashboardEntity::class, StateCacheEntity::class, FolderNameEntity::class],
    // v2: added min/max/step/allowedValues/localOnly/confirmPolicy to CatalogObjectEntity.
    // v3: added FolderNameEntity - folder display names used to be in-memory only (reset on every
    // process start, and left empty for the whole session if the first catalog refresh happened to
    // fail), which live-confirmed as looking exactly like the "object tree shows ids not names" bug
    // resurfacing whenever connectivity was flaky, even though the actual name-resolution logic
    // was already correct. Persisting it means a bad refresh just shows the last known names.
    // This v2->v3 bump was shipped with fallbackToDestructiveMigration(), which live-confirmed as a
    // real problem: it silently wiped an actively-used tablet's local dashboards/objects cache.
    // DatabaseModule no longer falls back destructively - any future version bump needs a real
    // Migration added here, or the app will fail loudly (IllegalStateException) instead of
    // silently deleting local data.
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun stateCacheDao(): StateCacheDao
    abstract fun folderNameDao(): FolderNameDao
}
