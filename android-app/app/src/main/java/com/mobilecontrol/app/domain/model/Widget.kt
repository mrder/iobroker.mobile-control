package com.mobilecontrol.app.domain.model

data class Widget(
    val id: String,
    val objectId: String?,
    val type: WidgetType,
    val title: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val config: Map<String, String> = emptyMap(),
)

enum class WidgetType(val wireName: String) {
    TEXT_VALUE("text_value"),
    TEMPERATURE("temperature"),
    HUMIDITY("humidity"),
    BOOLEAN_STATUS("boolean_status"),
    SWITCH("switch"),
    HISTORY_PLACEHOLDER("history_placeholder"),
    ;

    companion object {
        fun fromWireName(value: String): WidgetType =
            entries.firstOrNull { it.wireName == value } ?: TEXT_VALUE

        /** Maps a server-suggested widget hint (from ObjectCatalogItem.suggestedWidgets) to a concrete type. */
        fun fromSuggestion(hint: String): WidgetType =
            entries.firstOrNull { it.wireName.equals(hint, ignoreCase = true) } ?: TEXT_VALUE
    }
}
