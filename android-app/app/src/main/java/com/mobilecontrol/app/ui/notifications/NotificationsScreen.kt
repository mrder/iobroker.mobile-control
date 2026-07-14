package com.mobilecontrol.app.ui.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilecontrol.app.R
import com.mobilecontrol.app.domain.repository.AppNotification
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel = hiltViewModel()) {
    val notifications by viewModel.notifications.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.start_tab_notifications)) },
                actions = { TextButton(onClick = { viewModel.clearAll() }) { Text("Alle löschen") } },
            )
        },
    ) { padding ->
        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Keine Meldungen")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(notifications, key = { it.id }) { notification ->
                    NotificationRow(notification) { viewModel.markRead(notification.id) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: AppNotification, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(notification.title) },
        supportingContent = { Text(notification.body) },
        trailingContent = { Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(notification.timestamp))) },
    )
    if (!notification.read) {
        TextButton(onClick = onClick) { Text("Als gelesen markieren") }
    }
}
