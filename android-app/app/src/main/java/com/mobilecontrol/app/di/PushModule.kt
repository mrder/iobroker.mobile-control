package com.mobilecontrol.app.di

import com.mobilecontrol.app.push.PushServiceController
import com.mobilecontrol.app.push.PushServiceControllerImpl
import com.mobilecontrol.app.push.SystemNotifier
import com.mobilecontrol.app.push.SystemNotifierImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {

    @Binds
    @Singleton
    abstract fun bindSystemNotifier(impl: SystemNotifierImpl): SystemNotifier

    @Binds
    @Singleton
    abstract fun bindPushServiceController(impl: PushServiceControllerImpl): PushServiceController
}
