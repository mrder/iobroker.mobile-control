package com.mobilecontrol.app.domain.model

/**
 * A single entry of the server-filtered public object catalog. This is intentionally NOT the raw
 * ioBroker object tree - only what the server explicitly exposes for this device/user.
 */
data class ObjectCatalogItem(
    val id: String,
    val name: String,
    val path: List<String>,
    val role: String?,
    val valueType: ValueType,
    val unit: String?,
    val canRead: Boolean,
    val canWrite: Boolean,
    val hasHistory: Boolean,
    val suggestedWidgets: List<String>,
) {
    /** First path segment is used as a room heuristic since the server does not expose an explicit room field. */
    val roomHeuristic: String
        get() = path.firstOrNull() ?: "—"
}

enum class ValueType {
    BOOLEAN,
    NUMBER,
    STRING,
    UNKNOWN,
}
