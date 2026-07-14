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

    init {
        scope.launch {
            webSocketClient.events.collect { event ->
                if (event is WsEvent.CommandResult) {
                    val status = CommandStatus.fromWireName(event.status)
                    _commandStates.value = _commandStates.value + (event.commandId to status)
                    if (status == CommandStatus.REJECTED || status == CommandStatus.BLOCKED) {
                        notifyCommandFailure(status)
                    }
                }
            }
        }
    }

    override suspend fun sendCommand(objectId: String, value: Any?): Result<String> {
        val commandId = UUID.randomUUID().toString()
        _commandStates.value = _commandStates.value + (commandId to CommandStatus.ACCEPTED)

        val request = CommandRequestDto(
            commandId = commandId,
            objectId = objectId,
            value = value.toJsonElement(),
            timestamp = Instant.now().toString(),
            nonce = UUID.randomUUID().toString(),
        )

        val result = safeApiCall { apiService.sendCommand(request) }
        result.onFailure {
            _commandStates.value = _commandStates.value + (commandId to CommandStatus.REJECTED)
            return Result.failure(it)
        }

        scope.launch { watchForTimeout(commandId) }
        return Result.success(commandId)
    }

    private suspend fun watchForTimeout(commandId: String) {
        delay(COMMAND_TIMEOUT_MS)
        val current = _commandStates.value[commandId]
        if (current != null && !current.isTerminal) {
            _commandStates.value = _commandStates.value + (commandId to CommandStatus.TIMEOUT)
            notifyCommandFailure(CommandStatus.TIMEOUT)
        }
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
