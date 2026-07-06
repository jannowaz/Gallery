package org.fossify.gallery.helpers

/**
 * Expands [tags] to include every descendant (children, grandchildren, ...) according to
 * [hierarchy] (a child→parent tag-name map, as stored in `Config.tagHierarchy`). Used so
 * filtering/searching on a parent tag like "Places" also surfaces files tagged only with a
 * nested child like "Berlin", instead of requiring an exact tag-name match.
 */
fun expandTagsWithDescendants(tags: Collection<String>, hierarchy: Map<String, String>): Set<String> {
    if (hierarchy.isEmpty() || tags.isEmpty()) return tags.toSet()
    val childrenByParent = hierarchy.entries.groupBy({ it.value }, { it.key })
    val result = tags.toMutableSet()
    val queue = ArrayDeque(tags)
    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        childrenByParent[cur]?.forEach { child -> if (result.add(child)) queue.add(child) }
    }
    return result
}
