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

    /** Epoch millis until which PIN entry is locked out; 0 = not locked. Refreshed on init and
     *  after every verify attempt so a killed-and-reopened app still honors an active lockout -
     *  see TokenStore's own doc on why this is persisted rather than kept in AppLockManager. */
    private val _lockoutUntil = MutableStateFlow(0L)
    val lockoutUntil: StateFlow<Long> = _lockoutUntil

    init {
        viewModelScope.launch { _lockoutUntil.value = settingsRepository.getPinLockoutUntil() }
    }

    suspend fun hasPin(): Boolean = settingsRepository.hasPin()

    fun setupPin(pin: String) {
        viewModelScope.launch {
            settingsRepository.setPinHash(hash(pin))
            appLockManager.unlock()
        }
    }

    /**
     * A plain suspend function, deliberately NOT fire-and-forget like the old version - the
     * caller must await the real result before treating the PIN as accepted. The previous
     * fire-and-forget version exposed only [wrongPin] as a StateFlow and let the caller navigate
     * to "unlocked" the instant it *attempted* a verification, via a LaunchedEffect keyed on
     * (wrongPin, verifyAttempted) - since [wrongPin] starts out false and this suspend call takes
     * a moment (reads the stored hash from DataStore), that effect fired on the STALE "false"
     * value before this coroutine had a chance to run and correct it, unlocking the app for an
     * actually-wrong PIN. Confirmed live. Returning the real result here and having the caller
     * gate navigation on it directly removes the race entirely.
     *
     * Also enforces the lockout: after 3 consecutive wrong PINs, entry is blocked for
     * [LOCKOUT_DURATION_MS], persisted so restarting the app cannot be used to skip it. A call
     * made while already locked out is rejected outright without even checking the PIN.
     */
    suspend fun verifyPin(pin: String): Boolean {
        val now = System.currentTimeMillis()
        val currentLockout = settingsRepository.getPinLockoutUntil()
        if (currentLockout > now) {
            _lockoutUntil.value = currentLockout
            _wrongPin.value = false
            return false
        }

        val stored = settingsRepository.getPinHash()
        val correct = stored != null && stored == hash(pin)
        _wrongPin.value = !correct

        if (correct) {
            settingsRepository.setFailedPinAttempts(0)
            settingsRepository.setPinLockoutUntil(0L)
            _lockoutUntil.value = 0L
            appLockManager.unlock()
        } else {
            val attempts = settingsRepository.getFailedPinAttempts() + 1
            if (attempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
                val until = now + LOCKOUT_DURATION_MS
                settingsRepository.setFailedPinAttempts(0)
                settingsRepository.setPinLockoutUntil(until)
                _lockoutUntil.value = until
            } else {
                settingsRepository.setFailedPinAttempts(attempts)
            }
        }
        return correct
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

    private companion object {
        const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 3
        const val LOCKOUT_DURATION_MS = 10 * 60_000L
    }
}
