package com.mobilecontrol.app.ui.dashboards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilecontrol.app.R
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.SizeClass
import com.mobilecontrol.app.domain.model.Widget
import com.mobilecontrol.app.domain.model.WidgetType
import com.mobilecontrol.app.domain.repository.ConnectionState
import com.mobilecontrol.app.ui.widgets.WidgetHost
import com.mobilecontrol.app.ui.widgets.WidgetState

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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(displayColumns),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    items(layout.widgets, key = { it.id }, span = { GridItemSpan(it.w.coerceIn(1, displayColumns)) }) { widget ->
                        WidgetCell(
                            widget = widget,
                            state = state,
                            editMode = state.editMode,
                            onToggle = { on -> widget.objectId?.let { id -> viewModel.sendCommand(id, on) } },
                            onRemove = { viewModel.removeWidget(widget.id) },
                            onMoveUp = { viewModel.moveWidget(widget.id, -1) },
                            onMoveDown = { viewModel.moveWidget(widget.id, 1) },
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

@Composable
private fun WidgetCell(
    widget: Widget,
    state: DashboardEditorUiState,
    editMode: Boolean,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onGrow: () -> Unit,
    onShrink: () -> Unit,
) {
    val widgetState = deriveWidgetState(widget, state)
    val catalogItem = state.catalog.firstOrNull { it.id == widget.objectId }

    Column(modifier = Modifier.padding(4.dp).height((120 * widget.h.coerceAtLeast(1)).dp)) {
        WidgetHost(
            widget = widget,
            state = widgetState,
            modifier = Modifier.fillMaxSize(),
            canWrite = catalogItem?.canWrite ?: (widget.objectId == null),
            isOnline = state.connectionState != ConnectionState.OFFLINE,
            onToggle = onToggle,
        )
        if (editMode) {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onMoveUp) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Nach oben") }
                IconButton(onClick = onMoveDown) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Nach unten") }
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
