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

/** Renames one tag in both sides of the child→parent map. Existing target ancestry wins when
 * merging into a tag that already exists; otherwise the renamed tag inherits the source ancestry. */
fun renameTagInHierarchy(hierarchy: Map<String, String>, oldName: String, newName: String): MutableMap<String, String> {
    if (oldName == newName) return hierarchy.toMutableMap()
    val result = linkedMapOf<String, String>()
    hierarchy.forEach { (child, parent) ->
        if (child == oldName) return@forEach
        val rewrittenParent = if (parent == oldName) newName else parent
        val rewrittenChild = child
        if (rewrittenChild != rewrittenParent) result[rewrittenChild] = rewrittenParent
    }
    if (newName !in hierarchy) hierarchy[oldName]?.takeIf { it != newName }?.let { result[newName] = it }
    return result
}

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
