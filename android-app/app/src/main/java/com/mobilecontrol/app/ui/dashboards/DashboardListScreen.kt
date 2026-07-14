package com.mobilecontrol.app.ui.dashboards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.mobilecontrol.app.domain.model.Dashboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardListScreen(
    viewModel: DashboardListViewModel = hiltViewModel(),
    onOpenDashboard: (String) -> Unit,
) {
    val dashboards by viewModel.dashboards.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Dashboard?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.dashboards_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.dashboards_create))
            }
        },
    ) { padding ->
        if (dashboards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.dashboards_empty))
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(dashboards, key = { it.id }) { dashboard ->
                    DashboardRow(
                        dashboard = dashboard,
                        onOpen = { onOpenDashboard(dashboard.id) },
                        onSetStart = { viewModel.setStartDashboard(dashboard.id) },
                        onDuplicate = { viewModel.duplicateDashboard(dashboard) },
                        onDelete = { viewModel.deleteDashboard(dashboard.id) },
                        onRename = { renameTarget = dashboard },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateDashboardDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                viewModel.createDashboard(name, onOpenDashboard)
            },
        )
    }

    renameTarget?.let { target ->
        RenameDashboardDialog(
            currentName = target.name,
            onDismiss = { renameTarget = null },
            onRename = { newName ->
                viewModel.renameDashboard(target, newName)
                renameTarget = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardRow(
    dashboard: Dashboard,
    onOpen: () -> Unit,
    onSetStart: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(dashboard.name) },
        supportingContent = { Text("${dashboard.widgetCount} Widgets") },
        leadingContent = {
            IconButton(onClick = onSetStart) {
                Icon(
                    if (dashboard.isStartDashboard) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(R.string.dashboards_set_start),
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) { Text("⋮") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.dashboards_rename)) }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.dashboards_duplicate)) }, onClick = { menuExpanded = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.dashboards_delete)) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onOpen),
    )
    HorizontalDivider()
}

@Composable
private fun CreateDashboardDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboards_create)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun RenameDashboardDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboards_rename)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onRename(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
