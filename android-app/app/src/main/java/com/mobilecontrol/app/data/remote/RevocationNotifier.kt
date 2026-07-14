package com.mobilecontrol.app.data.remote

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class RevocationReason { DEVICE_REVOKED, SESSION_REVOKED }

/** Fan-in point for revocation signals coming from either REST 401 bodies or the `session_revoked` WS message. */
@Singleton
class RevocationNotifier @Inject constructor() {
    private val _events = MutableSharedFlow<RevocationReason>(extraBufferCapacity = 4)
    val events: SharedFlow<RevocationReason> = _events

    fun notify(reason: RevocationReason) {
        _events.tryEmit(reason)
    }
}
