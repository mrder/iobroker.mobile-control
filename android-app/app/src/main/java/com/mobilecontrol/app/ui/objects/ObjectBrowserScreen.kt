package com.mobilecontrol.app.ui.objects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilecontrol.app.R
import com.mobilecontrol.app.domain.model.ObjectCatalogItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectBrowserScreen(viewModel: ObjectBrowserViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(state.filteredObjects.map { it.id }) {
        val visibleIds = state.filteredObjects.take(50).map { it.id }.toSet()
        viewModel.subscribeVisible(visibleIds)
        onDispose { viewModel.unsubscribeVisible(visibleIds) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.objects_title)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                label = { Text(stringResource(R.string.objects_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoomFilterChip(state.rooms, state.selectedRoom, viewModel::setRoomFilter)
                RoleFilterChip(state.roles, state.selectedRole, viewModel::setRoleFilter)
            }

            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Checkbox(checked = state.writableOnly, onCheckedChange = viewModel::setWritableOnly)
                Text(
                    stringResource(R.string.objects_filter_writable_only),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

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
        }
    }
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
