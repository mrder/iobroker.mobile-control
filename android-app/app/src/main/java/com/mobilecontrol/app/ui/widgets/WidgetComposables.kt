package com.mobilecontrol.app.ui.widgets

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mobilecontrol.app.ui.theme.StatusLive
import com.mobilecontrol.app.ui.theme.StatusOffline
import java.util.Locale

private fun WidgetState.currentValue(): Any? = when (this) {
    is WidgetState.Live -> value
    is WidgetState.Stale -> value
    is WidgetState.CommandPending -> value
    is WidgetState.CommandConfirmed -> value
    is WidgetState.CommandFailed -> value
    else -> null
}

@Composable
fun TextValueWidget(title: String, unit: String?, state: WidgetState, modifier: Modifier = Modifier) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        val value = state.currentValue()
        Text(
            text = if (value != null) "$value${unit?.let { " $it" } ?: ""}" else "—",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
fun TemperatureWidget(title: String, state: WidgetState, modifier: Modifier = Modifier) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        val value = (state.currentValue() as? Number)?.toDouble()
        Text(
            text = if (value != null) String.format(Locale.getDefault(), "%.1f °C", value) else "—",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
fun HumidityWidget(title: String, state: WidgetState, modifier: Modifier = Modifier) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        val value = (state.currentValue() as? Number)?.toDouble()
        Text(
            text = if (value != null) String.format(Locale.getDefault(), "%.0f %%", value) else "—",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
fun BooleanStatusWidget(title: String, state: WidgetState, modifier: Modifier = Modifier) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        val on = state.currentValue() as? Boolean ?: false
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (on) Icons.Filled.CheckCircle else Icons.Filled.Circle,
                contentDescription = null,
                tint = if (on) StatusLive else StatusOffline,
            )
            Text(text = if (on) "Ein" else "Aus", modifier = Modifier, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun SwitchWidget(
    title: String,
    state: WidgetState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        val on = state.currentValue() as? Boolean ?: false
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = on, onCheckedChange = onToggle, enabled = enabled)
            CommandOverlayIcon(state)
        }
    }
}

@Composable
private fun CommandOverlayIcon(state: WidgetState) {
    when (state) {
        is WidgetState.CommandPending -> Text(" …", color = Color.Gray)
        is WidgetState.CommandConfirmed -> Text(" ✓", color = StatusLive)
        is WidgetState.CommandFailed -> Text(" ✗", color = MaterialTheme.colorScheme.error)
        else -> Unit
    }
}

@Composable
fun HistoryPlaceholderWidget(title: String, state: WidgetState, modifier: Modifier = Modifier) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        Text(text = "Verlauf folgt", style = MaterialTheme.typography.bodyMedium)
    }
}
