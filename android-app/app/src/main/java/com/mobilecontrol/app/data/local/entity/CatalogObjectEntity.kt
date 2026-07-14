package com.mobilecontrol.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_objects")
data class CatalogObjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: List<String>,
    val role: String?,
    val valueType: String,
    val unit: String?,
    val canRead: Boolean,
    val canWrite: Boolean,
    val hasHistory: Boolean,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    /** Empty list means "no restriction" (matches how the mapper treats a null/absent server value). */
    val allowedValues: List<String> = emptyList(),
    val localOnly: Boolean = false,
    val confirmPolicy: String = "NONE",
    val suggestedWidgets: List<String>,
)
