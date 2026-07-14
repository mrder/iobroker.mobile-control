package com.mobilecontrol.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mobilecontrol.app.data.local.dao.CatalogDao
import com.mobilecontrol.app.data.local.dao.DashboardDao
import com.mobilecontrol.app.data.local.dao.StateCacheDao
import com.mobilecontrol.app.data.local.entity.CatalogObjectEntity
import com.mobilecontrol.app.data.local.entity.DashboardEntity
import com.mobilecontrol.app.data.local.entity.StateCacheEntity

@Database(
    entities = [CatalogObjectEntity::class, DashboardEntity::class, StateCacheEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun stateCacheDao(): StateCacheDao
}
