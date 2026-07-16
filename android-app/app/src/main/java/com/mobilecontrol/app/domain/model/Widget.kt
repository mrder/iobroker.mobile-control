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
    // Kotlin symbol renamed from HISTORY_PLACEHOLDER now that a real history widget exists (see
    // ui/widgets/WidgetComposables.kt#HistoryWidget), but the wireName stays "history_placeholder"
    // so dashboards already persisted (server-side and in the local Room cache) keep decoding to
    // this type unchanged - less breakage than switching to a "chart" wireName.
    HISTORY("history_placeholder"),
    MOMENTARY_BUTTON("button"),
    SLIDER("slider"),
    ROLLER_SHUTTER("shutter"),
    THERMOSTAT("thermostat"),
    ALARM("alarm"),
    ;

    companion object {
        fun fromWireName(value: String): WidgetType =
            entries.firstOrNull { it.wireName == value } ?: TEXT_VALUE

        /**
         * Maps a server-suggested widget hint (ObjectCatalogItem.suggestedWidgets, see
         * docs/API-VERTRAG.md and src/catalog/index.ts#suggestWidgets) to a concrete widget type.
         *
         * This is an explicit lookup table rather than a direct wireName comparison: several
         * server hints ("value", "chart", "status", "text") don't correspond 1:1 to a
         * WidgetType.wireName, so the previous `entries.firstOrNull { it.wireName == hint }`
         * silently fell through to the TEXT_VALUE fallback for most objects.
         */
        fun fromSuggestion(hint: String): WidgetType = SUGGESTION_TO_TYPE[hint.lowercase()] ?: TEXT_VALUE

        private val SUGGESTION_TO_TYPE: Map<String, WidgetType> = mapOf(
            "temperature" to TEMPERATURE,
            "humidity" to HUMIDITY,
            "switch" to SWITCH,
            "value" to TEXT_VALUE,
            "chart" to HISTORY,
            "status" to BOOLEAN_STATUS,
            "text" to TEXT_VALUE,
            "shutter" to ROLLER_SHUTTER,
            "slider" to SLIDER,
            "alarm" to ALARM,
            // Not currently emitted by the server's suggestWidgets() but kept so a manually
            // configured widget type still round-trips through fromSuggestion/wireName matching.
            "button" to MOMENTARY_BUTTON,
            "thermostat" to THERMOSTAT,
        )
    }
}
