package com.mobilecontrol.app.di

import android.content.Context
import androidx.room.Room
import com.mobilecontrol.app.data.local.AppDatabase
import com.mobilecontrol.app.data.local.dao.CatalogDao
import com.mobilecontrol.app.data.local.dao.DashboardDao
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
        Room.databaseBuilder(context, AppDatabase::class.java, "mobile_control.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCatalogDao(db: AppDatabase): CatalogDao = db.catalogDao()

    @Provides
    fun provideDashboardDao(db: AppDatabase): DashboardDao = db.dashboardDao()

    @Provides
    fun provideStateCacheDao(db: AppDatabase): StateCacheDao = db.stateCacheDao()
}
