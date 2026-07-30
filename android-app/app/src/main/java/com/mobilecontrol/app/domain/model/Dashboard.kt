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
            ?: DashboardLayout(sizeClass = sizeClass, columns = sizeClass.defaultColumns, widgets = emptyList())
}

data class DashboardLayout(
    val sizeClass: SizeClass,
    val columns: Int,
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
