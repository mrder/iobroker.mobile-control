package com.mobilecontrol.app.di

import com.mobilecontrol.app.data.repository.AuthRepositoryImpl
import com.mobilecontrol.app.data.repository.CameraRepositoryImpl
import com.mobilecontrol.app.data.repository.CommandRepositoryImpl
import com.mobilecontrol.app.data.repository.DashboardRepositoryImpl
import com.mobilecontrol.app.data.repository.DiagnosticsRepositoryImpl
import com.mobilecontrol.app.data.repository.HistoryRepositoryImpl
import com.mobilecontrol.app.data.repository.NotificationRepositoryImpl
import com.mobilecontrol.app.data.repository.ObjectCatalogRepositoryImpl
import com.mobilecontrol.app.data.repository.PairingRepositoryImpl
import com.mobilecontrol.app.data.repository.SettingsRepositoryImpl
import com.mobilecontrol.app.data.repository.StateRepositoryImpl
import com.mobilecontrol.app.domain.repository.AuthRepository
import com.mobilecontrol.app.domain.repository.CameraRepository
import com.mobilecontrol.app.domain.repository.CommandRepository
import com.mobilecontrol.app.domain.repository.DashboardRepository
import com.mobilecontrol.app.domain.repository.DiagnosticsRepository
import com.mobilecontrol.app.domain.repository.HistoryRepository
import com.mobilecontrol.app.domain.repository.NotificationRepository
import com.mobilecontrol.app.domain.repository.ObjectCatalogRepository
import com.mobilecontrol.app.domain.repository.PairingRepository
import com.mobilecontrol.app.domain.repository.SettingsRepository
import com.mobilecontrol.app.domain.repository.StateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindPairingRepository(impl: PairingRepositoryImpl): PairingRepository

    @Binds
    @Singleton
    abstract fun bindObjectCatalogRepository(impl: ObjectCatalogRepositoryImpl): ObjectCatalogRepository

    @Binds
    @Singleton
    abstract fun bindStateRepository(impl: StateRepositoryImpl): StateRepository

    @Binds
    @Singleton
    abstract fun bindCommandRepository(impl: CommandRepositoryImpl): CommandRepository

    @Binds
    @Singleton
    abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindDiagnosticsRepository(impl: DiagnosticsRepositoryImpl): DiagnosticsRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindCameraRepository(impl: CameraRepositoryImpl): CameraRepository
}
