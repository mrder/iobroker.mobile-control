package com.mobilecontrol.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CatalogResponseDto(
    // Server sends this as a JSON number (a FNV-1a hash, see CatalogService.effectiveCatalog).
    val version: Long = 0,
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
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val allowedValues: List<JsonElement>? = null,
    val localOnly: Boolean = false,
    val confirmPolicy: String = "NONE",
    val suggestedWidgets: List<String> = emptyList(),
)
