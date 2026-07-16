package com.mobilecontrol.app.ui.dashboards

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilecontrol.app.R
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.SizeClass
import com.mobilecontrol.app.domain.model.Widget
import com.mobilecontrol.app.domain.model.WidgetType
import com.mobilecontrol.app.domain.repository.ConnectionState
import com.mobilecontrol.app.ui.widgets.WidgetHost
import com.mobilecontrol.app.ui.widgets.WidgetState
import kotlin.math.roundToInt

/** Row height per grid unit - matches the previous fixed per-widget height convention (120dp * h). */
private val GRID_ROW_HEIGHT = 120.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditorScreen(
    viewModel: DashboardEditorViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val dashboard = state.dashboard

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dashboard?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
                actions = {
                    if (state.editMode) {
                        IconButton(onClick = { viewModel.showAddWidgetDialog(true) }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.dashboard_editor_add_widget))
                        }
                        IconButton(onClick = { viewModel.save() }) {
                            Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.dashboard_editor_save))
                        }
                    } else {
                        IconButton(onClick = { viewModel.toggleEditMode() }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.connectionState == ConnectionState.OFFLINE) {
                OfflineBanner()
            }

            SizeClassSelector(selected = state.sizeClass, onSelect = viewModel::selectSizeClass)

            val layout = state.currentLayout
            if (layout == null || layout.widgets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Widgets in diesem Layout.")
                }
            } else {
                // The grid always renders at most 4 physical columns regardless of the layout's
                // configured column count (which can go up to 12 for "expanded") - widget spans are
                // clamped to the same number so a wide widget can never exceed the visible grid.
                val displayColumns = layout.columns.coerceIn(1, 4)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    DashboardGrid(
                        widgets = layout.widgets,
                        columns = displayColumns,
                        editMode = state.editMode,
                        onMoveWidget = viewModel::moveWidgetTo,
                    ) { widget ->
                        WidgetCell(
                            widget = widget,
                            state = state,
                            editMode = state.editMode,
                            modifier = Modifier.fillMaxSize(),
                            onCommand = { value, confirmed ->
                                widget.objectId?.let { id -> viewModel.sendCommand(id, value, confirmed) }
                            },
                            onRemove = { viewModel.removeWidget(widget.id) },
                            onGrow = { viewModel.resizeWidget(widget.id, 1, 0) },
                            onShrink = { viewModel.resizeWidget(widget.id, -1, 0) },
                        )
                    }
                }
            }
        }
    }

    if (state.showAddWidgetDialog) {
        AddWidgetDialog(
            catalog = state.catalog,
            onDismiss = { viewModel.showAddWidgetDialog(false) },
            onAdd = { item, type, title -> viewModel.addWidget(item, type, title) },
        )
    }

    if (state.revisionConflict) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.dashboard_editor_revision_conflict_title)) },
            text = { Text(stringResource(R.string.dashboard_editor_revision_conflict_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveConflictOverwrite() }) { Text(stringResource(R.string.dashboard_editor_overwrite)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveConflictDiscard() }) { Text(stringResource(R.string.dashboard_editor_discard)) }
            },
        )
    }
}

@Composable
private fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.common_offline),
            modifier = Modifier.padding(8.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SizeClassSelector(selected: SizeClass, onSelect: (SizeClass) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        SizeClass.entries.forEachIndexed { index, sizeClass ->
            SegmentedButton(
                selected = selected == sizeClass,
                onClick = { onSelect(sizeClass) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = SizeClass.entries.size),
            ) {
                Text(sizeClass.wireName)
            }
        }
    }
}

/**
 * Absolute-position grid: unlike the previous `LazyVerticalGrid` (which only ever reflected widget
 * *order*, never `Widget.x`/`y`), every widget is placed at its actual grid cell so dragging has a
 * real effect the rest of the UI (and a later re-open of the same dashboard) also sees.
 *
 * Not lazy - dashboards have at most a few dozen widgets, so a plain absolutely-positioned [Box]
 * plus the caller's own [androidx.compose.foundation.verticalScroll] is simpler than wiring up a
 * lazy layout that would also have to cooperate with in-progress drag gestures.
 */
