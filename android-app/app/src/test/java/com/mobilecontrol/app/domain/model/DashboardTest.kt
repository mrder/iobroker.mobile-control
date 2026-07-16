package com.mobilecontrol.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DashboardTest {

    @Test
    fun `fromWireName matches an exact size class`() {
        assertEquals(SizeClass.MEDIUM, SizeClass.fromWireName("medium"))
    }

    @Test
    fun `fromWireName falls back to COMPACT for an unknown value`() {
        assertEquals(SizeClass.COMPACT, SizeClass.fromWireName("giant"))
    }

    @Test
    fun `layoutFor returns the matching layout when present`() {
        val compact = DashboardLayout(SizeClass.COMPACT, columns = 4, widgets = emptyList())
        val medium = DashboardLayout(SizeClass.MEDIUM, columns = 8, widgets = emptyList())
        val dashboard = Dashboard(id = "d1", name = "Test", revision = 1, layouts = listOf(compact, medium))

        assertSame(medium, dashboard.layoutFor(SizeClass.MEDIUM))
    }

    @Test
    fun `layoutFor falls back to the first layout when the requested size class is missing`() {
        val compact = DashboardLayout(SizeClass.COMPACT, columns = 4, widgets = emptyList())
        val dashboard = Dashboard(id = "d1", name = "Test", revision = 1, layouts = listOf(compact))

        assertSame(compact, dashboard.layoutFor(SizeClass.EXPANDED))
    }

    @Test
    fun `layoutFor synthesizes an empty layout when the dashboard has no layouts at all`() {
        val dashboard = Dashboard(id = "d1", name = "Test", revision = 1, layouts = emptyList())

        val layout = dashboard.layoutFor(SizeClass.EXPANDED)

        assertEquals(SizeClass.EXPANDED, layout.sizeClass)
        assertEquals(SizeClass.EXPANDED.defaultColumns, layout.columns)
        assertEquals(emptyList<Widget>(), layout.widgets)
    }

    @Test
    fun `widgetCount sums widgets across all layouts`() {
        val w = Widget(id = "w", objectId = null, type = WidgetType.TEXT_VALUE, title = "w", x = 0, y = 0, w = 1, h = 1)
        val compact = DashboardLayout(SizeClass.COMPACT, columns = 4, widgets = listOf(w, w))
        val medium = DashboardLayout(SizeClass.MEDIUM, columns = 8, widgets = listOf(w))
        val dashboard = Dashboard(id = "d1", name = "Test", revision = 1, layouts = listOf(compact, medium))

        assertEquals(3, dashboard.widgetCount)
    }
}
