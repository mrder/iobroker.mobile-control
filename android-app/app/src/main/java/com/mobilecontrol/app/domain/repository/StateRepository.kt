package com.mobilecontrol.app.domain.repository

import com.mobilecontrol.app.domain.model.LiveValue
import kotlinx.coroutines.flow.StateFlow

enum class ConnectionState { CONNECTING, CONNECTED, DISCONNECTED, OFFLINE }

interface StateRepository {
    val connectionState: StateFlow<ConnectionState>
    val liveValues: StateFlow<Map<String, LiveValue>>

    suspend fun fetchInitialStates(objectIds: List<String>): Result<Unit>
    fun subscribe(objectIds: Set<String>)
    fun unsubscribe(objectIds: Set<String>)

    fun connect()
    fun disconnect()
}
