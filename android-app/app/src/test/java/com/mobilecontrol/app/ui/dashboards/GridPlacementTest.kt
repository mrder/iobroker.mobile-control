package com.mobilecontrol.app.ui.dashboards

import com.mobilecontrol.app.domain.model.DashboardLayout
import com.mobilecontrol.app.domain.model.SizeClass
import com.mobilecontrol.app.domain.model.Widget
import com.mobilecontrol.app.domain.model.WidgetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun widget(id: String, x: Int, y: Int, w: Int = 2, h: Int = 1): Widget =
    Widget(id = id, objectId = null, type = WidgetType.TEXT_VALUE, title = id, x = x, y = y, w = w, h = h)

private fun layoutOf(vararg widgets: Widget, columns: Int = 4): DashboardLayout =
    DashboardLayout(sizeClass = SizeClass.COMPACT, columns = columns, widgets = widgets.toList())

class GridPlacementTest {

    @Test
    fun `identical rectangles overlap`() {
        val a = widget("a", x = 0, y = 0)
        val b = widget("b", x = 0, y = 0)
        assertTrue(GridPlacement.rectsOverlap(a, b))
    }

    @Test
    fun `adjacent rectangles sharing only an edge do not overlap`() {
        val a = widget("a", x = 0, y = 0, w = 2, h = 1)
        val b = widget("b", x = 2, y = 0, w = 2, h = 1)
        assertFalse(GridPlacement.rectsOverlap(a, b))
    }

    @Test
    fun `partially overlapping rectangles overlap`() {
        val a = widget("a", x = 0, y = 0, w = 2, h = 2)
        val b = widget("b", x = 1, y = 1, w = 2, h = 2)
        assertTrue(GridPlacement.rectsOverlap(a, b))
    }

    @Test
    fun `rectangles in different rows never overlap`() {
        val a = widget("a", x = 0, y = 0, w = 4, h = 1)
        val b = widget("b", x = 0, y = 1, w = 4, h = 1)
        assertFalse(GridPlacement.rectsOverlap(a, b))
    }

    @Test
    fun `findFreeSlot places the first widget at the origin`() {
        val layout = layoutOf(columns = 4)
        val (x, y) = GridPlacement.findFreeSlot(layout, w = 2, h = 1)
        assertEquals(0 to 0, x to y)
    }

    @Test
    fun `findFreeSlot skips an occupied cell in the same row`() {
        val layout = layoutOf(widget("existing", x = 0, y = 0, w = 2, h = 1), columns = 4)
        val (x, y) = GridPlacement.findFreeSlot(layout, w = 2, h = 1)
        assertEquals(2 to 0, x to y)
    }

    @Test
    fun `findFreeSlot moves to the next row once the current row is full`() {
        val layout = layoutOf(
            widget("a", x = 0, y = 0, w = 2, h = 1),
            widget("b", x = 2, y = 0, w = 2, h = 1),
            columns = 4,
        )
        val (x, y) = GridPlacement.findFreeSlot(layout, w = 2, h = 1)
        assertEquals(0 to 1, x to y)
    }

    @Test
    fun `findFreeSlot never returns a slot that overlaps an existing widget`() {
        val layout = layoutOf(
            widget("a", x = 0, y = 0, w = 3, h = 2),
            widget("b", x = 3, y = 0, w = 1, h = 1),
            columns = 4,
        )
        val (x, y) = GridPlacement.findFreeSlot(layout, w = 2, h = 1)
        val candidate = widget("candidate", x = x, y = y, w = 2, h = 1)
        assertTrue(layout.widgets.none { GridPlacement.rectsOverlap(candidate, it) })
    }

    @Test
    fun `wouldOverlapAny excludes the widget's own id`() {
        val existing = widget("self", x = 0, y = 0, w = 2, h = 1)
        val overlaps = GridPlacement.wouldOverlapAny(x = 0, y = 0, w = 2, h = 1, others = listOf(existing), excludeId = "self")
        assertFalse("a widget must not be reported as colliding with itself", overlaps)
    }

    @Test
    fun `wouldOverlapAny detects a collision with a different widget`() {
        val existing = widget("other", x = 0, y = 0, w = 2, h = 1)
        val overlaps = GridPlacement.wouldOverlapAny(x = 1, y = 0, w = 2, h = 1, others = listOf(existing), excludeId = "self")
        assertTrue(overlaps)
    }
}
