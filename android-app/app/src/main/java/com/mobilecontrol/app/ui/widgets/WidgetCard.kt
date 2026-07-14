package com.mobilecontrol.app.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mobilecontrol.app.ui.theme.StatusError
import com.mobilecontrol.app.ui.theme.StatusLive
import com.mobilecontrol.app.ui.theme.StatusOffline
import com.mobilecontrol.app.ui.theme.StatusPending
import com.mobilecontrol.app.ui.theme.StatusStale

fun WidgetState.borderColor(): Color = when (this) {
    is WidgetState.Live, is WidgetState.CommandConfirmed -> StatusLive
    is WidgetState.Stale -> StatusStale
    is WidgetState.Offline -> StatusOffline
    is WidgetState.NoPermission, is WidgetState.ObjectMissing, is WidgetState.CommandFailed -> StatusError
    is WidgetState.CommandPending -> StatusPending
    WidgetState.Loading -> StatusOffline
}

/** Shared frame every widget type renders inside, so the WidgetState is always visually consistent. */
@Composable
fun WidgetCard(
    title: String,
    state: WidgetState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        border = BorderStroke(2.dp, state.borderColor()),
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            Box(modifier = Modifier.padding(top = 4.dp), contentAlignment = Alignment.CenterStart) {
                // Non-interactive states replace the widget body entirely. Command
                // pending/confirmed/failed still render the widget's own content (e.g. the switch
                // stays visible and usable) - those types are expected to show their own overlay
                // icon for the command status, on top of the border color set above.
                when (state) {
                    WidgetState.Loading -> Text("…", style = MaterialTheme.typography.bodyMedium)
                    is WidgetState.Offline -> StatusRow(Icons.Filled.CloudOff, "Offline")
                    is WidgetState.NoPermission -> StatusRow(Icons.Filled.Lock, "Kein Zugriff")
                    is WidgetState.ObjectMissing -> StatusRow(Icons.Filled.Help, "Objekt nicht gefunden")
                    else -> content()
                }
            }
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = StatusError)
        Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
    }
}
