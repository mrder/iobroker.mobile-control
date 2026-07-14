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
