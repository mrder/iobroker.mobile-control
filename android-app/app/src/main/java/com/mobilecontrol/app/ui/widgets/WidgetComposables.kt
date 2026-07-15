package com.mobilecontrol.app.ui.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mobilecontrol.app.ui.theme.StatusLive
import com.mobilecontrol.app.ui.theme.StatusOffline
import java.util.Locale
import kotlin.math.roundToInt

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

/**
 * A single fire-and-forget command: unlike SwitchWidget there is no persistent on/off state to
 * reflect (the underlying object is momentary, e.g. a doorbell/scene-trigger role) - the widget
 * only shows the shared PENDING/CONFIRMED/FAILED overlay from [state] while a command is in flight.
 */
@Composable
fun MomentaryButtonWidget(
    title: String,
    state: WidgetState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPress, enabled = enabled) { Text("Auslösen") }
            CommandOverlayIcon(state)
        }
    }
}

@Composable
fun SliderWidget(
    title: String,
    state: WidgetState,
    min: Double,
    max: Double,
    step: Double?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (Double) -> Unit,
) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        val externalValue = (state.currentValue() as? Number)?.toDouble() ?: min
        // Local drag state: the thumb position while the user is actively dragging must not be
        // overwritten by a server-driven value push (WidgetState.Live), otherwise the thumb would
        // jump back under the user's finger mid-gesture. Once dragging ends, external updates are
        // allowed to resync the displayed value again.
        var isDragging by remember { mutableStateOf(false) }
        var sliderPosition by remember { mutableStateOf(externalValue.toFloat()) }

        LaunchedEffect(externalValue, isDragging) {
            if (!isDragging) sliderPosition = externalValue.toFloat()
        }

        // steps = number of discrete stops *between* the endpoints (Compose Slider convention);
        // step == null or 0 means a continuous slider.
        val steps = if (step == null || step <= 0.0) {
            0
        } else {
            (((max - min) / step).roundToInt() - 1).coerceAtLeast(0)
        }

        Column {
            Slider(
                value = sliderPosition,
                onValueChange = {
                    isDragging = true
                    sliderPosition = it
                },
                onValueChangeFinished = {
                    isDragging = false
                    // Only the final value is sent to the server - not every drag tick - to stay
                    // well under any command rate limit.
                    onValueChangeFinished(sliderPosition.toDouble())
                },
                valueRange = min.toFloat()..max.toFloat(),
                steps = steps,
                enabled = enabled,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(String.format(Locale.getDefault(), "%.1f", sliderPosition), style = MaterialTheme.typography.bodyMedium)
                CommandOverlayIcon(state)
            }
        }
    }
}

@Composable
fun RollerShutterWidget(
    title: String,
    state: WidgetState,
    min: Double,
    max: Double,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSetPosition: (Double) -> Unit,
) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        val value = (state.currentValue() as? Number)?.toDouble()
        Column {
            val positionLabel = if (value != null && max != min) {
                val percent = ((value - min) / (max - min) * 100).roundToInt().coerceIn(0, 100)
                "$percent %"
            } else {
                "—"
            }
            Text(positionLabel, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onSetPosition(min) }, enabled = enabled) { Text("Auf") }
                // "Stopp" has no corresponding server command in the API contract - only an
                // absolute position can be sent, there is no separate stop/halt state. This button
                // is a deliberately UI-only affordance (familiar three-button shutter layout) and
                // sends nothing to the server.
                TextButton(onClick = { /* intentionally no-op, see comment above */ }, enabled = enabled) { Text("Stopp") }
                TextButton(onClick = { onSetPosition(max) }, enabled = enabled) { Text("Ab") }
                CommandOverlayIcon(state)
            }
        }
    }
}

@Composable
fun ThermostatWidget(
    title: String,
    state: WidgetState,
    min: Double,
    max: Double,
    step: Double,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onSetTarget: (Double) -> Unit,
) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        val value = (state.currentValue() as? Number)?.toDouble()
        val effectiveStep = if (step <= 0.0) 0.5 else step

        Column {
            Text(
                text = if (value != null) String.format(Locale.getDefault(), "%.1f °C", value) else "—",
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onSetTarget(((value ?: min) - effectiveStep).coerceIn(min, max)) },
                    enabled = enabled,
                ) { Text("−") }
                TextButton(
                    onClick = { onSetTarget(((value ?: min) + effectiveStep).coerceIn(min, max)) },
                    enabled = enabled,
                ) { Text("+") }
                CommandOverlayIcon(state)
            }
        }
    }
}
