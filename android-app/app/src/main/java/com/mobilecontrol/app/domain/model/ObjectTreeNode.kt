package com.mobilecontrol.app.domain.model

/**
 * A folder-level grouping synthesized purely from ObjectCatalogItem.path (the app never sees
 * real channel/device objects - only the server's already-filtered, flat catalog of exposed
 * leaf states - so unlike the admin web tab's object tree, there is no separate "container"
 * object to fetch; folders here are just common path prefixes). [name] is the server-resolved
 * display name from the catalog response's folderNames map when available (e.g. a zigbee
 * device's real name instead of its raw id segment) - see [buildObjectTree]; falls back to the
 * bare path segment when the server has no name for that folder (or none was passed at all).
 */
data class ObjectTreeNode(
    /** Dot-joined path up to and including this folder, e.g. "zigbee.0.living_room". */
    val id: String,
    val name: String,
    val children: List<ObjectTreeNode>,
    /** Leaf items directly inside this folder (not in a subfolder). */
    val items: List<ObjectCatalogItem>,
)

private class MutableTreeNode(val name: String, val id: String) {
    val children = linkedMapOf<String, MutableTreeNode>()
    val items = mutableListOf<ObjectCatalogItem>()
}

/**
 * Groups a flat catalog into a tree by ObjectCatalogItem.path. Items with an empty path (no
 * folder at all) end up directly in the returned root's own `items`, alongside top-level folders
 * in `children` - callers render both at depth 0.
 *
 * [folderNames] is the catalog response's own folder-id -> display-name map (see
 * CatalogResponseDto.folderNames) - a folder id not present there (server has no resolved name,
 * or the caller didn't pass one at all) falls back to its bare path segment, exactly like before
 * this map existed.
 */
fun buildObjectTree(items: List<ObjectCatalogItem>, folderNames: Map<String, String> = emptyMap()): ObjectTreeNode {
    val root = MutableTreeNode(name = "", id = "")
    for (item in items) {
        var node = root
        val pathSoFar = StringBuilder()
        for (segment in item.path) {
            if (pathSoFar.isNotEmpty()) pathSoFar.append('.')
            pathSoFar.append(segment)
            val folderId = pathSoFar.toString()
            node = node.children.getOrPut(segment) { MutableTreeNode(name = folderNames[folderId] ?: segment, id = folderId) }
        }
        node.items.add(item)
    }
    return root.toImmutable()
}

private fun MutableTreeNode.toImmutable(): ObjectTreeNode = ObjectTreeNode(
    id = id,
    name = name,
    children = children.values.map { it.toImmutable() }.sortedBy { it.name.lowercase() },
    items = items.sortedBy { it.name.lowercase() },
)

/**
 * True if the search [query] matches this item's own name, its raw ioBroker id path, or any
 * ancestor folder's resolved display name.
 *
 * Live-reported (2026-08-09): searching by a device's real-world name (e.g. "Erdspieß
 * Vorgarten", a Zigbee device) never found anything, even though the catalog clearly carried
 * that exact name - because it's only ever attached to the *folder* (via [folderNames], resolved
 * server-side from the device/channel object's own common.name), never to the leaf state itself.
 * A leaf's own `name` is just its own short label (e.g. "On/off state of the switch") and its
 * `path` is raw ioBroker id segments (e.g. "zigbee/0/60a423fffe0b54a7") - neither contains the
 * friendly device name at all, so the old two-field check could never match it.
 */
fun ObjectCatalogItem.matchesSearch(query: String, folderNames: Map<String, String>): Boolean {
    if (query.isBlank()) return true
    if (name.contains(query, ignoreCase = true)) return true
    if (path.joinToString("/").contains(query, ignoreCase = true)) return true
    var prefix = ""
    for (segment in path) {
        prefix = if (prefix.isEmpty()) segment else "$prefix.$segment"
        val folderName = folderNames[prefix] ?: continue
        if (folderName.contains(query, ignoreCase = true)) return true
    }
    return false
}

/** All leaf item ids currently rendered given which folder ids are expanded - collapsed folders
 *  hide their contents entirely, so only these need a live-value subscription. */
fun ObjectTreeNode.visibleLeafIds(expanded: Set<String>): List<String> {
    val result = mutableListOf<String>()
    fun walk(node: ObjectTreeNode) {
        result += node.items.map { it.id }
        for (child in node.children) {
            if (expanded.contains(child.id)) {
                walk(child)
            }
        }
    }
    walk(this)
    return result
}
