package com.mobilecontrol.app.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mobilecontrol.app.domain.model.HistoryEntry
import java.util.Locale

/** A single plotted point in canvas pixel space (origin top-left, y grows downward). */
data class ChartPoint(val x: Float, val y: Float)

/**
 * Maps numeric [HistoryEntry] samples onto a widthPx x heightPx canvas, oldest to newest. X is
 * placed by actual elapsed time (not sample index), so uneven sampling intervals still read
 * correctly; y is normalized against the samples' own min/max and inverted (canvas y grows
 * downward, but a higher value should plot higher on screen). Pure/no Compose dependency so it's
 * unit-testable without a Compose UI test harness - the composable below only adds drawing.
 */
fun computeLineChartPoints(entries: List<HistoryEntry>, widthPx: Float, heightPx: Float): List<ChartPoint> {
    val numeric = entries.mapNotNull { entry -> (entry.value as? Number)?.toDouble()?.let { entry.timestampMillis to it } }
        .sortedBy { it.first }
    if (numeric.size < 2) return emptyList()
    val minTime = numeric.first().first.toDouble()
    val maxTime = numeric.last().first.toDouble()
    val timeSpan = (maxTime - minTime).takeIf { it > 0.0 } ?: 1.0
    val minValue = numeric.minOf { it.second }
    val maxValue = numeric.maxOf { it.second }
    val valueSpan = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0
    return numeric.map { (time, value) ->
        val x = ((time - minTime) / timeSpan).toFloat() * widthPx
        val y = heightPx - ((value - minValue) / valueSpan).toFloat() * heightPx
        ChartPoint(x, y)
    }
}

/**
 * Maps boolean [HistoryEntry] samples onto a step-chart timeline: each sample holds its value
 * (drawn as a horizontal segment) until the next sample's timestamp, then steps vertically - the
 * usual way to read a switch/state history ("on" plots at the top). Returns a flat point list
 * meant to be connected with straight line segments in order (two points per transition: one to
 * hold the previous value up to the new x, one to step to the new value).
 */
fun computeStepChartPoints(entries: List<HistoryEntry>, widthPx: Float, heightPx: Float): List<ChartPoint> {
    val samples = entries.mapNotNull { entry -> (entry.value as? Boolean)?.let { entry.timestampMillis to it } }
        .sortedBy { it.first }
    if (samples.size < 2) return emptyList()
    val minTime = samples.first().first.toDouble()
    val maxTime = samples.last().first.toDouble()
    val timeSpan = (maxTime - minTime).takeIf { it > 0.0 } ?: 1.0
    fun xFor(time: Long) = ((time - minTime) / timeSpan).toFloat() * widthPx
    fun yFor(value: Boolean) = if (value) 0f else heightPx

    val (firstTime, firstValue) = samples.first()
    val points = mutableListOf(ChartPoint(xFor(firstTime), yFor(firstValue)))
    for (i in 1 until samples.size) {
        val (_, prevValue) = samples[i - 1]
        val (time, value) = samples[i]
        val x = xFor(time)
        points.add(ChartPoint(x, yFor(prevValue))) // hold the previous value up to this sample's time
        points.add(ChartPoint(x, yFor(value))) // then step to the new value
    }
    return points
}

private fun List<ChartPoint>.toPath(): Path = Path().apply {
    if (isEmpty()) return@apply
    moveTo(first().x, first().y)
    drop(1).forEach { lineTo(it.x, it.y) }
}

/**
 * Auto-scaling history chart (live-requested, 2026-07-31): numeric values render as a line
 * chart, boolean/switch values as a step chart, anything else (string/JSON history) falls back
 * to the plain [HistoryEntryList] this replaces - a chart doesn't meaningfully represent those.
 * Sizing comes entirely from [modifier]/the surrounding widget cell (via [Canvas]'s own
 * `size` in [androidx.compose.ui.graphics.drawscope.DrawScope]), so it reflows automatically
 * when the dashboard grid's column count or row height changes, without any fixed dp constants.
 */
@Composable
fun HistoryChart(entries: List<HistoryEntry>, unit: String?, modifier: Modifier = Modifier) {
    val numericEntries = remember(entries) { entries.filter { it.value is Number } }
    val booleanEntries = remember(entries) { entries.filter { it.value is Boolean } }
    when {
        numericEntries.size >= 2 -> NumericLineChart(numericEntries, unit, modifier)
        booleanEntries.size >= 2 -> BooleanStepChart(booleanEntries, modifier)
        else -> HistoryEntryList(entries, unit)
    }
}

@Composable
private fun NumericLineChart(numericEntries: List<HistoryEntry>, unit: String?, modifier: Modifier = Modifier) {
    val minValue = numericEntries.minOf { (it.value as Number).toDouble() }
    val maxValue = numericEntries.maxOf { (it.value as Number).toDouble() }
    val lineColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxSize()) {
        Text(formatChartValue(maxValue, unit), style = MaterialTheme.typography.bodySmall.scaledForWidget())
        Canvas(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 2.dp)) {
            val path = computeLineChartPoints(numericEntries, size.width, size.height).toPath()
            drawPath(path, color = lineColor, style = Stroke(width = 3f))
        }
        Text(formatChartValue(minValue, unit), style = MaterialTheme.typography.bodySmall.scaledForWidget())
    }
}

@Composable
private fun BooleanStepChart(booleanEntries: List<HistoryEntry>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.fillMaxSize().padding(vertical = 2.dp)) {
        val path = computeStepChartPoints(booleanEntries, size.width, size.height).toPath()
        drawPath(path, color = lineColor, style = Stroke(width = 3f))
    }
}

private fun formatChartValue(value: Double, unit: String?): String =
    String.format(Locale.getDefault(), "%.1f%s", value, unit?.let { " $it" } ?: "")
