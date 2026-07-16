package com.mobilecontrol.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTypeTest {

    @Test
    fun `fromWireName matches an exact wireName`() {
        assertEquals(WidgetType.SWITCH, WidgetType.fromWireName("switch"))
        assertEquals(WidgetType.SLIDER, WidgetType.fromWireName("slider"))
    }

    @Test
    fun `fromWireName falls back to TEXT_VALUE for an unknown wire name`() {
        assertEquals(WidgetType.TEXT_VALUE, WidgetType.fromWireName("does-not-exist"))
    }

    @Test
    fun `history widget keeps its legacy wireName for backward compatibility`() {
        // Renamed from HISTORY_PLACEHOLDER to HISTORY, but wireName is unchanged so previously
        // persisted dashboards still decode correctly - see Widget.kt's comment on this constant.
        assertEquals("history_placeholder", WidgetType.HISTORY.wireName)
        assertEquals(WidgetType.HISTORY, WidgetType.fromWireName("history_placeholder"))
    }

    @Test
    fun `fromSuggestion maps every server hint documented in suggestWidgets to a distinct type`() {
        assertEquals(WidgetType.TEMPERATURE, WidgetType.fromSuggestion("temperature"))
        assertEquals(WidgetType.HUMIDITY, WidgetType.fromSuggestion("humidity"))
        assertEquals(WidgetType.SWITCH, WidgetType.fromSuggestion("switch"))
        assertEquals(WidgetType.TEXT_VALUE, WidgetType.fromSuggestion("value"))
        assertEquals(WidgetType.HISTORY, WidgetType.fromSuggestion("chart"))
        assertEquals(WidgetType.BOOLEAN_STATUS, WidgetType.fromSuggestion("status"))
        assertEquals(WidgetType.TEXT_VALUE, WidgetType.fromSuggestion("text"))
        assertEquals(WidgetType.ROLLER_SHUTTER, WidgetType.fromSuggestion("shutter"))
        assertEquals(WidgetType.SLIDER, WidgetType.fromSuggestion("slider"))
        assertEquals(WidgetType.ALARM, WidgetType.fromSuggestion("alarm"))
    }

    @Test
    fun `fromSuggestion is case-insensitive and falls back to TEXT_VALUE`() {
        assertEquals(WidgetType.TEMPERATURE, WidgetType.fromSuggestion("TEMPERATURE"))
        assertEquals(WidgetType.TEXT_VALUE, WidgetType.fromSuggestion("totally-unknown-hint"))
    }
}
