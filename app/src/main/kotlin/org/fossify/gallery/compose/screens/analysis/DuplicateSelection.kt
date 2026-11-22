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

    /**
     * Applies [strategy] to either every group or the groups identified by [targetGroupKeys]. A
     * targeted, non-additive application replaces marks only inside those groups, preserving every
     * mark outside them. This lets the UI select arbitrary group cards and run auto-select without
     * changing unselected groups.
     */
    fun applyKeepStrategy(
        groups: List<DuplicateGroup>,
        selected: Set<String>,
        strategy: KeepStrategy,
        targetGroupKeys: Set<String>? = null,
        additive: Boolean = targetGroupKeys != null,
        restrictToFolder: String? = null,
    ): Set<String> {
        val targets = if (targetGroupKeys == null) groups else groups.filter { it.targetKey in targetGroupKeys }
        fun inFolder(path: String) = restrictToFolder == null || File(path).parent == restrictToFolder

        val toDelete = targets.flatMap { group ->
            val keeper = when (strategy) {
                KeepStrategy.NEWEST -> group.files.maxWithOrNull(compareBy({ it.modified }, { it.size }))
                KeepStrategy.OLDEST -> group.files.minWithOrNull(compareBy({ it.modified }, { it.path }))
                KeepStrategy.LARGEST -> group.files.maxWithOrNull(compareBy({ it.size }, { it.modified }))
                KeepStrategy.SMALLEST -> group.files.minWithOrNull(compareBy({ it.size }, { it.modified }))
                KeepStrategy.SHORTEST_NAME -> group.files.minWithOrNull(compareBy({ it.name.length }, { it.name }))
                KeepStrategy.SHORTEST_PATH -> group.files.minWithOrNull(compareBy({ it.path.length }, { it.path }))
                KeepStrategy.HIGHEST_RATED -> keeperOf(group.files)
            }
            group.files.filter { it.path != keeper?.path && inFolder(it.path) }
        }.map { it.path }.toSet()

        return when {
            targetGroupKeys == null -> toDelete
            additive -> selected + toDelete
            else -> {
                val scopePaths = targets.flatMap { it.files }
                    .filter { inFolder(it.path) }
                    .map { it.path }
                    .toSet()
                (selected - scopePaths) + toDelete
            }
        }
    }

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
