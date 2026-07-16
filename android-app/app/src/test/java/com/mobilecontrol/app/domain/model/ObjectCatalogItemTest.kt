package com.mobilecontrol.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

private fun item(path: List<String>): ObjectCatalogItem = ObjectCatalogItem(
    id = "id",
    name = "name",
    path = path,
    role = null,
    valueType = ValueType.NUMBER,
    unit = null,
    canRead = true,
    canWrite = false,
    hasHistory = false,
    suggestedWidgets = emptyList(),
)

class ObjectCatalogItemTest {

    @Test
    fun `roomHeuristic is the first path segment`() {
        assertEquals("Wohnzimmer", item(listOf("Wohnzimmer", "Klimasensor")).roomHeuristic)
    }

    @Test
    fun `roomHeuristic falls back to an em dash when the path is empty`() {
        assertEquals("—", item(emptyList()).roomHeuristic)
    }
}
