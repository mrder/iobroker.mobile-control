package com.mobilecontrol.app.ui.dashboards

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilecontrol.app.domain.model.ApiErrorCode
import com.mobilecontrol.app.domain.model.ApiException
import com.mobilecontrol.app.domain.model.CommandStatus
import com.mobilecontrol.app.domain.model.Dashboard
import com.mobilecontrol.app.domain.model.DashboardLayout
import com.mobilecontrol.app.domain.model.LiveValue
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.SizeClass
import com.mobilecontrol.app.domain.model.Widget
import com.mobilecontrol.app.domain.model.WidgetType
import com.mobilecontrol.app.domain.repository.CommandRepository
import com.mobilecontrol.app.domain.repository.ConnectionState
import com.mobilecontrol.app.domain.repository.DashboardRepository
import com.mobilecontrol.app.domain.repository.ObjectCatalogRepository
import com.mobilecontrol.app.domain.repository.StateRepository
import com.mobilecontrol.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class DashboardEditorUiState(
    val dashboard: Dashboard? = null,
    val editMode: Boolean = false,
    val sizeClass: SizeClass = SizeClass.COMPACT,
    val catalog: List<ObjectCatalogItem> = emptyList(),
    val showAddWidgetDialog: Boolean = false,
    val revisionConflict: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val liveValues: Map<String, LiveValue> = emptyMap(),
    val commandStates: Map<String, CommandStatus> = emptyMap(),
    /** objectId -> most recently sent commandId, so a widget can look up its own command's status. */
    val pendingCommandByObjectId: Map<String, String> = emptyMap(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
) {
    val currentLayout: DashboardLayout?
        get() = dashboard?.layoutFor(sizeClass)
}

@HiltViewModel
class DashboardEditorViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val catalogRepository: ObjectCatalogRepository,
    private val stateRepository: StateRepository,
    private val commandRepository: CommandRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val dashboardId: String = checkNotNull(savedStateHandle.get<String>(Routes.DASHBOARD_EDITOR_ARG))

    private val local = MutableStateFlow(DashboardEditorUiState())

    val uiState: StateFlow<DashboardEditorUiState> = combine(
        local,
        stateRepository.liveValues,
        commandRepository.commandStates,
        stateRepository.connectionState,
    ) { localState, live, commands, connection ->
        localState.copy(liveValues = live, commandStates = commands, connectionState = connection)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardEditorUiState())

    init {
        viewModelScope.launch {
            val dashboard = dashboardRepository.getDashboard(dashboardId)
            local.update { it.copy(dashboard = dashboard) }
            subscribeCurrentLayoutObjects()
        }
        viewModelScope.launch {
            catalogRepository.observeCatalog().collect { catalog -> local.update { it.copy(catalog = catalog) } }
        }
    }

    private fun subscribeCurrentLayoutObjects() {
        val ids = local.value.currentLayout?.widgets?.mapNotNull { it.objectId }?.toSet() ?: emptySet()
        if (ids.isNotEmpty()) {
            stateRepository.subscribe(ids)
            viewModelScope.launch { stateRepository.fetchInitialStates(ids.toList()) }
        }
    }

    fun toggleEditMode() = local.update { it.copy(editMode = !it.editMode) }

    fun selectSizeClass(sizeClass: SizeClass) {
        local.update { it.copy(sizeClass = sizeClass) }
        subscribeCurrentLayoutObjects()
    }

    fun showAddWidgetDialog(show: Boolean) = local.update { it.copy(showAddWidgetDialog = show) }

    fun addWidget(catalogItem: ObjectCatalogItem?, type: WidgetType, title: String) {
        val current = local.value.dashboard ?: return
        val layout = current.layoutFor(local.value.sizeClass)
        val newWidget = Widget(
            id = UUID.randomUUID().toString(),
            objectId = catalogItem?.id,
            type = type,
            title = title,
            x = 0,
            y = layout.widgets.size, // append below existing widgets; real placement is future drag&drop work
            w = 2,
            h = 1,
            config = catalogItem?.unit?.let { mapOf("unit" to it) } ?: emptyMap(),
        )
        updateLayout(local.value.sizeClass) { it.copy(widgets = it.widgets + newWidget) }
        local.update { it.copy(showAddWidgetDialog = false) }
        catalogItem?.let { stateRepository.subscribe(setOf(it.id)) }
    }

    fun removeWidget(widgetId: String) {
        updateLayout(local.value.sizeClass) { it.copy(widgets = it.widgets.filterNot { w -> w.id == widgetId }) }
    }

    fun moveWidget(widgetId: String, delta: Int) {
        updateLayout(local.value.sizeClass) { layout ->
            val list = layout.widgets.toMutableList()
            val index = list.indexOfFirst { it.id == widgetId }
            val newIndex = (index + delta).coerceIn(0, list.lastIndex)
            if (index >= 0 && newIndex != index) {
                val item = list.removeAt(index)
                list.add(newIndex, item)
                list.forEachIndexed { i, w -> list[i] = w.copy(y = i) }
            }
            layout.copy(widgets = list)
        }
    }

    fun resizeWidget(widgetId: String, dw: Int, dh: Int) {
        updateLayout(local.value.sizeClass) { layout ->
            layout.copy(
                widgets = layout.widgets.map { w ->
                    if (w.id == widgetId) w.copy(w = (w.w + dw).coerceIn(1, layout.columns), h = (w.h + dh).coerceIn(1, 4)) else w
                },
            )
        }
    }

    private fun updateLayout(sizeClass: SizeClass, transform: (DashboardLayout) -> DashboardLayout) {
        local.update { state ->
            val dashboard = state.dashboard ?: return@update state
            val updatedLayouts = dashboard.layouts.map { if (it.sizeClass == sizeClass) transform(it) else it }
            state.copy(dashboard = dashboard.copy(layouts = updatedLayouts))
        }
    }

    fun sendCommand(objectId: String, value: Any?, confirmed: Boolean = false) {
        viewModelScope.launch {
            commandRepository.sendCommand(objectId, value, confirmed).onSuccess { commandId ->
                local.update { it.copy(pendingCommandByObjectId = it.pendingCommandByObjectId + (objectId to commandId)) }
            }
        }
    }

    fun save() {
        val dashboard = local.value.dashboard ?: return
        viewModelScope.launch {
            local.update { it.copy(isSaving = true, saveError = null) }
            val result = dashboardRepository.updateDashboard(dashboard)
            result.fold(
                onSuccess = { saved -> local.update { it.copy(isSaving = false, dashboard = saved, editMode = false) } },
                onFailure = { error ->
                    val isConflict = (error as? ApiException)?.errorCode == ApiErrorCode.REVISION_CONFLICT
                    local.update {
                        it.copy(isSaving = false, revisionConflict = isConflict, saveError = if (isConflict) null else error.message)
                    }
                },
            )
        }
    }

    fun resolveConflictOverwrite() {
        val dashboard = local.value.dashboard ?: return
        viewModelScope.launch {
            local.update { it.copy(revisionConflict = false, isSaving = true) }
            // Force-write by bumping past whatever revision the server currently holds.
            val serverCopy = dashboardRepository.getDashboard(dashboard.id)
            val forced = dashboard.copy(revision = (serverCopy?.revision ?: dashboard.revision) + 1)
            dashboardRepository.updateDashboard(forced).fold(
                onSuccess = { saved -> local.update { it.copy(isSaving = false, dashboard = saved, editMode = false) } },
                onFailure = { error -> local.update { it.copy(isSaving = false, saveError = error.message) } },
            )
        }
    }

    fun resolveConflictDiscard() {
        viewModelScope.launch {
            val dashboard = local.value.dashboard ?: return@launch
            val fresh = dashboardRepository.getDashboard(dashboard.id)
            local.update { it.copy(revisionConflict = false, dashboard = fresh, editMode = false) }
        }
    }
}
