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
    val suggestedWidgets: List<String>,
)
