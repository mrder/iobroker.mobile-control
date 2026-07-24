package com.mobilecontrol.app.ui.dashboards

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilecontrol.app.R
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.ObjectTreeNode
import com.mobilecontrol.app.domain.model.UrlEmbed
import com.mobilecontrol.app.domain.model.ValueType
import com.mobilecontrol.app.domain.model.Widget
import com.mobilecontrol.app.domain.model.WidgetType
import com.mobilecontrol.app.domain.model.buildObjectTree
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

            val layout = state.currentLayout
            if (layout == null || layout.widgets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Noch keine Widgets in diesem Layout.")
                }
            } else {
                // Sanity clamp only - the real column count comes from the layout (see
                // DashboardEditorViewModel.withWidenedColumns, which bumps every dashboard to at
                // least 8 on load); this just guards against a corrupt/unexpectedly huge value.
                val displayColumns = layout.columns.coerceIn(1, 16)
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
                            maxColumns = displayColumns,
                            modifier = Modifier.fillMaxSize(),
                            onCommand = { value, confirmed ->
                                widget.objectId?.let { id -> viewModel.sendCommand(id, value, confirmed) }
                            },
                            onRemove = { viewModel.removeWidget(widget.id) },
                            onSaveEdit = { title, unit, w, h, previewMode ->
                                viewModel.updateWidget(widget.id, title, unit, w, h, previewMode)
                            },
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
            onAdd = { item, type, title, urlEmbedId -> viewModel.addWidget(item, type, title, urlEmbedId) },
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
                                                isInvalidTarget = GridPlacement.wouldOverlapAny(
                                                    x = candidateX,
                                                    y = candidateY,
                                                    w = clampedW,
                                                    h = widget.h,
                                                    others = widgets,
                                                    excludeId = widget.id,
                                                )
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

/** Widget types where a unit suffix ("°C", "kWh", ...) is meaningful and worth exposing in the
 *  edit dialog. Switches, buttons, cameras, embeds and labels have no numeric value to suffix. */
private val UNIT_CAPABLE_TYPES = setOf(
    WidgetType.TEXT_VALUE,
    WidgetType.TEMPERATURE,
    WidgetType.HUMIDITY,
    WidgetType.HISTORY,
    WidgetType.SLIDER,
    WidgetType.THERMOSTAT,
)

@Composable
private fun WidgetCell(
    widget: Widget,
    state: DashboardEditorUiState,
    editMode: Boolean,
    maxColumns: Int,
    modifier: Modifier = Modifier,
    onCommand: (value: Any?, confirmed: Boolean) -> Unit,
    onRemove: () -> Unit,
    onSaveEdit: (title: String, unit: String?, w: Int, h: Int, previewMode: String?) -> Unit,
) {
    val widgetState = deriveWidgetState(widget, state)
    val catalogItem = state.catalog.firstOrNull { it.id == widget.objectId }
    var showEditDialog by remember(widget.id) { mutableStateOf(false) }

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
            // Reordering happens by dragging the widget itself (see DashboardGrid); title, unit
            // and size are all edited together in one dialog (see WidgetEditDialog) rather than
            // cramming multiple +/- stepper pairs into this footer row.
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.dashboard_editor_edit_widget))
                }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.dashboard_editor_remove_widget)) }
            }
        }
    }

    if (showEditDialog) {
        WidgetEditDialog(
            widget = widget,
            maxColumns = maxColumns,
            showUnitField = widget.type in UNIT_CAPABLE_TYPES,
            showPreviewToggle = widget.type == WidgetType.WEB_VIEW,
            onDismiss = { showEditDialog = false },
            onSave = { title, unit, w, h, previewMode ->
                onSaveEdit(title, unit, w, h, previewMode)
                showEditDialog = false
            },
        )
    }
}

