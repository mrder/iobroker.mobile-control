package com.mobilecontrol.app.domain.model

data class Dashboard(
    val id: String,
    val name: String,
    val revision: Long,
    val layouts: List<DashboardLayout>,
    val isStartDashboard: Boolean = false,
) {
    val widgetCount: Int
        get() = layouts.sumOf { it.widgets.size }

    fun layoutFor(sizeClass: SizeClass): DashboardLayout =
        layouts.firstOrNull { it.sizeClass == sizeClass }
            ?: layouts.firstOrNull()
            ?: DashboardLayout(
                sizeClass = sizeClass,
                columns = sizeClass.defaultColumns,
                rowHeight = DEFAULT_ROW_HEIGHT_DP,
                widgets = emptyList(),
            )

    companion object {
        /** Default grid row height in dp for a brand-new layout - user-adjustable per dashboard
         *  from there on (live-requested, 2026-07-30), see DashboardEditorViewModel.updateGridSize. */
        const val DEFAULT_ROW_HEIGHT_DP = 72
    }
}

data class DashboardLayout(
    val sizeClass: SizeClass,
    val columns: Int,
    val rowHeight: Int,
    val widgets: List<Widget>,
)

enum class SizeClass(val wireName: String, val defaultColumns: Int) {
    // Kept in sync with DashboardEditorViewModel.MIN_GRID_COLUMNS - a fallback layout built here
    // (Dashboard.layoutFor's last-resort branch) should already be at the current minimum grid
    // width, not immediately trigger that same widening the moment it's opened for editing.
    COMPACT("compact", 12),
    MEDIUM("medium", 12),
    EXPANDED("expanded", 12),
    ;

    companion object {
        fun fromWireName(value: String): SizeClass =
            entries.firstOrNull { it.wireName == value } ?: COMPACT
    }
}
