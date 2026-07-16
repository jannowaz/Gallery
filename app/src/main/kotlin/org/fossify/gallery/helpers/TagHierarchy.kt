package org.fossify.gallery.helpers

/**
 * Expands [tags] to include every descendant (children, grandchildren, ...) according to
 * [hierarchy] (a child→parent tag-name map, as stored in `Config.tagHierarchy`). Used so
 * filtering/searching on a parent tag like "Places" also surfaces files tagged only with a
 * nested child like "Berlin", instead of requiring an exact tag-name match.
 */
fun expandTagsWithDescendants(tags: Collection<String>, hierarchy: Map<String, String>): Set<String> {
    if (hierarchy.isEmpty() || tags.isEmpty()) return tags.toSet()
    val childrenByParent = childrenByParentMap(hierarchy)
    val result = tags.toMutableSet()
    val queue = ArrayDeque(tags)
    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        childrenByParent[cur]?.forEach { child -> if (result.add(child)) queue.add(child) }
    }
    return result
}

/** Parent tag -> its direct children, precomputed once so callers building a tree over many tags
 * don't repeat this grouping per node (would otherwise be O(tags) per lookup). */
fun childrenByParentMap(hierarchy: Map<String, String>): Map<String, List<String>> =
    hierarchy.entries.groupBy({ it.value }, { it.key })

/** Walks [tag]'s parent chain up to its topmost ancestor. Guards against a corrupted/cyclic
 * [hierarchy] (e.g. from manually edited prefs) the same way the parent-assignment dialog's
 * cycle check does, by bailing out the moment a tag is revisited. */
fun rootOf(tag: String, hierarchy: Map<String, String>): String {
    var cur = tag
    val seen = HashSet<String>()
    while (seen.add(cur)) {
        val parent = hierarchy[cur] ?: return cur
        cur = parent
    }
    return cur
}
