package com.mobilecontrol.app

import android.app.Application
import com.mobilecontrol.app.domain.repository.AuthRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MobileControlApp : Application() {

    // Field injection forces AuthRepositoryImpl's singleton (and its session/server-config
    // bootstrap from disk) to run at process start, before any screen needs it.
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate() {
        super.onCreate()
    }
}
