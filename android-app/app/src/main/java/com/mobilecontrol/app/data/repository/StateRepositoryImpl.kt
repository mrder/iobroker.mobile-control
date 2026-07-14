package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.data.local.SettingsDataStore
import com.mobilecontrol.app.data.local.dao.StateCacheDao
import com.mobilecontrol.app.data.local.entity.StateCacheEntity
import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.NetworkMonitor
import com.mobilecontrol.app.data.remote.RealtimeWebSocketClient
import com.mobilecontrol.app.data.remote.RevocationNotifier
import com.mobilecontrol.app.data.remote.RevocationReason
import com.mobilecontrol.app.data.remote.WsEvent
import com.mobilecontrol.app.data.remote.parseIsoToEpochMillis
import com.mobilecontrol.app.data.remote.safeApiCall
import com.mobilecontrol.app.data.remote.toKotlinValue
import com.mobilecontrol.app.domain.model.LiveValue
import com.mobilecontrol.app.domain.repository.ConnectionState
import com.mobilecontrol.app.domain.repository.StateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StateRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val webSocketClient: RealtimeWebSocketClient,
    private val stateCacheDao: StateCacheDao,
    private val networkMonitor: NetworkMonitor,
    private val revocationNotifier: RevocationNotifier,
    private val settingsDataStore: SettingsDataStore,
) : StateRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _liveValues = MutableStateFlow<Map<String, LiveValue>>(emptyMap())
    override val liveValues: StateFlow<Map<String, LiveValue>> = _liveValues

    private val subscribedIds = mutableSetOf<String>()
    private var isOnline = true

    init {
        scope.launch {
            webSocketClient.events.collect { event -> handleWsEvent(event) }
        }
        scope.launch {
            networkMonitor.observeIsOnline().collect { online ->
                isOnline = online
                if (!online) {
                    _connectionState.value = ConnectionState.OFFLINE
                } else if (_connectionState.value == ConnectionState.OFFLINE) {
                    _connectionState.value = ConnectionState.CONNECTING
                    webSocketClient.connect()
                }
            }
        }
    }

    private suspend fun handleWsEvent(event: WsEvent) {
        when (event) {
            is WsEvent.Connected -> {
                _connectionState.value = ConnectionState.CONNECTED
                settingsDataStore.setLastConnectionAt(System.currentTimeMillis())
            }
            is WsEvent.Disconnected -> {
                _connectionState.value = if (!isOnline) {
                    ConnectionState.OFFLINE
                } else if (event.willReconnect) {
                    ConnectionState.CONNECTING
                } else {
                    ConnectionState.DISCONNECTED
                }
            }

            is WsEvent.StateUpdate -> {
                val timestamp = parseIsoToEpochMillis(event.timestamp)
                val lastChange = parseIsoToEpochMillis(event.lastChange, timestamp)
                val liveValue = LiveValue(
                    objectId = event.objectId,
                    value = event.value.toKotlinValue(),
                    timestamp = timestamp,
                    lastChange = lastChange,
                    acknowledged = event.ack,
                )
                _liveValues.value = _liveValues.value + (event.objectId to liveValue)
                stateCacheDao.upsertAll(
                    listOf(
                        StateCacheEntity(
                            objectId = event.objectId,
                            valueJson = (event.value as? JsonPrimitive)?.content,
                            timestamp = timestamp,
                            lastChange = lastChange,
                        ),
                    ),
                )
            }

            is WsEvent.SessionRevoked -> revocationNotifier.notify(RevocationReason.SESSION_REVOKED)

            is WsEvent.PermissionsChanged -> {
                // Permission set narrowed/widened server-side; the catalog/object browser should
                // refetch on next visibility. No direct action needed from the realtime layer itself.
            }

            is WsEvent.Heartbeat -> Unit
            is WsEvent.CommandResult -> Unit // handled by CommandRepositoryImpl, which observes the same shared flow
        }
    }

    override suspend fun fetchInitialStates(objectIds: List<String>): Result<Unit> {
        if (objectIds.isEmpty()) return Result.success(Unit)

        // Always surface last-known cached values immediately so the UI has something to show offline.
        val cached = stateCacheDao.getFor(objectIds)
        if (cached.isNotEmpty()) {
            val cachedMap = cached.associate { entity ->
                entity.objectId to LiveValue(
                    objectId = entity.objectId,
                    value = entity.valueJson,
                    timestamp = entity.timestamp,
                    lastChange = entity.lastChange,
                    acknowledged = true,
                )
            }
            _liveValues.value = _liveValues.value + cachedMap
        }

        val result = safeApiCall { apiService.getStates(objectIds.joinToString(",")) }
        val body = result.getOrElse { return Result.failure(it) }

        val fresh = body.states.mapValues { (objectId, dto) ->
            val timestamp = parseIsoToEpochMillis(dto.timestamp)
            val lastChange = parseIsoToEpochMillis(dto.lastChange, timestamp)
            LiveValue(
                objectId = objectId,
                value = dto.value.toKotlinValue(),
                timestamp = timestamp,
                lastChange = lastChange,
                acknowledged = dto.ack,
            )
        }
        _liveValues.value = _liveValues.value + fresh
        stateCacheDao.upsertAll(
            fresh.map { (id, live) ->
                StateCacheEntity(
                    objectId = id,
                    valueJson = live.value?.toString(),
                    timestamp = live.timestamp,
                    lastChange = live.lastChange,
                )
            },
        )
        return Result.success(Unit)
    }

    override fun subscribe(objectIds: Set<String>) {
        val newOnes = objectIds - subscribedIds
        subscribedIds.addAll(objectIds)
        if (newOnes.isNotEmpty()) webSocketClient.subscribe(newOnes)
    }

    override fun unsubscribe(objectIds: Set<String>) {
        subscribedIds.removeAll(objectIds)
        webSocketClient.unsubscribe(objectIds)
    }

    override fun connect() {
        _connectionState.value = if (isOnline) ConnectionState.CONNECTING else ConnectionState.OFFLINE
        if (isOnline) webSocketClient.connect()
    }

    override fun disconnect() {
        webSocketClient.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
