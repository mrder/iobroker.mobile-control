package com.mobilecontrol.app.di

import android.content.Context
import androidx.room.Room
import com.mobilecontrol.app.data.local.AppDatabase
import com.mobilecontrol.app.data.local.dao.CatalogDao
import com.mobilecontrol.app.data.local.dao.DashboardDao
import com.mobilecontrol.app.data.local.dao.FolderNameDao
import com.mobilecontrol.app.data.local.dao.StateCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        // No fallbackToDestructiveMigration(): this used to silently wipe every cached dashboard
        // and catalog entry on any schema version bump, which live-confirmed as a real problem the
        // moment there was an actively-used install with real user data in it (a v2->v3 bump for
        // FolderNameEntity emptied a live tablet's local dashboards/objects). Any future schema
        // change now needs a real Migration below - Room throws IllegalStateException on a missing
        // one, which is the correct failure mode (loud and caught in testing) over a silent wipe.
        Room.databaseBuilder(context, AppDatabase::class.java, "mobile_control.db")
            .build()

    @Provides
    fun provideCatalogDao(db: AppDatabase): CatalogDao = db.catalogDao()

    @Provides
    fun provideDashboardDao(db: AppDatabase): DashboardDao = db.dashboardDao()

    @Provides
    fun provideStateCacheDao(db: AppDatabase): StateCacheDao = db.stateCacheDao()

    @Provides
    fun provideFolderNameDao(db: AppDatabase): FolderNameDao = db.folderNameDao()
}