@Composable
private fun DashboardGrid(
    widgets: List<Widget>,
    columns: Int,
    editMode: Boolean,
    onMoveWidget: (widgetId: String, newX: Int, newY: Int) -> Unit,
    cellContent: @Composable (Widget) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val cellWidth: Dp = maxWidth / columns
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val rowHeightPx = with(density) { GRID_ROW_HEIGHT.toPx() }
        val rowCount = (widgets.maxOfOrNull { it.y + it.h } ?: 0).coerceAtLeast(1)

        Box(modifier = Modifier.fillMaxWidth().height(GRID_ROW_HEIGHT * rowCount)) {
            widgets.forEach { widget ->
                key(widget.id) {
                    var dragOffset by remember(widget.id) { mutableStateOf(Offset.Zero) }
                    var isDragging by remember(widget.id) { mutableStateOf(false) }
                    var isInvalidTarget by remember(widget.id) { mutableStateOf(false) }

                    val clampedW = widget.w.coerceIn(1, columns)
                    val baseX = widget.x.coerceIn(0, (columns - clampedW).coerceAtLeast(0))
                    val baseOffsetPx = Offset(baseX * cellWidthPx, widget.y * rowHeightPx)

                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (baseOffsetPx.x + dragOffset.x).roundToInt(),
                                    (baseOffsetPx.y + dragOffset.y).roundToInt(),
                                )
                            }
                            .size(cellWidth * clampedW, GRID_ROW_HEIGHT * widget.h.coerceAtLeast(1))
                            .zIndex(if (isDragging) 1f else 0f)
                            .then(
                                if (editMode) {
                                    // `widgets` (not just this widget's own x/y/w/h) is part of the key so a
                                    // change to any *other* widget's rect (resize, add, remove) invalidates the
                                    // running gesture detector too - detectDragGestures() loops internally
                                    // across multiple drag gestures without pointerInput relaunching on its
                                    // own, so without this the live collision preview below could compare
                                    // against a stale snapshot of the other widgets' positions.
                                    Modifier.pointerInput(widget.id, columns, widget.x, widget.y, widget.w, widget.h, widgets) {
                                        detectDragGestures(
                                            onDragStart = { isDragging = true },
                                            onDragCancel = {
                                                isDragging = false
                                                isInvalidTarget = false
                                                dragOffset = Offset.Zero
                                            },
                                            onDragEnd = {
                                                isDragging = false
                                                isInvalidTarget = false
                                                val newX = ((baseOffsetPx.x + dragOffset.x) / cellWidthPx).roundToInt()
                                                val newY = ((baseOffsetPx.y + dragOffset.y) / rowHeightPx).roundToInt()
                                                onMoveWidget(widget.id, newX, newY)
                                                dragOffset = Offset.Zero
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount
                                                // Live "would this drop be rejected?" preview, purely
                                                // for the red-border feedback below - the ViewModel
                                                // re-checks authoritatively against the latest state
                                                // once the gesture actually ends (see moveWidgetTo).
                                                val candidateX = ((baseOffsetPx.x + dragOffset.x) / cellWidthPx)
                                                    .roundToInt().coerceIn(0, (columns - clampedW).coerceAtLeast(0))
                                                val candidateY = ((baseOffsetPx.y + dragOffset.y) / rowHeightPx)
                                                    .roundToInt().coerceAtLeast(0)
                                                isInvalidTarget = widgets.any { other ->
                                                    other.id != widget.id &&
                                                        candidateX < other.x + other.w && candidateX + clampedW > other.x &&
                                                        candidateY < other.y + other.h && candidateY + widget.h > other.y
                                                }
                                            },
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (isDragging && isInvalidTarget) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.error)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        cellContent(widget)
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCell(
    widget: Widget,
    state: DashboardEditorUiState,
    editMode: Boolean,
    modifier: Modifier = Modifier,
    onCommand: (value: Any?, confirmed: Boolean) -> Unit,
    onRemove: () -> Unit,
    onGrow: () -> Unit,
    onShrink: () -> Unit,
) {
    val widgetState = deriveWidgetState(widget, state)
    val catalogItem = state.catalog.firstOrNull { it.id == widget.objectId }

    Column(modifier = modifier.padding(4.dp)) {
        WidgetHost(
            widget = widget,
            state = widgetState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            canWrite = catalogItem?.canWrite ?: (widget.objectId == null),
            isOnline = state.connectionState != ConnectionState.OFFLINE,
            catalogItem = catalogItem,
            onCommand = onCommand,
        )
        if (editMode) {
            // Reordering now happens by dragging the widget itself (see DashboardGrid) - only
            // size (+/−, since drag doesn't cover resizing) and delete remain as button actions.
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onShrink) { Text("−") }
                TextButton(onClick = onGrow) { Text("+") }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.dashboard_editor_remove_widget)) }
            }
        }
    }
}

private fun deriveWidgetState(widget: Widget, state: DashboardEditorUiState): WidgetState {
    if (state.connectionState == ConnectionState.OFFLINE) {
        val cached = widget.objectId?.let { state.liveValues[it] }
        return if (cached != null) WidgetState.Stale(cached.value, cached.lastChange) else WidgetState.Offline
    }
    if (widget.objectId == null) return WidgetState.Live(null, System.currentTimeMillis())

    val commandId = state.pendingCommandByObjectId[widget.objectId]
    val commandStatus = commandId?.let { state.commandStates[it] }
    val live = state.liveValues[widget.objectId]

    return when {
        commandStatus != null && !commandStatus.isTerminal -> WidgetState.CommandPending(live?.value)
        commandStatus == com.mobilecontrol.app.domain.model.CommandStatus.CONFIRMED -> WidgetState.CommandConfirmed(live?.value)
        commandStatus != null && commandStatus.isTerminal -> WidgetState.CommandFailed(live?.value)
        live == null -> WidgetState.Loading
        System.currentTimeMillis() - live.lastChange > com.mobilecontrol.app.ui.widgets.STALE_THRESHOLD_MS -> WidgetState.Stale(live.value, live.lastChange)
        else -> WidgetState.Live(live.value, live.lastChange)
    }
}

@Composable
private fun AddWidgetDialog(
    catalog: List<ObjectCatalogItem>,
    onDismiss: () -> Unit,
    onAdd: (ObjectCatalogItem?, WidgetType, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ObjectCatalogItem?>(null) }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(WidgetType.TEXT_VALUE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_editor_add_widget)) },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Objekt suchen") })
                val filtered = catalog.filter { it.name.contains(query, ignoreCase = true) }.take(20)
                filtered.forEach { item ->
                    ListItem(
                        headlineContent = { Text(item.name) },
                        modifier = Modifier.padding(0.dp),
                        supportingContent = { Text(item.path.joinToString("/")) },
                        trailingContent = { if (selected?.id == item.id) Text("✓") },
                    )
                    TextButton(onClick = {
                        selected = item
                        title = item.name
                        type = item.suggestedWidgets.firstOrNull()?.let { WidgetType.fromSuggestion(it) }
                            ?: (if (item.canWrite) WidgetType.SWITCH else WidgetType.TEXT_VALUE)
                    }) { Text("Auswählen") }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titel") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(selected, type, title.ifBlank { "Widget" }) }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
