package org.fossify.gallery.helpers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.extensions.mediaDB
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

    /**
     * "Replace" resolution, made recoverable: for every colliding item, the EXISTING target file is
     * renamed aside to a free "name (n).ext" and soft-deleted into the recycle bin (recoverable for
     * the bin's retention window), which frees the original path so the incoming file can take it with
     * its canonical name. Returns [items] unchanged (they keep their original targets, now free). Any
     * target that couldn't be freed is left alone - its item then simply fails and is reported.
     *
     * This app's recycle bin is a soft-delete flag that leaves files on disk, so a straight
     * "delete the existing then move in" would still collide; renaming aside is what actually frees
     * the path while keeping the old file restorable.
     */
    suspend fun freeTargetsForReplace(context: Context, items: List<BatchJobItem>) = withContext(Dispatchers.IO) {
        items.filter { collides(it) }.forEach { backupToRecycleBin(context, it.targetPath) }
        RefreshBus.trigger()
    }

    /**
     * Renames the file at [path] aside to a free "name (n).ext" and soft-deletes that copy into the
     * recycle bin, freeing [path] while keeping the file restorable. Returns true if [path] is now
     * free. Caller must be on a background thread (no dispatcher hop, so the editor's existing
     * background save can use it directly). Used to make an in-place overwrite recoverable: back up
     * the original here first, then write the new content to the now-free path - so a failed or
     * interrupted write can never corrupt the original.
     */
    fun backupToRecycleBin(context: Context, path: String): Boolean {
        val uri = MediaStoreOps.uriForPath(context, path) ?: return false
        val backupPath = uniquePath(path)
        val backupName = File(backupPath).name
        if (!MediaStoreOps.rename(context, uri, backupName)) return false
        runCatching {
            val parent = File(path).parent ?: ""
            context.mediaDB.updateMedium(path, parent, backupName, backupPath)
            context.mediaDB.softDelete(backupPath, System.currentTimeMillis())
        }
        return true
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
