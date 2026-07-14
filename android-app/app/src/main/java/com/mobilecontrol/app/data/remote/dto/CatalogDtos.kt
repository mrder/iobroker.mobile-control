package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CatalogResponseDto(
    val version: String,
    val objects: List<ObjectDto>,
)

@Serializable
data class ObjectDto(
    val id: String,
    val name: String,
    val path: List<String> = emptyList(),
    val role: String? = null,
    val valueType: String? = null,
    val unit: String? = null,
    val read: Boolean = false,
    val write: Boolean = false,
    val history: Boolean = false,
    val suggestedWidgets: List<String> = emptyList(),
)
