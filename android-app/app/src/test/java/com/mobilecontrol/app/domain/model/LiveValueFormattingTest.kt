package com.mobilecontrol.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveValueFormattingTest {

    @Test
    fun `null value renders as an em dash`() {
        assertEquals("—", formatLiveValueForDisplay(null))
    }

    @Test
    fun `short value with a unit is shown in full`() {
        assertEquals("21.5 °C", formatLiveValueForDisplay(21.5, "°C"))
    }

    @Test
    fun `short value without a unit is shown in full`() {
        assertEquals("true", formatLiveValueForDisplay(true))
    }

    @Test
    fun `a value at exactly the display limit is not truncated`() {
        val value = "a".repeat(MAX_VALUE_DISPLAY_LENGTH)
        assertEquals(value, formatLiveValueForDisplay(value))
    }

    @Test
    fun `a value one character over the limit is truncated with an ellipsis`() {
        val value = "a".repeat(MAX_VALUE_DISPLAY_LENGTH + 1)
        val result = formatLiveValueForDisplay(value)
        assertEquals(MAX_VALUE_DISPLAY_LENGTH + 1, result.length) // truncated content + the ellipsis char
        assertTrue(result.endsWith("…"))
    }

    @Test
    fun `a real multi-kilobyte JSON blob (the actual crash trigger) is bounded regardless of size`() {
        // Regression test for a real live crash: rendering an unbounded value (e.g. an
        // event-log "json" state) in a single-line ListItem Text crashed the app with
        // "maxWidth must be >= minWidth" during Compose's intrinsic-width measurement.
        val hugeJson = "[" + (1..2000).joinToString(",") { "{\"ts\":$it,\"note\":\"event $it\"}" } + "]"
        val result = formatLiveValueForDisplay(hugeJson)
        assertTrue(result.length <= MAX_VALUE_DISPLAY_LENGTH + 1)
    }
}
