package com.mobilecontrol.app.data.remote

import com.mobilecontrol.app.data.local.TokenStore
import com.mobilecontrol.app.data.remote.dto.WsEnvelopeDto
import com.mobilecontrol.app.data.remote.dto.WsCommandResultDto
import com.mobilecontrol.app.data.remote.dto.WsSessionRevokedDto
import com.mobilecontrol.app.data.remote.dto.WsStateUpdateDto
import com.mobilecontrol.app.data.remote.dto.WsSubscribeDto
import com.mobilecontrol.app.data.remote.dto.WsUnsubscribeDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

@Singleton
class RealtimeWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val serverConfigHolder: ServerConfigHolder,
    private val tokenStore: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events

    private var webSocket: WebSocket? = null
    private var heartbeatWatchdog: Job? = null
    private val userRequestedDisconnect = AtomicBoolean(false)
    private var reconnectAttempt = 0
    private val pendingSubscriptions = mutableSetOf<String>()

    fun connect() {
        userRequestedDisconnect.set(false)
        scope.launch { openSocket() }
    }

    fun disconnect() {
        userRequestedDisconnect.set(true)
        heartbeatWatchdog?.cancel()
        webSocket?.close(NORMAL_CLOSURE, "client disconnect")
        webSocket = null
    }

    fun subscribe(objectIds: Set<String>) {
        if (objectIds.isEmpty()) return
        pendingSubscriptions.addAll(objectIds)
        send(json.encodeToString(WsSubscribeDto.serializer(), WsSubscribeDto(objectIds = objectIds.toList())))
    }

    fun unsubscribe(objectIds: Set<String>) {
        if (objectIds.isEmpty()) return
        pendingSubscriptions.removeAll(objectIds)
        send(json.encodeToString(WsUnsubscribeDto.serializer(), WsUnsubscribeDto(objectIds = objectIds.toList())))
    }

    private fun send(text: String) {
        webSocket?.send(text)
    }

    private suspend fun openSocket() {
        val restBase = serverConfigHolder.baseUrl ?: return
        val token = tokenStore.getAccessToken() ?: return

        val scheme = if (restBase.scheme == "https") "wss" else "ws"
        val wsUrl = restBase.resolve("ws/v1")?.newBuilder()
            ?.scheme(scheme)
            ?.addQueryParameter("access_token", token)
            ?.build() ?: return

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            reconnectAttempt = 0
            scope.launch { _events.emit(WsEvent.Connected) }
            if (pendingSubscriptions.isNotEmpty()) {
                subscribe(pendingSubscriptions.toSet())
            }
            startHeartbeatWatchdog()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleMessage(bytes.utf8())
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            heartbeatWatchdog?.cancel()
            val willReconnect = !userRequestedDisconnect.get()
            scope.launch { _events.emit(WsEvent.Disconnected(willReconnect)) }
            if (willReconnect) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            heartbeatWatchdog?.cancel()
            val willReconnect = !userRequestedDisconnect.get()
            scope.launch { _events.emit(WsEvent.Disconnected(willReconnect)) }
            if (willReconnect) scheduleReconnect()
        }
    }

    private fun handleMessage(text: String) {
        val type = runCatching { json.decodeFromString(WsEnvelopeDto.serializer(), text).type }.getOrNull() ?: return
        val event: WsEvent? = when (type) {
            "state_update" -> runCatching { json.decodeFromString(WsStateUpdateDto.serializer(), text) }.getOrNull()
                ?.let { WsEvent.StateUpdate(it.objectId, it.value, it.timestamp, it.lastChange, it.ack) }

            "command_result" -> runCatching { json.decodeFromString(WsCommandResultDto.serializer(), text) }.getOrNull()
                ?.let { WsEvent.CommandResult(it.commandId, it.status) }

            "session_revoked" -> runCatching { json.decodeFromString(WsSessionRevokedDto.serializer(), text) }.getOrNull()
                ?.let { WsEvent.SessionRevoked(it.reason) }
                ?: WsEvent.SessionRevoked(null)

            "permissions_changed" -> WsEvent.PermissionsChanged

            "heartbeat", "ping" -> WsEvent.Heartbeat

            else -> null // unknown message types are ignored defensively rather than crashing the pipe
        }
        heartbeatReceivedAt = System.currentTimeMillis()
        event?.let { scope.launch { _events.emit(it) } }
    }

    @Volatile private var heartbeatReceivedAt: Long = System.currentTimeMillis()

    private fun startHeartbeatWatchdog() {
        heartbeatWatchdog?.cancel()
        heartbeatReceivedAt = System.currentTimeMillis()
        heartbeatWatchdog = scope.launch {
            while (true) {
                delay(HEARTBEAT_CHECK_INTERVAL_MS)
                if (System.currentTimeMillis() - heartbeatReceivedAt > HEARTBEAT_TIMEOUT_MS) {
                    // No heartbeat/traffic in time: treat the connection as dead and force a reconnect.
                    webSocket?.cancel()
                    webSocket = null
                    if (!userRequestedDisconnect.get()) {
                        _events.emit(WsEvent.Disconnected(true))
                        scheduleReconnect()
                    }
                    return@launch
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (userRequestedDisconnect.get()) return
        reconnectAttempt++
        val backoffMs = min(MAX_BACKOFF_MS, (BASE_BACKOFF_MS * 2.0.pow(reconnectAttempt - 1)).toLong())
        scope.launch {
            delay(backoffMs)
            if (!userRequestedDisconnect.get()) openSocket()
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val HEARTBEAT_CHECK_INTERVAL_MS = 5_000L
        const val HEARTBEAT_TIMEOUT_MS = 45_000L
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
