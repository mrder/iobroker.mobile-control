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
    fun `a folder id resolved in folderNames uses that display name instead of the raw path segment`() {
        val tree = buildObjectTree(
            listOf(item("state", listOf("zigbee", "0", "00124b0024510164"))),
            folderNames = mapOf("zigbee.0.00124b0024510164" to "SNZB-03 Bewegungsmelder Briefkasten"),
        )
        val zigbeeAdapter = tree.children.single()
        // Not present in folderNames - falls back to its raw path segment, same as before this
        // parameter existed.
        assertEquals("zigbee", zigbeeAdapter.name)
        val instance = zigbeeAdapter.children.single()
        assertEquals("0", instance.name)
        val device = instance.children.single()
        assertEquals("SNZB-03 Bewegungsmelder Briefkasten", device.name)
    }

    @Test
    fun `matchesSearch finds an item by an ancestor folder's resolved display name`() {
        // Live-reported (2026-08-09): searching "Erdspieß" for a Zigbee device named "Erdspieß
        // Vorgarten" found nothing, even though the catalog clearly carried that exact name -
        // it's only ever attached to the device's *folder* entry (folderNames), never to the
        // leaf state's own name ("On/off state of the switch") or its raw id path.
        val switchState = item("state", listOf("zigbee", "0", "60a423fffe0b54a7"), name = "On/off state of the switch")
        val folderNames = mapOf("zigbee.0.60a423fffe0b54a7" to "Erdspieß Vorgarten")

        assertTrue(switchState.matchesSearch("Erdspieß", folderNames))
        assertTrue(switchState.matchesSearch("vorgarten", folderNames)) // case-insensitive
        assertTrue(switchState.matchesSearch("switch", folderNames)) // still matches the item's own name
        assertTrue(switchState.matchesSearch("60a423fffe0b54a7", folderNames)) // still matches the raw path
        assertTrue(switchState.matchesSearch("", folderNames)) // blank query matches everything
        assertTrue(!switchState.matchesSearch("Erdspieß", emptyMap())) // no folderNames at all - can't match
        assertTrue(!switchState.matchesSearch("no such match", folderNames))
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
