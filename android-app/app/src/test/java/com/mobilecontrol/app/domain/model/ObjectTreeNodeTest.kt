package com.mobilecontrol.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun item(id: String, path: List<String>, name: String = id): ObjectCatalogItem = ObjectCatalogItem(
    id = id,
    name = name,
    path = path,
    role = null,
    valueType = ValueType.NUMBER,
    unit = null,
    canRead = true,
    canWrite = false,
    hasHistory = false,
    suggestedWidgets = emptyList(),
)

class ObjectTreeNodeTest {

    @Test
    fun `groups items into nested folders by their path`() {
        val tree = buildObjectTree(
            listOf(
                item("temp", listOf("Wohnzimmer"), name = "Temperatur"),
                item("hum", listOf("Wohnzimmer"), name = "Feuchte"),
                item("motion", listOf("Flur", "Sensoren"), name = "Bewegung"),
            ),
        )

        assertEquals(2, tree.children.size)
        val livingRoom = tree.children.first { it.name == "Wohnzimmer" }
        assertEquals(2, livingRoom.items.size)
        assertTrue(livingRoom.children.isEmpty())

        val hallway = tree.children.first { it.name == "Flur" }
        assertTrue(hallway.items.isEmpty())
        assertEquals(1, hallway.children.size)
        assertEquals("Sensoren", hallway.children.single().name)
        assertEquals("motion", hallway.children.single().items.single().id)
    }

    @Test
    fun `items with an empty path attach directly to the root`() {
        val tree = buildObjectTree(listOf(item("top", emptyList(), name = "Top-Level")))
        assertTrue(tree.children.isEmpty())
        assertEquals("top", tree.items.single().id)
    }

    @Test
    fun `folder ids are the dot-joined path so far, matching how folders are addressed for expand state`() {
        val tree = buildObjectTree(listOf(item("motion", listOf("Flur", "Sensoren"))))
        val hallway = tree.children.single()
        assertEquals("Flur", hallway.id)
        val sensors = hallway.children.single()
        assertEquals("Flur.Sensoren", sensors.id)
    }

    @Test
    fun `visibleLeafIds only includes items under expanded folders`() {
        val tree = buildObjectTree(
            listOf(
                item("temp", listOf("Wohnzimmer")),
                item("motion", listOf("Flur", "Sensoren")),
            ),
        )

        assertEquals(emptyList<String>(), tree.visibleLeafIds(expanded = emptySet()))
        assertEquals(listOf("temp"), tree.visibleLeafIds(expanded = setOf("Wohnzimmer")))
        // "Flur" expanded but not its child "Flur.Sensoren" - motion stays hidden
        assertEquals(emptyList<String>(), tree.visibleLeafIds(expanded = setOf("Flur")))
        assertEquals(listOf("motion"), tree.visibleLeafIds(expanded = setOf("Flur", "Flur.Sensoren")))
    }
}
