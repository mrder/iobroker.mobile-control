package com.mobilecontrol.app.data.remote

import com.mobilecontrol.app.data.crypto.KeystoreManager
import com.mobilecontrol.app.data.local.TokenStore
import com.mobilecontrol.app.data.remote.dto.WsAuthDto
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
import android.util.Base64
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/** Sentinel "method"/"path" pair for the WS auth message's signature - mirrors
 *  RealtimeGateway.WS_AUTH_SIGNATURE_METHOD/PATH on the backend exactly (see that file's doc for
 *  why: there's only one WS endpoint, so this just keeps it going through the same signed-request
 *  shape as every REST call rather than a parallel format). */
private const val WS_AUTH_SIGNATURE_METHOD = "WS"
private const val WS_AUTH_SIGNATURE_PATH = "/ws/v1"

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

@Singleton
class RealtimeWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val serverConfigHolder: ServerConfigHolder,
    private val tokenStore: TokenStore,
    private val keystoreManager: KeystoreManager,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<WsEvent> = _events

    private var webSocket: WebSocket? = null
    private var heartbeatWatchdog: Job? = null
    private var authTimeoutJob: Job? = null
    private val userRequestedDisconnect = AtomicBoolean(false)
    private var reconnectAttempt = 0
    private val pendingSubscriptions = mutableSetOf<String>()

    /** Token this connection attempt authenticated (or is authenticating) with - set right before
     *  opening the socket, sent as the required auth message once [onOpen] fires. See [openSocket]. */
    private var connectingToken: String? = null

    /** True from the moment [connect] is called until [disconnect] is explicitly called - NOT
     *  reset on a plain disconnect/reconnect cycle, since [scheduleReconnect] handling that is
     *  still "connect() was asked for and is being honored". Guards against a real, live-confirmed
     *  bug: connect() is called from several places (after PIN unlock, on cold start, from the
     *  alarm push foreground service) with no coordination between them - without this guard, each
     *  call unconditionally opened a brand new WebSocket without closing any existing one, leaking
     *  multiple concurrent connections to the server. Only the most-recently-opened one was ever
     *  referenced by [webSocket] again, so subscribe()/send() calls went to that one while an
     *  earlier, now-orphaned-but-still-open connection could easily be the one the server actually
     *  had a subscription recorded against - live values and command confirmations then silently
     *  never arrived, even though the visible connection state looked fine. */
    private val isActive = AtomicBoolean(false)

    fun connect() {
        userRequestedDisconnect.set(false)
        if (!isActive.compareAndSet(false, true)) {
            return // already connected, or a connect/reconnect cycle is already in flight
        }
        scope.launch { openSocket() }
    }

    fun disconnect() {
        userRequestedDisconnect.set(true)
        isActive.set(false)
        heartbeatWatchdog?.cancel()
        authTimeoutJob?.cancel()
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
        val restBase = serverConfigHolder.baseUrl ?: run { isActive.set(false); return }
        val token = tokenStore.getAccessToken() ?: run { isActive.set(false); return }
        connectingToken = token

        // Defensive: never leave a previous raw socket dangling before opening a new one, even if
        // some future caller ever manages to reach here without going through connect()'s isActive
        // guard - see that field's own doc for the live-confirmed connection-leak this prevents.
        webSocket?.cancel()

        // OkHttp's HttpUrl only accepts http/https schemes (it rejects ws/wss outright) - this is
        // intentional on OkHttp's part: newWebSocket() performs the protocol upgrade internally
        // over a plain http(s) URL, there is no separate ws(s) scheme to build here.
        val wsUrl = restBase.resolve("ws/v1") ?: run { isActive.set(false); return }

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, listener)
    }

    /** Signs the access token with the device Keystore key - see WsAuthDto's own doc for the exact
     *  canonical string, mirroring every REST request's signature (RequestSigningInterceptor). */
    private suspend fun sendAuthMessage(webSocket: WebSocket, token: String) {
        val timestamp = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val bodyHashHex = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).toHexString()
        val canonical = "$WS_AUTH_SIGNATURE_METHOD\n$WS_AUTH_SIGNATURE_PATH\n$timestamp\n$nonce\n$bodyHashHex"
        val signatureBytes = keystoreManager.sign(canonical.toByteArray(Charsets.UTF_8))
        val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

        webSocket.send(
            json.encodeToString(
                WsAuthDto.serializer(),
                WsAuthDto(accessToken = token, timestamp = timestamp, nonce = nonce, signature = signatureBase64),
            ),
        )
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
            // The raw socket is open, but NOT yet authenticated: the server (RealtimeGateway) never
            // reads a query-param token on the upgrade request - it requires this explicit message
            // as the first thing sent, and force-closes the connection after 5s (AUTH_TIMEOUT_MS) if
            // it never arrives. Everything that depends on being truly connected (WsEvent.Connected,
            // flushing pendingSubscriptions, the heartbeat watchdog) waits for the server's "auth_ok"
            // reply in handleMessage below instead of firing here.
            val token = connectingToken
            if (token == null) {
                webSocket.close(NORMAL_CLOSURE, "no token")
                return
            }
            scope.launch { sendAuthMessage(webSocket, token) }
            startAuthTimeoutWatchdog(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleMessage(bytes.utf8())
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            heartbeatWatchdog?.cancel()
            authTimeoutJob?.cancel()
            val willReconnect = !userRequestedDisconnect.get()
            scope.launch { _events.emit(WsEvent.Disconnected(willReconnect)) }
            if (willReconnect) scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
            heartbeatWatchdog?.cancel()
            authTimeoutJob?.cancel()
            val willReconnect = !userRequestedDisconnect.get()
            scope.launch { _events.emit(WsEvent.Disconnected(willReconnect)) }
            if (willReconnect) scheduleReconnect()
        }
    }

    private fun handleMessage(text: String) {
        val type = runCatching { json.decodeFromString(WsEnvelopeDto.serializer(), text).type }.getOrNull() ?: return

        // The server's reply to our WsAuthDto - not a WsEvent itself, it's what actually flips this
        // connection from "socket open" to "usable" (see the comment in onOpen above).
        if (type == "auth_ok") {
            onAuthenticated()
            heartbeatReceivedAt = System.currentTimeMillis()
            return
        }

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

            // "error" (e.g. AUTH_REQUIRED) is always followed by the server closing the socket, so
            // onClosed's own reconnect handling already covers it - nothing extra to do here.
            else -> null // unknown/unhandled message types are ignored defensively rather than crashing the pipe
        }
        heartbeatReceivedAt = System.currentTimeMillis()
        event?.let { scope.launch { _events.emit(it) } }
    }

    private fun onAuthenticated() {
        authTimeoutJob?.cancel()
        reconnectAttempt = 0
        scope.launch { _events.emit(WsEvent.Connected) }
        if (pendingSubscriptions.isNotEmpty()) {
            subscribe(pendingSubscriptions.toSet())
        }
        startHeartbeatWatchdog()
    }

    /**
     * Guards the "socket opened, auth message sent, but the server never replies at all - neither
     * auth_ok nor an error+close" case. Live-confirmed as a real, severe bug: nothing else was
     * watching this state (startHeartbeatWatchdog only ever starts AFTER onAuthenticated fires),
     * so a connection stuck here just sat open forever, doing nothing. Before the isActive guard
     * above existed, this was accidentally papered over - some other uncoordinated connect() call
     * from elsewhere in the app would eventually spawn a fresh, unstuck connection alongside it.
     * Once isActive started correctly refusing those duplicate attempts, a connection landing in
     * this exact stuck state had no way to ever recover on its own again (confirmed live: "letzte
     * Verbindung" stuck 6 hours in the past, Diagnose log showing repeated
     * "disconnected(willReconnect=true)" that never actually reconnects afterward).
     */
    private fun startAuthTimeoutWatchdog(openedSocket: WebSocket) {
        authTimeoutJob?.cancel()
        authTimeoutJob = scope.launch {
            delay(AUTH_ACK_TIMEOUT_MS)
            openedSocket.cancel()
            if (webSocket === openedSocket) {
                webSocket = null
            }
            if (!userRequestedDisconnect.get()) {
                _events.emit(WsEvent.Disconnected(true))
                scheduleReconnect()
            }
        }
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
        // Comfortably above the server's own 5s AUTH_TIMEOUT_MS (RealtimeGateway) plus margin for
        // the Keystore signing operation and network latency, but short enough that a truly stuck
        // connection (server never replies at all) doesn't sit unusable for long before recovering.
        const val AUTH_ACK_TIMEOUT_MS = 10_000L
    }
}
