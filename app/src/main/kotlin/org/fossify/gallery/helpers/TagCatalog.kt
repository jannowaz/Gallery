package org.fossify.gallery.helpers

/**
 * One consistent snapshot of the normalized tag index.
 *
 * The UI surfaces need different projections of the same data: the media filter needs counts,
 * while Explorer, search and the tag browser need the paths. Keeping those projections together
 * prevents each tab from rebuilding or ordering its own slightly different tag snapshot.
 */
data class TagCatalog(
    val pathsByTag: Map<String, List<String>>,
) {
    val countsByTag: Map<String, Int> = pathsByTag.mapValues { it.value.size }
    val tags: List<String> = pathsByTag.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)

    companion object {
        val EMPTY = TagCatalog(emptyMap())
    }
}
