package org.fossify.gallery.helpers

import android.util.Log
import org.fossify.commons.extensions.isAStorageRootFolder
import org.fossify.gallery.extensions.mediaDB
import java.io.File

internal fun MediaRepository.moveToRecycleBinImpl(path: String) {
    try {
        val now = System.currentTimeMillis()
        val uri = MediaStoreOps.uriForPath(context, path)
        val trash = if (uri != null) MediaStoreOps.setTrashed(context, uri, true) else MediaStoreOps.TrashResult(false, null)
        val trashedPath = trash.path
        if (trash.updated && trashedPath != null && trashedPath != path) {
            val updated = context.mediaDB.updateMedium(path, File(trashedPath).parent ?: File(path).parent ?: "", File(path).name, trashedPath)
            if (updated > 0) {
                migratePathMetadata(path, trashedPath)
                context.mediaDB.softDelete(trashedPath, now)
            } else {
                Log.e("MediaRepository", "Recycle-bin DB update did not match $path")
                context.mediaDB.softDelete(path, now)
            }
        } else {
            context.mediaDB.softDelete(path, now)
        }
    } catch (e: Exception) {
        Log.e("MediaRepository", "moveToRecycleBin failed for $path", e)
    }
}

internal fun MediaRepository.mediaPathsUnderFoldersImpl(folderPaths: Collection<String>): List<String> =
    folderPaths.filterNot { context.isAStorageRootFolder(it) }
        .flatMap { MediaStoreOps.mediaEntriesUnder(context, it) }
        .map { it.path }
        .distinct()

internal fun MediaRepository.moveToRecycleBinBatchImpl(paths: Collection<String>) {
    paths.forEach { moveToRecycleBinImpl(it) }
    RefreshBus.trigger(affectedPaths = paths)
}

internal fun MediaRepository.restoreFromRecycleBinImpl(path: String, notify: Boolean): Boolean {
    val ok = try {
        val medium = context.mediaDB.getMediaByPaths(listOf(path)).firstOrNull()
        val uri = MediaStoreOps.uriForPathIncludingTrashed(context, path)
            ?: medium?.mediaStoreId?.let { MediaStoreOps.uriForMediaStoreId(context, it) }
        val restore = if (uri != null) MediaStoreOps.setTrashed(context, uri, false) else MediaStoreOps.TrashResult(false, null)
        val restoredPath = restore.path
        if (restore.updated && restoredPath != null && restoredPath != path) {
            val updated = context.mediaDB.updateMedium(path, File(restoredPath).parent ?: "", File(restoredPath).name, restoredPath)
            if (updated <= 0) {
                false
            } else {
                migratePathMetadata(path, restoredPath)
                context.mediaDB.restoreDeleted(restoredPath)
                true
            }
        } else if (restore.updated && restoredPath == path) {
            context.mediaDB.restoreDeleted(path)
            true
        } else if (restore.updated) {
            false
        } else if (uri != null) {
            false
        } else {
            context.mediaDB.restoreDeleted(path)
            true
        }
    } catch (e: Exception) {
        Log.e("MediaRepository", "restoreFromRecycleBin failed for $path", e)
        false
    }
    if (ok && notify) RefreshBus.trigger(affectedPaths = listOf(path))
    return ok
}

internal fun MediaRepository.restoreFromRecycleBinBatchImpl(paths: Collection<String>): Int {
    var failed = 0
    paths.forEach { if (!restoreFromRecycleBinImpl(it, notify = false)) failed++ }
    if (failed < paths.size) RefreshBus.trigger(affectedPaths = paths)
    return failed
}
