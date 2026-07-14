package com.mobilecontrol.app.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecontrol.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockManager: AppLockManager,
) : ViewModel() {

    val biometricEnabled: StateFlow<Boolean> = settingsRepository.observeBiometricEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _wrongPin = MutableStateFlow(false)
    val wrongPin: StateFlow<Boolean> = _wrongPin

    suspend fun hasPin(): Boolean = settingsRepository.hasPin()

    fun setupPin(pin: String) {
        viewModelScope.launch {
            settingsRepository.setPinHash(hash(pin))
            appLockManager.unlock()
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            val stored = settingsRepository.getPinHash()
            if (stored != null && stored == hash(pin)) {
                _wrongPin.value = false
                appLockManager.unlock()
            } else {
                _wrongPin.value = true
            }
        }
    }

    fun onBiometricSuccess() {
        appLockManager.unlock()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setBiometricEnabled(enabled) }
    }

    // Unsalted SHA-256 is fine here: the PIN only gates local UI access to an already-authenticated
    // session (server auth is the Keystore-backed ECDSA key), it is not itself a server credential.
    private fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
