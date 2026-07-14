package com.mobilecontrol.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecontrol.app.data.remote.RevocationNotifier
import com.mobilecontrol.app.domain.repository.AuthRepository
import com.mobilecontrol.app.domain.repository.DashboardRepository
import com.mobilecontrol.app.domain.repository.SettingsRepository
import com.mobilecontrol.app.domain.repository.StateRepository
import com.mobilecontrol.app.ui.lock.AppLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartDestination { ONBOARDING, LOCK_VERIFY, LOCK_SETUP, START }

@HiltViewModel
class AppRootViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val stateRepository: StateRepository,
    private val dashboardRepository: DashboardRepository,
    val appLockManager: AppLockManager,
    val revocationNotifier: RevocationNotifier,
) : ViewModel() {

    val isLocked: StateFlow<Boolean> = appLockManager.isLocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    suspend fun resolveStartDestination(): StartDestination {
        val profile = settingsRepository.observeDeviceProfile().first()
        val session = authRepository.session.value
        if (profile == null || session == null) return StartDestination.ONBOARDING

        val lockEnabled = settingsRepository.observeAppLockEnabled().first()
        if (!lockEnabled) {
            appLockManager.unlock()
            return StartDestination.START
        }
        return if (settingsRepository.hasPin()) StartDestination.LOCK_VERIFY else StartDestination.LOCK_SETUP
    }

    fun connectRealtime() {
        stateRepository.connect()
    }

    fun handleRevocation() {
        viewModelScope.launch {
            authRepository.handleRevocation()
            appLockManager.lock()
        }
    }

    suspend fun startDashboardId(): String? =
        dashboardRepository.getStartDashboardId() ?: dashboardRepository.observeDashboards().first().firstOrNull()?.id
}
