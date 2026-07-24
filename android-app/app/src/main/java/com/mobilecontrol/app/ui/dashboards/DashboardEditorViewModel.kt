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
    val folderNames: Map<String, String> = emptyMap(),
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
            val dashboard = dashboardRepository.getDashboard(dashboardId)?.let(::withWidenedColumns)
            local.update { it.copy(dashboard = dashboard) }
            subscribeCurrentLayoutObjects()
        }
        viewModelScope.launch {
            catalogRepository.observeCatalog().collect { catalog -> local.update { it.copy(catalog = catalog) } }
        }
        viewModelScope.launch {
            catalogRepository.observeFolderNames().collect { names -> local.update { it.copy(folderNames = names) } }
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

    fun showAddWidgetDialog(show: Boolean) = local.update { it.copy(showAddWidgetDialog = show) }

    fun addWidget(catalogItem: ObjectCatalogItem?, type: WidgetType, title: String, urlEmbedId: String? = null) {
        val current = local.value.dashboard ?: return
        val layout = current.layoutFor(local.value.sizeClass)
        val defaultW = 2
        val defaultH = 1
        val (freeX, freeY) = GridPlacement.findFreeSlot(layout, defaultW, defaultH)
        val config = buildMap {
            catalogItem?.unit?.let { put("unit", it) }
            urlEmbedId?.let { put("urlEmbedId", it) }
        }
        val newWidget = Widget(
            id = UUID.randomUUID().toString(),
            objectId = catalogItem?.id,
            type = type,
            title = title,
            x = freeX,
            y = freeY,
            w = defaultW,
            h = defaultH,
            config = config,
        )
        updateLayout(local.value.sizeClass) { it.copy(widgets = it.widgets + newWidget) }
        local.update { it.copy(showAddWidgetDialog = false) }
        catalogItem?.let { stateRepository.subscribe(setOf(it.id)) }
    }

    fun removeWidget(widgetId: String) {
        updateLayout(local.value.sizeClass) { it.copy(widgets = it.widgets.filterNot { w -> w.id == widgetId }) }
    }

    /**
     * Moves widget [widgetId] to grid cell ([newX], [newY]) - the drop target of a drag gesture in
     * [DashboardEditorScreen]. [newX]/[newY] are clamped into the layout's bounds first. If the
     * resulting rectangle would overlap another widget, the move is rejected outright (layout
     * returned unchanged) rather than trying to shuffle/swap the colliding widget out of the way:
     * swapping can cascade (the widget being displaced may itself now overlap a third widget, and
     * so on) and picking a robust, unsurprising resolution for that gets complicated fast. Simply
     * refusing an occupied drop target is the simpler, more predictable rule - the UI reflects the
     * rejection by having the dragged widget snap back to its last committed position.
     */
    fun moveWidgetTo(widgetId: String, newX: Int, newY: Int) {
        updateLayout(local.value.sizeClass) { layout ->
            val target = layout.widgets.firstOrNull { it.id == widgetId } ?: return@updateLayout layout
            val clampedX = newX.coerceIn(0, (layout.columns - target.w).coerceAtLeast(0))
            val clampedY = newY.coerceAtLeast(0)
            if (clampedX == target.x && clampedY == target.y) return@updateLayout layout
            val proposed = target.copy(x = clampedX, y = clampedY)
            val collides = layout.widgets.any { other -> other.id != widgetId && GridPlacement.rectsOverlap(proposed, other) }
            if (collides) return@updateLayout layout
            layout.copy(widgets = layout.widgets.map { if (it.id == widgetId) proposed else it })
        }
    }

    /**
     * Replaces the previous +/- (always both dimensions, always ±1) resize buttons with a single
     * atomic update from the per-widget edit dialog: title, unit and size are edited together, and
     * width/height are set to absolute values instead of deltas so the dialog's own steppers can
     * move them independently (live-test feedback: coupling both dimensions per press made it
     * impossible to reach non-square shapes without over/undershooting one axis).
     *
     * [previewMode] and [tunnel] are only meaningful for WidgetType.WEB_VIEW - see
     * ui/widgets/WidgetComposables.kt#WebPageWidget. Harmless no-ops for every other widget type.
     * previewMode: "button" = tap-to-open only, no live mini-WebView in the grid tile; null/blank
     * = default live preview. tunnel: "on" = route the page through the adapter's tunnel instead
     * of direct LAN navigation; null/blank = default direct navigation.
     */
    fun updateWidget(widgetId: String, title: String, unit: String?, w: Int, h: Int, previewMode: String? = null, tunnel: String? = null) {
        updateLayout(local.value.sizeClass) { layout ->
            layout.copy(
                widgets = layout.widgets.map { widget ->
                    if (widget.id != widgetId) return@map widget
                    var config = if (unit.isNullOrBlank()) widget.config - "unit" else widget.config + ("unit" to unit)
                    config = if (previewMode.isNullOrBlank()) config - "previewMode" else config + ("previewMode" to previewMode)
                    config = if (tunnel.isNullOrBlank()) config - "tunnel" else config + ("tunnel" to tunnel)
                    widget.copy(
                        title = title.ifBlank { widget.title },
                        w = w.coerceIn(1, layout.columns),
                        h = h.coerceIn(1, MAX_WIDGET_ROWS),
                        config = config,
                    )
                },
            )
        }
    }

    private inline fun updateLayout(sizeClass: SizeClass, transform: (DashboardLayout) -> DashboardLayout) {
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
            val fresh = dashboardRepository.getDashboard(dashboard.id)?.let(::withWidenedColumns)
            local.update { it.copy(revisionConflict = false, dashboard = fresh, editMode = false) }
        }
    }

    companion object {
        /** Maximum height of a single widget, in grid rows - see [MIN_GRID_COLUMNS]. */
        const val MAX_WIDGET_ROWS = 8

        /**
         * Dashboards created before the grid was widened from 4 to 8 columns (live-test feedback:
         * 4 made every resize step too coarse) still carry `columns: 4` from the server. Rather
         * than a one-off server-side migration, every dashboard is bumped up to this width the
         * moment it's loaded into the editor - existing widgets keep their x/y/w/h (they just now
         * occupy less of the row than before, and can be resized wider), and the wider value is
         * persisted back on the next save.
         */
        private const val MIN_GRID_COLUMNS = 8

        private fun withWidenedColumns(dashboard: Dashboard): Dashboard =
            dashboard.copy(
                layouts = dashboard.layouts.map { layout ->
                    if (layout.columns >= MIN_GRID_COLUMNS) layout else layout.copy(columns = MIN_GRID_COLUMNS)
                },
            )
    }
}
