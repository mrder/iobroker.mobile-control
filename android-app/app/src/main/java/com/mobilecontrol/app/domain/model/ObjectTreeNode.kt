package com.mobilecontrol.app.domain.model

/**
 * A folder-level grouping synthesized purely from ObjectCatalogItem.path (the app never sees
 * real channel/device objects - only the server's already-filtered, flat catalog of exposed
 * leaf states - so unlike the admin web tab's object tree, there is no separate "container"
 * object to fetch; folders here are just common path prefixes).
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
 */
fun buildObjectTree(items: List<ObjectCatalogItem>): ObjectTreeNode {
    val root = MutableTreeNode(name = "", id = "")
    for (item in items) {
        var node = root
        val pathSoFar = StringBuilder()
        for (segment in item.path) {
            if (pathSoFar.isNotEmpty()) pathSoFar.append('.')
            pathSoFar.append(segment)
            node = node.children.getOrPut(segment) { MutableTreeNode(name = segment, id = pathSoFar.toString()) }
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
