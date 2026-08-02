package org.fossify.gallery.compose.screens.analysis

import java.io.File

/**
 * Pure selection maths for the duplicate finder, split out from the ViewModel so it can be unit
 * tested without an Android runtime. Every function takes the current groups, the current marks and
 * the active folder filter, and returns the new set of marked paths. The keeper rule is shared:
 * within a group the "keeper" is the favorite, then highest rating, then newest, then largest - the
 * copy a user is least likely to want gone - and no bulk action ever marks a group's last survivor.
 */
object DuplicateSelection {

    private fun visibleGroups(groups: List<DuplicateGroup>, folderFilter: String?): List<DuplicateGroup> =
        if (folderFilter == null) groups
        else groups.filter { g -> g.files.any { File(it.path).parent == folderFilter } }

    private fun keeperOf(files: List<DuplicateFile>): DuplicateFile? =
        files.maxWithOrNull(compareBy({ if (it.isFavorite) 1 else 0 }, { it.rating }, { it.modified }, { it.size }))

    /** Marks files whose full path [contains] (or, when false, does NOT contain) [query], across the
     * shown groups, never marking a group's last survivor. Replaces the marks in the shown scope
     * (outright when no folder filter, else only over the shown groups' files). */
    fun bySubstring(groups: List<DuplicateGroup>, selected: Set<String>, folderFilter: String?, query: String, contains: Boolean): Set<String> {
        val q = query.trim()
        if (q.isEmpty()) return selected
        val visible = visibleGroups(groups, folderFilter)
        val toMark = visible.flatMap { g ->
            val hit = g.files.filter { it.path.contains(q, ignoreCase = true) == contains }
            if (hit.size >= g.files.size) hit.filter { it.path != keeperOf(g.files)?.path } else hit
        }.map { it.path }.toSet()
        return if (folderFilter == null) toMark
        else (selected - visible.flatMap { it.files }.map { it.path }.toSet()) + toMark
    }

    /** Flips marked/unmarked across the shown groups; spares a keeper if a group would end fully
     * marked. Marks in hidden groups are preserved. */
    fun invert(groups: List<DuplicateGroup>, selected: Set<String>, folderFilter: String?): Set<String> {
        val visible = visibleGroups(groups, folderFilter)
        val toMark = visible.flatMap { g ->
            val flipped = g.files.filter { it.path !in selected }
            if (flipped.size >= g.files.size) flipped.filter { it.path != keeperOf(g.files)?.path } else flipped
        }.map { it.path }.toSet()
        val visiblePaths = visible.flatMap { it.files }.map { it.path }.toSet()
        return (selected - visiblePaths) + toMark
    }
}
