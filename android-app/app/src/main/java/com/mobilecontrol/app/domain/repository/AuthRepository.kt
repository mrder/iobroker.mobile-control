package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.Session
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val session: StateFlow<Session?>

    suspend fun login(deviceId: String): Result<Session>
    suspend fun refresh(): Result<Session>

    /** Persists tokens handed back directly by pairing/status(approved), skipping the separate challenge/login round trip. */
    suspend fun adoptSession(deviceId: String, accessToken: String, refreshToken: String, expiresIn: Long)

    /** Clears local tokens/keys. If [notifyServer] is true and online, best-effort informs the server. */
    suspend fun logout(notifyServer: Boolean)

    suspend fun handleRevocation()
}