@Composable
private fun WidgetEditDialog(
    widget: Widget,
    maxColumns: Int,
    showUnitField: Boolean,
    showPreviewToggle: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, unit: String?, w: Int, h: Int, previewMode: String?) -> Unit,
) {
    var title by remember { mutableStateOf(widget.title) }
    var unit by remember { mutableStateOf(widget.config["unit"].orEmpty()) }
    var width by remember { mutableStateOf(widget.w.coerceIn(1, maxColumns)) }
    var height by remember { mutableStateOf(widget.h.coerceIn(1, DashboardEditorViewModel.MAX_WIDGET_ROWS)) }
    var livePreview by remember { mutableStateOf(widget.config["previewMode"] != "button") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_editor_edit_widget)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showUnitField) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text(stringResource(R.string.dashboard_editor_unit_label)) },
                        placeholder = { Text("°C, kWh, %…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showPreviewToggle) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.dashboard_editor_live_preview_label), modifier = Modifier.weight(1f))
                        Switch(checked = livePreview, onCheckedChange = { livePreview = it })
                    }
                    Text(
                        stringResource(R.string.dashboard_editor_live_preview_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                SizeStepper(
                    label = stringResource(R.string.dashboard_editor_width_label),
                    value = width,
                    range = 1..maxColumns,
                    onChange = { width = it },
                )
                SizeStepper(
                    label = stringResource(R.string.dashboard_editor_height_label),
                    value = height,
                    range = 1..DashboardEditorViewModel.MAX_WIDGET_ROWS,
                    onChange = { height = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val previewMode = if (showPreviewToggle && !livePreview) "button" else null
                onSave(title, unit.ifBlank { null }, width, height, previewMode)
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun SizeStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { if (value > range.first) onChange(value - 1) }, enabled = value > range.first) {
            Text("−")
        }
        Text(value.toString(), modifier = Modifier.padding(horizontal = 8.dp))
        IconButton(onClick = { if (value < range.last) onChange(value + 1) }, enabled = value < range.last) {
            Text("+")
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

private val WIDGET_TYPE_LABELS: Map<WidgetType, String> = mapOf(
    WidgetType.TEXT_VALUE to "Textwert",
    WidgetType.TEMPERATURE to "Temperatur",
    WidgetType.HUMIDITY to "Feuchte",
    WidgetType.BOOLEAN_STATUS to "Status",
    WidgetType.SWITCH to "Schalter",
    WidgetType.HISTORY to "Verlauf",
    WidgetType.MOMENTARY_BUTTON to "Taster",
    WidgetType.SLIDER to "Schieberegler",
    WidgetType.ROLLER_SHUTTER to "Rollladen",
    WidgetType.THERMOSTAT to "Thermostat",
    WidgetType.ALARM to "Alarm",
    WidgetType.CAMERA to "Kamera",
    WidgetType.URL_IMAGE to "URL-Bild",
    WidgetType.WEB_VIEW to "Web-Seite",
    WidgetType.LABEL to "Überschrift",
)

private fun suggestedWidgetType(item: ObjectCatalogItem): WidgetType =
    item.suggestedWidgets.firstOrNull()?.let { WidgetType.fromSuggestion(it) }
        ?: (if (item.canWrite) WidgetType.SWITCH else WidgetType.TEXT_VALUE)

/** Which source the step-1 picker currently browses - objects (the ioBroker catalog, as before)
 *  or the admin-managed URL-embed allowlist (see UrlEmbedPickerViewModel). */
private enum class PickerSource { OBJECT, URL_EMBED }

/**
 * Live-test feedback: the previous picker was a flat, unranked, 20-item-capped list with only a
 * name search - no folders, no way to narrow by data type. Reuses the same folder-tree approach
 * as ObjectBrowserScreen (ObjectTreeNode/buildObjectTree), plus a value-type filter row (Bool/
 * Number/String/JSON), in a large Dialog instead of a cramped AlertDialog - a tree genuinely
 * doesn't fit in a small dialog. Picking a leaf switches to a second, simpler step (widget type +
 * title) rather than cramming both into one screen at once. A second step-1 tab lets you pick an
 * admin-approved URL embed instead of an ioBroker object (see [PickerSource]).
 */
@Composable
private fun AddWidgetDialog(
    catalog: List<ObjectCatalogItem>,
    onDismiss: () -> Unit,
    onAdd: (ObjectCatalogItem?, WidgetType, String, String?) -> Unit,
    urlEmbedViewModel: UrlEmbedPickerViewModel = hiltViewModel(),
) {
    val urlEmbeds by urlEmbedViewModel.embeds.collectAsState()

    var pickerSource by remember { mutableStateOf(PickerSource.OBJECT) }
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<ValueType?>(null) }
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableStateOf<ObjectCatalogItem?>(null) }
    var selectedUrlEmbed by remember { mutableStateOf<UrlEmbed?>(null) }
    var creatingLabel by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var widgetType by remember { mutableStateOf(WidgetType.TEXT_VALUE) }

    val hasFilter = query.isNotBlank() || typeFilter != null
    val typeFiltered = remember(catalog, typeFilter) {
        if (typeFilter == null) catalog else catalog.filter { it.valueType == typeFilter }
    }
    val filtered = remember(typeFiltered, query) {
        typeFiltered.filter { item ->
            query.isBlank() || item.name.contains(query, true) || item.path.joinToString("/").contains(query, true)
        }.take(200)
    }
    val tree = remember(typeFiltered) { buildObjectTree(typeFiltered) }

    fun selectItem(item: ObjectCatalogItem) {
        selectedUrlEmbed = null
        creatingLabel = false
        selected = item
        title = item.name
        widgetType = suggestedWidgetType(item)
    }

    fun selectUrlEmbed(embed: UrlEmbed) {
        selected = null
        creatingLabel = false
        selectedUrlEmbed = embed
        title = embed.name
        widgetType = WidgetType.URL_IMAGE
    }

    fun startLabel() {
        selected = null
        selectedUrlEmbed = null
        creatingLabel = true
        title = ""
        widgetType = WidgetType.LABEL
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(stringResource(R.string.dashboard_editor_add_widget), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(12.dp))

                if (selected == null && selectedUrlEmbed == null && !creatingLabel) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PickerSourceChip(PickerSource.OBJECT, "Objekte", pickerSource) { pickerSource = it }
                        PickerSourceChip(PickerSource.URL_EMBED, "URL-Einbettungen", pickerSource) { pickerSource = it }
                        FilterChip(selected = false, onClick = { startLabel() }, label = { Text(stringResource(R.string.dashboard_editor_add_label)) })
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (pickerSource == PickerSource.OBJECT) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(stringResource(R.string.objects_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        ) {
                            ValueTypeFilterChip(null, "Alle", typeFilter) { typeFilter = it }
                            ValueTypeFilterChip(ValueType.BOOLEAN, "Bool", typeFilter) { typeFilter = it }
                            ValueTypeFilterChip(ValueType.NUMBER, "Zahl", typeFilter) { typeFilter = it }
                            ValueTypeFilterChip(ValueType.STRING, "Text", typeFilter) { typeFilter = it }
                            ValueTypeFilterChip(ValueType.JSON, "JSON", typeFilter) { typeFilter = it }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when {
                                catalog.isEmpty() -> Text(stringResource(R.string.objects_empty))
                                hasFilter && filtered.isEmpty() -> Text(stringResource(R.string.objects_empty))
                                hasFilter -> LazyColumn {
                                    items(filtered, key = { it.id }) { item ->
                                        PickerLeafRow(item = item, depth = 0, onClick = { selectItem(item) })
                                    }
                                }
                                else -> LazyColumn {
                                    pickerTreeItems(
                                        node = tree,
                                        depth = 0,
                                        expanded = expandedFolders,
                                        onToggle = { id -> expandedFolders = if (id in expandedFolders) expandedFolders - id else expandedFolders + id },
                                        onSelect = ::selectItem,
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (urlEmbeds.isEmpty()) {
                                Text(stringResource(R.string.dashboard_editor_no_url_embeds))
                            } else {
                                LazyColumn {
                                    items(urlEmbeds, key = { it.id }) { embed ->
                                        ListItem(
                                            modifier = Modifier.clickable { selectUrlEmbed(embed) },
                                            headlineContent = { Text(embed.name) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (selected != null) {
                    val item = selected!!
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(item.path.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    WidgetTypeSelector(selected = widgetType, onSelect = { widgetType = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titel") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { selected = null }) { Text(stringResource(R.string.dashboard_editor_pick_different_object)) }
                } else if (selectedUrlEmbed != null) {
                    val embed = selectedUrlEmbed!!
                    Text(embed.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.dashboard_editor_url_embed_label), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    WidgetTypeSelector(selected = widgetType, onSelect = { widgetType = it })
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Titel") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { selectedUrlEmbed = null }) { Text(stringResource(R.string.dashboard_editor_pick_different_embed)) }
                } else {
                    // creatingLabel: a heading has no backing object/embed and only one possible
                    // widget type, so this step just asks for the display text.
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.dashboard_editor_label_title_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { creatingLabel = false }) { Text(stringResource(R.string.dashboard_editor_pick_different_source)) }
                }

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                    if (selected != null || selectedUrlEmbed != null || creatingLabel) {
                        TextButton(
                            onClick = { onAdd(selected, widgetType, title.ifBlank { "Widget" }, selectedUrlEmbed?.id) },
                        ) { Text(stringResource(R.string.common_ok)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSourceChip(value: PickerSource, label: String, selected: PickerSource, onSelect: (PickerSource) -> Unit) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ValueTypeFilterChip(value: ValueType?, label: String, selected: ValueType?, onSelect: (ValueType?) -> Unit) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetTypeSelector(selected: WidgetType, onSelect: (WidgetType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    FilterChip(
        selected = true,
        onClick = { expanded = true },
        label = { Text(WIDGET_TYPE_LABELS[selected] ?: selected.name) },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        WidgetType.entries.forEach { type ->
            DropdownMenuItem(text = { Text(WIDGET_TYPE_LABELS[type] ?: type.name) }, onClick = { onSelect(type); expanded = false })
        }
    }
}

/** Same "cap indentation, don't push width negative" precaution as ObjectBrowserScreen's tree
 *  (see its own comment for the real crash that taught us this). A dedicated, lighter picker row
 *  (no live value, no write control) since this tree is purely for selecting an object. */
private const val PICKER_MAX_INDENT_LEVELS = 8
private val PICKER_INDENT_STEP = 16.dp
private fun pickerIndentFor(depth: Int) = PICKER_INDENT_STEP * minOf(depth, PICKER_MAX_INDENT_LEVELS)

private fun LazyListScope.pickerTreeItems(
    node: ObjectTreeNode,
    depth: Int,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onSelect: (ObjectCatalogItem) -> Unit,
) {
    for (folder in node.children) {
        val isOpen = folder.id in expanded
        item(key = "folder:${folder.id}") {
            ListItem(
                modifier = Modifier.padding(start = pickerIndentFor(depth)).clickable { onToggle(folder.id) },
                leadingContent = {
                    Row {
                        Icon(if (isOpen) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight, contentDescription = null)
                        Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                },
                headlineContent = { Text(folder.name, style = MaterialTheme.typography.titleSmall) },
            )
        }
        if (isOpen) {
            pickerTreeItems(folder, depth + 1, expanded, onToggle, onSelect)
        }
    }
    items(node.items, key = { "item:${it.id}" }) { catalogItem ->
        PickerLeafRow(item = catalogItem, depth = depth, onClick = { onSelect(catalogItem) })
    }
}

@Composable
private fun PickerLeafRow(item: ObjectCatalogItem, depth: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.padding(start = pickerIndentFor(depth)).clickable(onClick = onClick),
        headlineContent = { Text(item.name) },
        supportingContent = { Text(item.path.joinToString(" / "), style = MaterialTheme.typography.bodySmall) },
    )
}
