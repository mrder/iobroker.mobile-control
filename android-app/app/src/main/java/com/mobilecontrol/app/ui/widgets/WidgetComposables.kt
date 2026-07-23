package com.mobilecontrol.app.ui.widgets

import android.graphics.BitmapFactory
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilecontrol.app.domain.model.HistoryEntry
import com.mobilecontrol.app.ui.theme.StatusError
import com.mobilecontrol.app.ui.theme.StatusLive
import com.mobilecontrol.app.ui.theme.StatusOffline
import com.mobilecontrol.app.ui.theme.StatusStale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
fun TemperatureWidget(title: String, state: WidgetState, unit: String? = null, modifier: Modifier = Modifier) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        val value = (state.currentValue() as? Number)?.toDouble()
        Text(
            text = if (value != null) String.format(Locale.getDefault(), "%.1f %s", value, unit ?: "°C") else "—",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
fun HumidityWidget(title: String, state: WidgetState, unit: String? = null, modifier: Modifier = Modifier) {
    WidgetCard(title = title, state = state, modifier = modifier) {
        val value = (state.currentValue() as? Number)?.toDouble()
        Text(
            text = if (value != null) String.format(Locale.getDefault(), "%.0f %s", value, unit ?: "%") else "—",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/**
 * A purely visual heading/divider (WidgetType.LABEL) - no objectId, no live state, so it skips
 * WidgetCard entirely (that chrome exists to convey live/stale/offline status, which doesn't
 * apply here) and just renders the title as a section header with a thin rule underneath.
 */
@Composable
fun LabelWidget(title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
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

/** Internal load state for [HistoryWidget] - see the doc comment on that function for why this
 * isn't modeled as [WidgetState] instead. */
private sealed interface HistoryLoadState {
    data object Loading : HistoryLoadState
    /** Covers both an empty result and any load failure (403/404/network/...) - the widget shows
     * the same "no history available" message for all of them, matching the MVP's error-detail scope. */
    data object Unavailable : HistoryLoadState
    data class Success(val entries: List<HistoryEntry>) : HistoryLoadState
}

/**
 * Real history widget backed by GET /api/v1/history. Unlike every other widget type it does not
 * derive its visuals from the shared [WidgetState] (that sealed interface models the live
 * WebSocket-driven value stream: live/stale/pending/confirmed/...); history is a one-shot REST
 * fetch on becoming visible, so it keeps its own tiny loading/unavailable/success state instead -
 * folding it into WidgetState would only add an awkward, meaningless mapping (e.g. "which
 * WidgetState is a finished history fetch?").
 *
 * Rendered as a simple newest-first "HH:mm  value" list rather than a drawn sparkline/chart: the
 * task explicitly allows either and a list needs no pixel-math/scaling logic to get right, which
 * matters here since there is no compiler in this environment to catch mistakes in that math.
 */
@Composable
fun HistoryWidget(
    title: String,
    objectId: String?,
    unit: String?,
    modifier: Modifier = Modifier,
    viewModel: HistoryWidgetViewModel = hiltViewModel(),
) {
    var loadState by remember(objectId) { mutableStateOf<HistoryLoadState>(HistoryLoadState.Loading) }

    LaunchedEffect(objectId) {
        if (objectId == null) {
            loadState = HistoryLoadState.Unavailable
            return@LaunchedEffect
        }
        loadState = HistoryLoadState.Loading
        val to = Instant.now()
        val from = to.minus(HISTORY_LOOKBACK_HOURS, ChronoUnit.HOURS)
        val result = viewModel.loadHistory(objectId, from.toString(), to.toString(), HISTORY_LIMIT)
        loadState = result.fold(
            onSuccess = { entries -> if (entries.isEmpty()) HistoryLoadState.Unavailable else HistoryLoadState.Success(entries) },
            onFailure = { HistoryLoadState.Unavailable },
        )
    }

    // Neither StatusLive (misleadingly implies a fresh live value) nor Loading/NoPermission/
    // ObjectMissing (those replace the widget body with WidgetCard's own hardcoded text) fit here -
    // Stale's amber border reads reasonably as "historical, not live data" while still letting this
    // composable's own content() run for every load state.
    WidgetCard(title = title, state = WidgetState.Stale(null, 0L), modifier = modifier) {
        when (val current = loadState) {
            HistoryLoadState.Loading -> Text("Lädt…", style = MaterialTheme.typography.bodyMedium)
            HistoryLoadState.Unavailable -> Text("Kein Verlauf verfügbar", style = MaterialTheme.typography.bodyMedium)
            is HistoryLoadState.Success -> HistoryEntryList(current.entries, unit)
        }
    }
}

private val historyTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun HistoryEntryList(entries: List<HistoryEntry>, unit: String?) {
    val newestFirst = remember(entries) { entries.sortedByDescending { it.timestampMillis }.take(HISTORY_DISPLAY_COUNT) }
    Column {
        newestFirst.forEach { entry ->
            val time = Instant.ofEpochMilli(entry.timestampMillis)
                .atZone(ZoneId.systemDefault())
                .format(historyTimeFormatter)
            Text(
                text = "$time  ${entry.value}${unit?.let { " $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** How far back the initial history fetch looks, per widget spec ("z. B. letzte 24h"). */
private const val HISTORY_LOOKBACK_HOURS = 24L

/** Kept moderate per widget spec ("limit moderat wie 100"), well under the server's max of 2000. */
private const val HISTORY_LIMIT = 100

/** Only the most recent entries are shown in the compact widget card, oldest fetched entries are
 * still used if the list ever grows a scroll/expand affordance later. */
private const val HISTORY_DISPLAY_COUNT = 8

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

/**
 * Read-only alarm indicator (MASTERKONZEPT.md "Alarmkachel" / TODO.md "Quittierung"/"Entwarnung").
 * No server write happens here: not every alarm source has a companion writable ack-state in
 * ioBroker, so "Quittieren" only mutes this widget's own alert styling locally (via
 * [AlarmWidgetViewModel]) rather than assuming one exists. On every true<->false transition it
 * also pushes an in-app notification ("Alarm aktiv" / "Entwarnung") through the same
 * NotificationRepository the connection/permission events use, so alarms show up in Meldungen
 * even for a dashboard the user isn't currently looking at.
 */
@Composable
fun AlarmWidget(
    title: String,
    objectId: String?,
    state: WidgetState,
    modifier: Modifier = Modifier,
    viewModel: AlarmWidgetViewModel = hiltViewModel(),
) {
    val active = state.currentValue() as? Boolean ?: false
    val isLiveish = state is WidgetState.Live || state is WidgetState.Stale
    val acknowledgedIds by viewModel.acknowledged.collectAsState()
    val acknowledged = objectId != null && objectId in acknowledgedIds

    LaunchedEffect(objectId, active, isLiveish) {
        if (objectId != null && isLiveish) {
            viewModel.observe(objectId, title, active)
        }
    }

    WidgetCard(title = title, state = state, modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tint = when {
                    !active -> StatusLive
                    acknowledged -> StatusStale
                    else -> StatusError
                }
                Icon(
                    imageVector = if (active) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = tint,
                )
                Text(
                    text = when {
                        !active -> "Kein Alarm"
                        acknowledged -> "Alarm (quittiert)"
                        else -> "Alarm aktiv"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (active && !acknowledged) {
                TextButton(onClick = { objectId?.let(viewModel::acknowledge) }) { Text("Quittieren") }
            }
        }
    }
}

/** Internal load state for [CameraWidget], same reasoning as [HistoryLoadState] - a snapshot is a
 * one-shot REST fetch, not a WebSocket-driven live value, so it isn't modeled as [WidgetState]. */
private sealed interface CameraLoadState {
    data object Loading : CameraLoadState
    data object Unavailable : CameraLoadState
    data class Success(val bitmap: ImageBitmap, val loadedAtMillis: Long) : CameraLoadState
}

/**
 * Kamera-Snapshot widget (MASTERKONZEPT.md §19): shows the current snapshot of an ioBroker
 * camera-backed object via GET /api/v1/objects/{id}/snapshot (see [CameraRepository] /
 * src/camera/index.ts for the two source formats supported). "Zeitstempel" is when the app last
 * successfully fetched the image (there is no reliable, adapter-independent way to know when the
 * underlying camera itself took the picture), "Aktualisieren" re-fetches on demand, tapping the
 * image opens it in a simple fullscreen [Dialog], and a failed/unsupported fetch shows a
 * [CameraLoadState.Unavailable] placeholder rather than crashing on a bad decode.
 */
@Composable
fun CameraWidget(
    title: String,
    objectId: String?,
    modifier: Modifier = Modifier,
    viewModel: CameraWidgetViewModel = hiltViewModel(),
) {
    var loadState by remember(objectId) { mutableStateOf<CameraLoadState>(CameraLoadState.Loading) }
    var refreshTrigger by remember(objectId) { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(objectId, refreshTrigger) {
        if (objectId == null) {
            loadState = CameraLoadState.Unavailable
            return@LaunchedEffect
        }
        loadState = CameraLoadState.Loading
        val result = viewModel.loadSnapshot(objectId)
        loadState = result.fold(
            onSuccess = { bytes ->
                val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                if (bitmap != null) {
                    CameraLoadState.Success(bitmap.asImageBitmap(), System.currentTimeMillis())
                } else {
                    CameraLoadState.Unavailable
                }
            },
            onFailure = { CameraLoadState.Unavailable },
        )
    }

    WidgetCard(title = title, state = WidgetState.Stale(null, 0L), modifier = modifier) {
        when (val current = loadState) {
            CameraLoadState.Loading -> Text("Lädt…", style = MaterialTheme.typography.bodyMedium)
            CameraLoadState.Unavailable -> EmbedUnavailable(message = "Kein Snapshot verfügbar", onRetry = { refreshTrigger++ })
            is CameraLoadState.Success -> CameraSnapshotView(
                bitmap = current.bitmap,
                loadedAtMillis = current.loadedAtMillis,
                onRefresh = { refreshTrigger++ },
                onOpenFullscreen = { fullscreen = true },
            )
        }
    }

    if (fullscreen) {
        val current = loadState
        if (current is CameraLoadState.Success) {
            Dialog(onDismissRequest = { fullscreen = false }) {
                Image(
                    bitmap = current.bitmap,
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth().clickable { fullscreen = false },
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            fullscreen = false
        }
    }
}

@Composable
private fun CameraSnapshotView(
    bitmap: ImageBitmap,
    loadedAtMillis: Long,
    onRefresh: () -> Unit,
    onOpenFullscreen: () -> Unit,
) {
    Column {
        Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clickable(onClick = onOpenFullscreen),
                contentScale = ContentScale.Crop,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = historyTimeFormatter.format(Instant.ofEpochMilli(loadedAtMillis).atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.bodySmall,
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
            }
            IconButton(onClick = onOpenFullscreen) {
                Icon(Icons.Filled.Fullscreen, contentDescription = "Vollbild")
            }
        }
    }
}

/** Shared "couldn't load" row for every embed-style widget (camera, url image, web page) - each
 *  passes its own message since the underlying reason differs (no snapshot vs. no image vs. the
 *  target URL couldn't be resolved), but the layout and retry affordance are identical. */
@Composable
private fun EmbedUnavailable(message: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.BrokenImage, contentDescription = null, tint = StatusError)
        Text(text = message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
        IconButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = "Erneut versuchen")
        }
    }
}

/** Internal load state for [UrlImageWidget], same reasoning as [CameraLoadState]. */
private sealed interface UrlImageLoadState {
    data object Loading : UrlImageLoadState
    data object Unavailable : UrlImageLoadState
    data class Success(val bitmap: ImageBitmap, val loadedAtMillis: Long) : UrlImageLoadState
}

/**
 * Renders the proxied content of an admin-approved URL embed (GET /api/v1/url-embeds/{id}/content)
 * as an image - the "screenshot URL"/camera-link use case for a source that isn't backed by any
 * ioBroker state. Structurally identical to [CameraWidget], just sourced by a urlEmbedId
 * (widget.config["urlEmbedId"]) instead of an objectId.
 */
@Composable
fun UrlImageWidget(
    title: String,
    urlEmbedId: String?,
    modifier: Modifier = Modifier,
    viewModel: UrlEmbedWidgetViewModel = hiltViewModel(),
) {
    var loadState by remember(urlEmbedId) { mutableStateOf<UrlImageLoadState>(UrlImageLoadState.Loading) }
    var refreshTrigger by remember(urlEmbedId) { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(urlEmbedId, refreshTrigger) {
        if (urlEmbedId == null) {
            loadState = UrlImageLoadState.Unavailable
            return@LaunchedEffect
        }
        loadState = UrlImageLoadState.Loading
        val result = viewModel.loadContent(urlEmbedId)
        loadState = result.fold(
            onSuccess = { bytes ->
                val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
                if (bitmap != null) {
                    UrlImageLoadState.Success(bitmap.asImageBitmap(), System.currentTimeMillis())
                } else {
                    UrlImageLoadState.Unavailable
                }
            },
            onFailure = { UrlImageLoadState.Unavailable },
        )
    }

    WidgetCard(title = title, state = WidgetState.Stale(null, 0L), modifier = modifier) {
        when (val current = loadState) {
            UrlImageLoadState.Loading -> Text("Lädt…", style = MaterialTheme.typography.bodyMedium)
            UrlImageLoadState.Unavailable -> EmbedUnavailable(message = "Kein Bild verfügbar", onRetry = { refreshTrigger++ })
            is UrlImageLoadState.Success -> CameraSnapshotView(
                bitmap = current.bitmap,
                loadedAtMillis = current.loadedAtMillis,
                onRefresh = { refreshTrigger++ },
                onOpenFullscreen = { fullscreen = true },
            )
        }
    }

    if (fullscreen) {
        val current = loadState
        if (current is UrlImageLoadState.Success) {
            Dialog(onDismissRequest = { fullscreen = false }) {
                Image(
                    bitmap = current.bitmap,
                    contentDescription = title,
                    modifier = Modifier.fillMaxWidth().clickable { fullscreen = false },
                    contentScale = ContentScale.Fit,
                )
            }
        } else {
            fullscreen = false
        }
    }
}

/** Internal load state for [WebPageWidget]. */
private sealed interface WebPageLoadState {
    data object Loading : WebPageLoadState
    data object Unavailable : WebPageLoadState
    data class Resolved(val url: String) : WebPageLoadState
}

/**
 * Embeds an admin-approved local web UI (GET /api/v1/url-embeds/{id}/resolve, then a plain
 * Android WebView navigation) - the "HTML frame" use case from the allowlist feature. The client
 * never picks the URL itself, only an id from the allowlist; once resolved, the WebView navigates
 * the LAN directly (a full page's own relative sub-resource requests can't realistically be
 * rewritten through a single-resource proxy - see UrlEmbedsService's own docs for why). JavaScript
 * is enabled since most small device web UIs need it to function - same trust level as opening
 * that same admin-approved URL in any other LAN browser tab, no bridge to app code is exposed.
 */
@Composable
fun WebPageWidget(
    title: String,
    urlEmbedId: String?,
    modifier: Modifier = Modifier,
    viewModel: UrlEmbedWidgetViewModel = hiltViewModel(),
) {
    var loadState by remember(urlEmbedId) { mutableStateOf<WebPageLoadState>(WebPageLoadState.Loading) }
    var refreshTrigger by remember(urlEmbedId) { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(urlEmbedId, refreshTrigger) {
        if (urlEmbedId == null) {
            loadState = WebPageLoadState.Unavailable
            return@LaunchedEffect
        }
        loadState = WebPageLoadState.Loading
        loadState = viewModel.resolveUrl(urlEmbedId).fold(
            onSuccess = { url -> WebPageLoadState.Resolved(url) },
            onFailure = { WebPageLoadState.Unavailable },
        )
    }

    WidgetCard(title = title, state = WidgetState.Stale(null, 0L), modifier = modifier) {
        when (val current = loadState) {
            WebPageLoadState.Loading -> Text("Lädt…", style = MaterialTheme.typography.bodyMedium)
            WebPageLoadState.Unavailable -> EmbedUnavailable(message = "Seite nicht verfügbar", onRetry = { refreshTrigger++ })
            is WebPageLoadState.Resolved -> Box(modifier = Modifier.fillMaxSize()) {
                EmbeddedWebView(url = current.url, modifier = Modifier.fillMaxSize())
                // A semi-transparent circular backing keeps the icon legible over arbitrary page
                // content - the WebView itself can't double as the tap target here (it needs to
                // keep receiving touches for the page's own scrolling/interaction).
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                ) {
                    IconButton(onClick = { fullscreen = true }) {
                        Icon(Icons.Filled.Fullscreen, contentDescription = "Vollbild")
                    }
                }
            }
        }
    }

    if (fullscreen) {
        val current = loadState
        if (current is WebPageLoadState.Resolved) {
            Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(text = title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp).weight(1f))
                            IconButton(onClick = { fullscreen = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Schließen")
                            }
                        }
                        EmbeddedWebView(url = current.url, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        } else {
            fullscreen = false
        }
    }
}

/** Fresh WebView per composition (inline widget and fullscreen dialog each get their own) - a
 *  live page's scroll/input state isn't worth the complexity of sharing a single View instance
 *  across two different parts of the composition, opening fullscreen just reloads the same URL. */
@Composable
private fun EmbeddedWebView(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
            }
        },
        update = { webView -> webView.loadUrl(url) },
    )
}
