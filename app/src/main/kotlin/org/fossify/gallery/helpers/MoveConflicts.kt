package org.fossify.gallery.helpers

import org.fossify.gallery.models.BatchJobItem
import java.io.File

/**
 * Name-collision handling for a Move/Copy batch. A move into a folder that already holds a file of
 * the same name would otherwise fail silently (MediaProvider rejects the RELATIVE_PATH write) and be
 * reported only as "N failed" with no reason. These helpers let the UI detect collisions up front and
 * offer the user a safe resolution - keep both (rename the incoming file) or skip - so nothing is ever
 * overwritten and nothing fails without explanation.
 */
object MoveConflicts {

    private fun collides(item: BatchJobItem): Boolean =
        item.sourcePath != item.targetPath && File(item.targetPath).exists()

    /** Items whose target name already exists in the destination. */
    fun conflicting(items: List<BatchJobItem>): List<BatchJobItem> = items.filter { collides(it) }

    /** Only the items that would NOT collide (the "skip the rest" resolution). */
    fun withoutConflicts(items: List<BatchJobItem>): List<BatchJobItem> = items.filterNot { collides(it) }

    /**
     * Retargets every colliding item to a free "name (n).ext" so both the existing and the incoming
     * file survive. Tracks names reserved within this same batch too, so two sources of the same name
     * moving into one folder don't both resolve to the same "(1)" and re-collide.
     */
    fun keepBoth(items: List<BatchJobItem>): List<BatchJobItem> {
        val reserved = HashSet<String>()
        return items.map { item ->
            val taken = item.targetPath in reserved || File(item.targetPath).exists()
            if (item.sourcePath != item.targetPath && taken) {
                val unique = uniquePath(item.targetPath, reserved)
                reserved.add(unique)
                item.copy(targetPath = unique)
            } else {
                reserved.add(item.targetPath)
                item
            }
        }
    }

    /** "dir/photo.jpg" -> "dir/photo (1).jpg", incrementing until neither disk nor [reserved] has it. */
    fun uniquePath(targetPath: String, reserved: Set<String> = emptySet()): String {
        val f = File(targetPath)
        val dir = f.parentFile ?: return targetPath
        val name = f.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        var candidate = File(dir, "$base ($i)$ext")
        while (candidate.exists() || candidate.absolutePath in reserved) {
            i++
            candidate = File(dir, "$base ($i)$ext")
        }
        return candidate.absolutePath
    }
}
