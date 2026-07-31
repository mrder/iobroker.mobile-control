package com.mobilecontrol.app.ui.widgets

import com.mobilecontrol.app.domain.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(value: Any?, timestampMillis: Long): HistoryEntry = HistoryEntry(value, timestampMillis)

class HistoryChartTest {

    @Test
    fun `fewer than two numeric entries produce no points`() {
        assertEquals(emptyList<ChartPoint>(), computeLineChartPoints(listOf(entry(1.0, 1000L)), 100f, 50f))
        assertEquals(emptyList<ChartPoint>(), computeLineChartPoints(emptyList(), 100f, 50f))
    }

    @Test
    fun `two numeric entries plot at the canvas corners by time and value`() {
        val entries = listOf(entry(0.0, 0L), entry(10.0, 1000L))
        val points = computeLineChartPoints(entries, widthPx = 100f, heightPx = 50f)
        assertEquals(2, points.size)
        // oldest (lowest value) -> bottom-left
        assertEquals(0f, points[0].x, 0.01f)
        assertEquals(50f, points[0].y, 0.01f)
        // newest (highest value) -> top-right
        assertEquals(100f, points[1].x, 0.01f)
        assertEquals(0f, points[1].y, 0.01f)
    }

    @Test
    fun `x position reflects actual elapsed time, not sample index`() {
        // three samples, but unevenly spaced in time: the middle one is 90% of the way through
        val entries = listOf(entry(5.0, 0L), entry(5.0, 900L), entry(5.0, 1000L))
        val points = computeLineChartPoints(entries, widthPx = 1000f, heightPx = 50f)
        assertEquals(3, points.size)
        assertEquals(0f, points[0].x, 0.01f)
        assertEquals(900f, points[1].x, 1f)
        assertEquals(1000f, points[2].x, 0.01f)
    }

    @Test
    fun `non-numeric and boolean entries are ignored by the line chart`() {
        val entries = listOf(entry(true, 0L), entry("on", 500L), entry(1.0, 1000L))
        assertEquals(emptyList<ChartPoint>(), computeLineChartPoints(entries, 100f, 50f))
    }

    @Test
    fun `constant value still produces distinct points instead of dividing by zero`() {
        val entries = listOf(entry(5.0, 0L), entry(5.0, 500L), entry(5.0, 1000L))
        val points = computeLineChartPoints(entries, widthPx = 100f, heightPx = 50f)
        assertEquals(3, points.size)
        points.forEach { assertTrue(it.y.isFinite()) }
    }

    @Test
    fun `fewer than two boolean entries produce no step points`() {
        assertEquals(emptyList<ChartPoint>(), computeStepChartPoints(listOf(entry(true, 0L)), 100f, 50f))
    }

    @Test
    fun `step chart holds the previous value until the next sample then steps`() {
        val entries = listOf(entry(false, 0L), entry(true, 1000L))
        val points = computeStepChartPoints(entries, widthPx = 100f, heightPx = 50f)
        // start at false (bottom) at x=0, hold false until x=100, then step up to true (top) at x=100
        assertEquals(3, points.size)
        assertEquals(0f, points[0].x, 0.01f); assertEquals(50f, points[0].y, 0.01f)
        assertEquals(100f, points[1].x, 0.01f); assertEquals(50f, points[1].y, 0.01f)
        assertEquals(100f, points[2].x, 0.01f); assertEquals(0f, points[2].y, 0.01f)
    }

    @Test
    fun `step chart ignores non-boolean entries`() {
        val entries = listOf(entry(1.0, 0L), entry("x", 500L))
        assertEquals(emptyList<ChartPoint>(), computeStepChartPoints(entries, 100f, 50f))
    }
}
