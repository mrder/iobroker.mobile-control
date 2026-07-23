package com.mobilecontrol.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecontrol.app.BuildConfig
import com.mobilecontrol.app.domain.model.DeviceProfile
import com.mobilecontrol.app.domain.model.ThemeMode
import com.mobilecontrol.app.domain.repository.AuthRepository
import com.mobilecontrol.app.domain.repository.DiagnosticsRepository
import com.mobilecontrol.app.domain.repository.LogEntry
import com.mobilecontrol.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val deviceProfile: DeviceProfile? = null,
    val lastConnectionAt: Long? = null,
    val appLockEnabled: Boolean = true,
    val biometricEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val logs: List<LogEntry> = emptyList(),
    val loggedOut: Boolean = false,
)

private data class BaseSettings(
    val deviceProfile: DeviceProfile?,
    val lastConnectionAt: Long?,
    val appLockEnabled: Boolean,
    val biometricEnabled: Boolean,
    val logs: List<LogEntry>,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val authRepository: AuthRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
) : ViewModel() {

    val appVersion: String = BuildConfig.VERSION_NAME
    val apiVersion: String = BuildConfig.API_VERSION

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsRepository.observeDeviceProfile(),
            settingsRepository.observeLastConnectionAt(),
            settingsRepository.observeAppLockEnabled(),
            settingsRepository.observeBiometricEnabled(),
            diagnosticsRepository.recentLogs,
        ) { profile, lastConnection, appLock, biometric, logs ->
            BaseSettings(profile, lastConnection, appLock, biometric, logs)
        },
        settingsRepository.observeThemeMode(),
    ) { base, themeMode ->
        SettingsUiState(base.deviceProfile, base.lastConnectionAt, base.appLockEnabled, base.biometricEnabled, themeMode, base.logs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAppLockEnabled(enabled) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun clearCache() {
        viewModelScope.launch { settingsRepository.clearCache() }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout(notifyServer = true)
            diagnosticsRepository.log(LogEntry.Level.INFO, "Device logged out locally")
            onDone()
        }
    }
}
