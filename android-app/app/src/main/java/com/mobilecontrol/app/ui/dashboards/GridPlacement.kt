package com.mobilecontrol.app.ui.dashboards

import com.mobilecontrol.app.domain.model.DashboardLayout
import com.mobilecontrol.app.domain.model.Widget
import com.mobilecontrol.app.domain.model.WidgetType

/**
 * Pure grid-placement math for the dashboard editor's drag & drop and auto-placement. Split out
 * from [DashboardEditorViewModel] (and out of the inline collision-preview check in
 * [DashboardEditorScreen]'s drag gesture handler, which used to hand-roll the same comparison) so
 * there is exactly one implementation, directly unit-testable without a ViewModel/Hilt harness.
 */
object GridPlacement {

    fun rectsOverlap(a: Widget, b: Widget): Boolean =
        a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y

    /**
     * Scans row-by-row, column-by-column for the first cell a [w]x[h] widget fits into without
     * overlapping any existing widget in [layout] - used to place newly added widgets.
     */
    fun findFreeSlot(layout: DashboardLayout, w: Int, h: Int): Pair<Int, Int> {
        val maxX = (layout.columns - w).coerceAtLeast(0)
        val maxY = (layout.widgets.maxOfOrNull { it.y + it.h } ?: 0) + h
        for (y in 0..maxY) {
            for (x in 0..maxX) {
                val candidate = Widget(id = "", objectId = null, type = WidgetType.TEXT_VALUE, title = "", x = x, y = y, w = w, h = h)
                if (layout.widgets.none { rectsOverlap(candidate, it) }) return x to y
            }
        }
        return 0 to maxY
    }

    /** Would a [w]x[h] widget at ([x], [y]) overlap anything in [others] (excluding [excludeId])? */
    fun wouldOverlapAny(x: Int, y: Int, w: Int, h: Int, others: List<Widget>, excludeId: String?): Boolean {
        val candidate = Widget(id = "", objectId = null, type = WidgetType.TEXT_VALUE, title = "", x = x, y = y, w = w, h = h)
        return others.any { other -> other.id != excludeId && rectsOverlap(candidate, other) }
    }
}
