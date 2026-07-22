package com.mobilecontrol.app.ui.objects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.mobilecontrol.app.domain.model.LiveValue
import com.mobilecontrol.app.domain.model.ObjectCatalogItem
import com.mobilecontrol.app.domain.model.ObjectTreeNode
import com.mobilecontrol.app.domain.model.visibleLeafIds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectBrowserScreen(viewModel: ObjectBrowserViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var expandedFolders by remember { mutableStateOf(setOf<String>()) }

    // Unfiltered: only subscribe to leaf items actually visible given which folders are
    // expanded (collapsed folders may hide hundreds of items - no point live-subscribing to
    // those). Filtered: unchanged flat-list behavior, capped at 50 like before.
    val tree = state.tree
    val visibleIds = if (state.hasActiveFilter) {
        state.filteredObjects.take(50).map { it.id }.toSet()
    } else {
        tree.visibleLeafIds(expandedFolders).toSet()
    }

    DisposableEffect(visibleIds) {
        viewModel.subscribeVisible(visibleIds)
        onDispose { viewModel.unsubscribeVisible(visibleIds) }
    }

    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.objects_title)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search + a single filter icon (opens FilterDialog) instead of always-visible
            // room/role chip rows and a checkbox row - on a small tablet those 2-3 extra rows,
            // stacked on top of the TopAppBar and below the bottom NavigationBar, left almost no
            // vertical room for the actual list/tree content.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    label = { Text(stringResource(R.string.objects_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                val activeFilterCount = listOfNotNull(state.selectedRoom, state.selectedRole).size + if (state.writableOnly) 1 else 0
                BadgedBox(
                    badge = { if (activeFilterCount > 0) Badge { Text("$activeFilterCount") } },
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.objects_filter_title))
                    }
                }
            }

            if (showFilterDialog) {
                FilterDialog(
                    rooms = state.rooms,
                    roles = state.roles,
                    selectedRoom = state.selectedRoom,
                    selectedRole = state.selectedRole,
                    writableOnly = state.writableOnly,
                    onRoomSelect = viewModel::setRoomFilter,
                    onRoleSelect = viewModel::setRoleFilter,
                    onWritableOnlyChange = viewModel::setWritableOnly,
                    onDismiss = { showFilterDialog = false },
                )
            }

            if (state.allObjects.isEmpty()) {
                Text(
                    stringResource(R.string.objects_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (state.hasActiveFilter) {
                if (state.filteredObjects.isEmpty()) {
                    Text(
                        stringResource(R.string.objects_empty),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn {
                        items(state.filteredObjects, key = { it.id }) { item ->
                            ObjectListRow(item = item, liveValue = state.liveValues[item.id]?.value)
                        }
                    }
                }
            } else {
                LazyColumn {
                    objectTreeItems(
                        node = tree,
                        depth = 0,
                        expanded = expandedFolders,
                        onToggle = { id ->
                            expandedFolders = if (id in expandedFolders) expandedFolders - id else expandedFolders + id
                        },
                        liveValues = state.liveValues,
                    )
                }
            }
        }
    }
}

/** Recursively appends this node's folders and leaf items to a LazyListScope, depth-first,
 *  skipping subtrees of collapsed folders entirely (they're neither composed nor subscribed). */
private fun LazyListScope.objectTreeItems(
    node: ObjectTreeNode,
    depth: Int,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    liveValues: Map<String, LiveValue>,
) {
    for (folder in node.children) {
        val isOpen = folder.id in expanded
        item(key = "folder:${folder.id}") {
            ObjectFolderRow(name = folder.name, depth = depth, expanded = isOpen, onClick = { onToggle(folder.id) })
        }
        if (isOpen) {
            objectTreeItems(folder, depth + 1, expanded, onToggle, liveValues)
        }
    }
    items(node.items, key = { "item:${it.id}" }) { catalogItem ->
        Box(modifier = Modifier.padding(start = (depth * 16).dp)) {
            ObjectListRow(item = catalogItem, liveValue = liveValues[catalogItem.id]?.value)
        }
    }
}

@Composable
private fun ObjectFolderRow(name: String, depth: Int, expanded: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .padding(start = (depth * 16).dp)
            .clickable(onClick = onClick),
        leadingContent = {
            Row {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                    contentDescription = null,
                )
                Icon(imageVector = Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        },
        headlineContent = { Text(name, style = MaterialTheme.typography.titleSmall) },
    )
}

@Composable
private fun ObjectListRow(item: ObjectCatalogItem, liveValue: Any?) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = {
            Column {
                Text(item.path.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                Row {
                    if (item.canRead) Text("R", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp))
                    if (item.canWrite) Text("W", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp))
                    if (item.hasHistory) Text("H", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        trailingContent = {
            Text(
                text = liveValue?.let { "$it${item.unit?.let { u -> " $u" } ?: ""}" } ?: "—",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomFilterChip(rooms: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    FilterChip(
        selected = selected != null,
        onClick = { expanded = true },
        label = { Text(selected ?: stringResource(R.string.objects_filter_room)) },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("Alle") }, onClick = { onSelect(null); expanded = false })
        rooms.forEach { room ->
            DropdownMenuItem(text = { Text(room) }, onClick = { onSelect(room); expanded = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleFilterChip(roles: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    FilterChip(
        selected = selected != null,
        onClick = { expanded = true },
        label = { Text(selected ?: stringResource(R.string.objects_filter_role)) },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(text = { Text("Alle") }, onClick = { onSelect(null); expanded = false })
        roles.forEach { role ->
            DropdownMenuItem(text = { Text(role) }, onClick = { onSelect(role); expanded = false })
        }
    }
}

@Composable
private fun FilterDialog(
    rooms: List<String>,
    roles: List<String>,
    selectedRoom: String?,
    selectedRole: String?,
    writableOnly: Boolean,
    onRoomSelect: (String?) -> Unit,
    onRoleSelect: (String?) -> Unit,
    onWritableOnlyChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.objects_filter_title)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoomFilterChip(rooms, selectedRoom, onRoomSelect)
                    RoleFilterChip(roles, selectedRole, onRoleSelect)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = writableOnly, onCheckedChange = onWritableOnlyChange)
                    Text(stringResource(R.string.objects_filter_writable_only))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } },
        dismissButton = {
            TextButton(onClick = {
                onRoomSelect(null)
                onRoleSelect(null)
                onWritableOnlyChange(false)
            }) { Text(stringResource(R.string.objects_filter_reset)) }
        },
    )
}
