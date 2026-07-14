package com.mobilecontrol.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.mobilecontrol.app.domain.repository.AuthRepository
import com.mobilecontrol.app.ui.lock.AppLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MobileControlApp : Application() {

    // Field injection forces AuthRepositoryImpl's singleton (and its session/server-config
    // bootstrap from disk) to run at process start, before any screen needs it.
    @Inject lateinit var authRepository: AuthRepository

    @Inject lateinit var appLockManager: AppLockManager

    override fun onCreate() {
        super.onCreate()

        // Re-lock only when the whole app (not just the current Activity) leaves the foreground -
        // using ProcessLifecycleOwner instead of Activity.onStop avoids spuriously re-locking on
        // every screen rotation/configuration change, which would trigger Activity onStop too.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    appLockManager.lock()
                }
            },
        )
    }
}
