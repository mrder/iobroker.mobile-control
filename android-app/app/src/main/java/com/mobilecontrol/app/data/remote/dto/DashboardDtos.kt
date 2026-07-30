package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DashboardDto(
    val id: String,
    val name: String,
    val revision: Long,
    val layouts: List<DashboardLayoutDto>,
)

@Serializable
data class DashboardLayoutDto(
    val sizeClass: String,
    val columns: Int,
    /** Default only for defensive deserialization of an older/unexpected response - the app's own
     *  `Json` has encodeDefaults=true (see NetworkModule), so this is always sent regardless. */
    val rowHeight: Int = 72,
    val widgets: List<WidgetDto>,
)

@Serializable
data class WidgetDto(
    val id: String,
    val objectId: String? = null,
    val type: String,
    val title: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val config: Map<String, String> = emptyMap(),
)

@Serializable
data class DashboardListResponseDto(
    val dashboards: List<DashboardDto> = emptyList(),
)
