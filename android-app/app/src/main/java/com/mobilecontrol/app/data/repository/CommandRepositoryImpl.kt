package com.mobilecontrol.app.data.repository

import com.mobilecontrol.app.data.remote.ApiService
import com.mobilecontrol.app.data.remote.RealtimeWebSocketClient
import com.mobilecontrol.app.data.remote.WsEvent
import com.mobilecontrol.app.data.remote.dto.CommandRequestDto
import com.mobilecontrol.app.data.remote.safeApiCall
import com.mobilecontrol.app.data.remote.toJsonElement
import com.mobilecontrol.app.domain.model.CommandStatus
import com.mobilecontrol.app.domain.repository.AppNotification
import com.mobilecontrol.app.domain.repository.CommandRepository
import com.mobilecontrol.app.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val webSocketClient: RealtimeWebSocketClient,
    private val notificationRepository: NotificationRepository,
) : CommandRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _commandStates = MutableStateFlow<Map<String, CommandStatus>>(emptyMap())
    override val commandStates: StateFlow<Map<String, CommandStatus>> = _commandStates

    /**
     * Maps the server-facing commandId of an in-flight *retry* back to the original/"public"
     * commandId that [sendCommand] returned to its caller. A retry must use a fresh commandId/nonce
     * (the server treats a repeated commandId as an idempotent no-op, so resending the same id
     * would not actually trigger a second attempt) - but the ViewModel/UI layer only ever learned
     * about the *original* commandId, so [commandStates] keeps publishing status updates under that
     * original id for the retry's whole lifetime. Entries are removed once the retry reaches a
     * terminal status. ConcurrentHashMap because the WS-event collector and the timeout watcher both
     * touch it from the IO-dispatcher thread pool.
     */
    private val retryServerIdToPublicId = ConcurrentHashMap<String, String>()

    init {
        scope.launch {
            webSocketClient.events.collect { event ->
                if (event is WsEvent.CommandResult) {
                    val mappedPublicId = retryServerIdToPublicId[event.commandId]
                    val publicId = mappedPublicId ?: event.commandId
                    val status = CommandStatus.fromWireName(event.status)
                    _commandStates.value = _commandStates.value + (publicId to status)
                    if (mappedPublicId != null && status.isTerminal) {
                        retryServerIdToPublicId.remove(event.commandId)
                    }
                    if (status == CommandStatus.REJECTED || status == CommandStatus.BLOCKED) {
                        notifyCommandFailure(status)
                    }
                }
            }
        }
    }

    override suspend fun sendCommand(objectId: String, value: Any?, confirmed: Boolean): Result<String> {
        val publicCommandId = UUID.randomUUID().toString()
        _commandStates.value = _commandStates.value + (publicCommandId to CommandStatus.ACCEPTED)

        val result = postCommand(publicCommandId, objectId, value, confirmed)
        result.onFailure {
            _commandStates.value = _commandStates.value + (publicCommandId to CommandStatus.REJECTED)
            return Result.failure(it)
        }

        scope.launch { watchForTimeout(publicCommandId, objectId, value, confirmed, isRetry = false) }
        return Result.success(publicCommandId)
    }

    private suspend fun postCommand(serverCommandId: String, objectId: String, value: Any?, confirmed: Boolean): Result<Unit> {
        val request = CommandRequestDto(
            commandId = serverCommandId,
            objectId = objectId,
            value = value.toJsonElement(),
            timestamp = Instant.now().toString(),
            nonce = UUID.randomUUID().toString(),
            confirmed = confirmed,
        )
        return safeApiCall { apiService.sendCommand(request) }.map { }
    }

    /**
     * Watches [publicCommandId] for a confirmation within [COMMAND_TIMEOUT_MS]. REJECTED/BLOCKED
     * are final server-side decisions - the WS handler above already marks the command terminal and
     * notifies the user as soon as one of those arrives, so `current.isTerminal` is already true by
     * the time this delay elapses and the function returns below without ever retrying them
     * (resending would just reproduce the same rejection). Only an actual TIMEOUT - most likely a
     * lost WebSocket confirmation event rather than a real rejection - is worth one automatic retry.
     */
    private suspend fun watchForTimeout(publicCommandId: String, objectId: String, value: Any?, confirmed: Boolean, isRetry: Boolean) {
        delay(COMMAND_TIMEOUT_MS)
        val current = _commandStates.value[publicCommandId]
        if (current == null || current.isTerminal) return

        if (isRetry) {
            // The retry itself also went unconfirmed - give up for good, exactly one retry allowed.
            _commandStates.value = _commandStates.value + (publicCommandId to CommandStatus.TIMEOUT)
            notifyCommandFailure(CommandStatus.TIMEOUT)
            return
        }

        // First timeout: silently retry once with a new commandId/nonce before bothering the user.
        val retryServerId = UUID.randomUUID().toString()
        retryServerIdToPublicId[retryServerId] = publicCommandId
        val retryResult = postCommand(retryServerId, objectId, value, confirmed)
        retryResult.onFailure {
            retryServerIdToPublicId.remove(retryServerId)
            _commandStates.value = _commandStates.value + (publicCommandId to CommandStatus.TIMEOUT)
            notifyCommandFailure(CommandStatus.TIMEOUT)
            return
        }
        scope.launch { watchForTimeout(publicCommandId, objectId, value, confirmed, isRetry = true) }
    }

    private fun notifyCommandFailure(status: CommandStatus) {
        notificationRepository.push(
            AppNotification(
                id = UUID.randomUUID().toString(),
                title = "Befehl nicht ausgeführt",
                body = "Ein Befehl wurde vom Server nicht bestätigt (${status.name}).",
                timestamp = System.currentTimeMillis(),
                severity = AppNotification.Severity.ERROR,
                read = false,
            ),
        )
    }

    private companion object {
        const val COMMAND_TIMEOUT_MS = 15_000L
    }
}
