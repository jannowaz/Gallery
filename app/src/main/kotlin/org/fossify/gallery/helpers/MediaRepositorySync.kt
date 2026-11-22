package org.fossify.gallery.helpers

import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.Medium
import java.io.File

internal fun MediaRepository.syncNewMediaFromStoreImpl(fullRescan: Boolean): List<Medium> {
    if (RefreshBus.isInForeground) {
        Log.d("MediaRepository", "skip video-duration backfill while app is foreground")
    } else {
        backfillVideoDurations()
    }
    try {
        val storedSync = context.config.lastSyncTimestamp
        val lastSync = if (fullRescan) 0L else storedSync
        val isFirstSync = storedSync == 0L || !context.mediaDB.hasAnyMedia()
        val newMedia = mutableListOf<Medium>()
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
        )

        val lastSyncSec = lastSync / 1000L
        val sel = "(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?) AND (${MediaStore.MediaColumns.DATE_MODIFIED} >= ? OR ${MediaStore.MediaColumns.DATE_ADDED} >= ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            lastSyncSec.toString(),
            lastSyncSec.toString(),
        )

        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        val batcher = SyncBatcher(CANDIDATE_BATCH_SIZE, lastSync) { batch ->
            val existing = batch.map { it.path }
                .chunked(SQLITE_BATCH_CHUNK_SIZE)
                .flatMap { context.mediaDB.getLivePaths(it) }
                .toSet()
            batch.filterTo(newMedia) { it.path !in existing }
        }

        context.contentResolver.query(uri, proj, sel, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
            val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
            val relPathIdx = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val takenIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val addedIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val typeIdx = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val durationIdx = c.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
            while (c.moveToNext()) {
                val path = SyncFields.resolvePath(
                    data = if (dataIdx >= 0) c.getString(dataIdx) else null,
                    storageRoot = storageRoot,
                    relativePath = if (relPathIdx >= 0) c.getString(relPathIdx) else null,
                    displayName = if (nameIdx >= 0) c.getString(nameIdx) else null,
                ) ?: continue
                val file = File(path)
                val name = file.name
                val modifiedSec = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                val providerModified = modifiedSec * 1000L
                val modified = providerModified.takeIf { it > 0L } ?: file.lastModified()
                val taken = SyncFields.takenMs(if (takenIdx >= 0) c.getLong(takenIdx) else 0L, modified)
                val added = SyncFields.addedMs(if (addedIdx >= 0) c.getLong(addedIdx) else 0L, modified, taken)

                val providerSize = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L
                val size = providerSize.takeIf { it > 0L } ?: file.length()
                val mediaType = if (typeIdx >= 0) c.getInt(typeIdx) else 1
                val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                val duration = if (durationIdx >= 0 && type == 2) (c.getLong(durationIdx) / 1000L).toInt() else 0
                batcher.add(
                    Medium(null, name, path, file.parent ?: "", modified, taken, size, type, duration, false, 0L, 0L, 0, dateAdded = added),
                    modifiedMs = modified,
                    addedMs = added,
                )
            }
        }
        batcher.finish()
        val latestTimestamp = batcher.watermark

        val deletedPaths = try {
            context.mediaDB.getDeletedMedia().map { it.path }.toSet()
        } catch (e: Exception) {
            Log.e("MediaRepository", "getDeletedMedia failed", e)
            emptySet()
        }

        if (newMedia.isNotEmpty() || lastSync == 0L) {
            newMedia.addAll(recentDiskMedia(newMedia.map { it.path }.toSet() + deletedPaths))
        }

        if (isFirstSync) {
            val diskMedia = scanMediaFromDiskImpl()
            val merged = (newMedia + diskMedia).distinctBy { it.path }
            if (merged.isNotEmpty()) {
                try {
                    context.mediaDB.insertAllKeepingExisting(merged)
                    syncDirectoriesFromMedia(force = true)
                } catch (e: Exception) {
                    Log.e("MediaRepository", "insertAllKeepingExisting failed", e)
                }
            }
            context.config.lastSyncTimestamp = System.currentTimeMillis()
            return merged
        } else if (newMedia.isNotEmpty()) {
            try {
                reviveSoftDeleted(newMedia)
                context.mediaDB.insertAllKeepingExisting(newMedia)
                context.config.lastSyncTimestamp = latestTimestamp
                syncDirectoriesFromMedia(force = true)
            } catch (e: Exception) {
                Log.e("MediaRepository", "insertAllKeepingExisting failed", e)
            }
            return newMedia
        } else {
            context.config.lastSyncTimestamp = latestTimestamp
        }
    } catch (e: Exception) {
        Log.e("MediaRepository", "syncNewMediaFromStore failed", e)
        throw e
    }
    return emptyList()
}

internal fun MediaRepository.scanMediaFromDiskImpl(): List<Medium> {
    val allMedia = mutableListOf<Medium>()
    val seen = mutableSetOf<String>()
    val exts = videoExts + imageExts

    try {
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
        )
        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        context.contentResolver.query(uri, proj, sel, args, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { c ->
            val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val addedCol = try { c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED) } catch (_: Exception) { -1 }
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val typeCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val durCol = try { c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION) } catch (_: Exception) { -1 }
            val maxItems = 20000
            while (c.moveToNext() && allMedia.size < maxItems) {
                val path = c.getString(dataCol) ?: continue
                if (path in seen) continue
                seen.add(path)
                val name = c.getString(nameCol) ?: ""
                val file = File(path)
                val modified = (c.getLong(dateCol) * 1000L).takeIf { it > 0L } ?: file.lastModified()
                val added = (if (addedCol >= 0) c.getLong(addedCol) * 1000L else 0L).takeIf { it > 0 } ?: modified
                val size = c.getLong(sizeCol).takeIf { it > 0L } ?: file.length()
                val mediaType = c.getInt(typeCol)
                val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                val duration = if (durCol >= 0) (c.getInt(durCol) / 1000) else 0
                allMedia.add(Medium(null, name, path, file.parent ?: "", modified, modified, size, type, duration, false, 0L, 0L, 0, dateAdded = added))
            }
        }
    } catch (_: Exception) { }

    if (allMedia.isEmpty()) {
        val root = Environment.getExternalStorageDirectory()
        val dirs = listOf(root, File(root, "DCIM"), File(root, "Pictures"), File(root, "Download"), File(root, "Movies")).filter { it.isDirectory }
        for (dir in dirs) scanFile(dir, allMedia, seen, 0, exts)
    }

    return allMedia.sortedByDescending { it.modified }
}

private fun MediaRepository.reviveSoftDeleted(candidates: List<Medium>) {
    if (candidates.isEmpty()) return
    try {
        val byPath = candidates.associateBy { it.path }
        val softDeleted = byPath.keys.chunked(SQLITE_BATCH_CHUNK_SIZE)
            .flatMap { context.mediaDB.getSoftDeletedMedia(it) }
        if (softDeleted.isEmpty()) return
        var revived = 0
        softDeleted.forEach { deletedRow ->
            val m = byPath[deletedRow.path] ?: byPath.entries.firstOrNull { it.key.equals(deletedRow.path, ignoreCase = true) }?.value
                ?: return@forEach
            if (m.size == deletedRow.size && m.modified == deletedRow.modified) return@forEach
            context.mediaDB.reviveMedium(deletedRow.path, m.modified, m.taken, m.size, m.type, m.dateAdded)
            revived++
        }
        if (revived > 0) Log.i("MediaRepository", "Revived $revived re-created files from soft-deleted rows")
    } catch (e: Exception) {
        Log.e("MediaRepository", "reviveSoftDeleted failed", e)
    }
}

private fun MediaRepository.recentDiskMedia(knownPaths: Set<String>): List<Medium> {
    val candidates = mutableListOf<Medium>()
    val exts = videoExts + imageExts
    try {
        val root = Environment.getExternalStorageDirectory()
        val dirs = listOf(
            File(root, "DCIM/Camera"),
            File(root, "DCIM"),
            File(root, "Pictures"),
            File(root, "Pictures/Screenshots"),
            File(root, "Pictures/Screenshot"),
            File(root, "Movies"),
            File(root, "Download"),
        ).filter { it.isDirectory }
        for (dir in dirs) {
            val files = dir.listFiles() ?: continue
            for (f in files) {
                if (!f.isFile || f.name.startsWith(".")) continue
                val p = f.absolutePath
                if (p in knownPaths) continue
                val ext = f.extension.lowercase()
                if (ext !in exts) continue
                val modified = f.lastModified()
                candidates.add(Medium(null, f.name, p, f.parent ?: "", modified, modified, f.length(), if (ext in videoExts) 2 else 1, 0, false, 0L, 0L, 0))
            }
        }
    } catch (_: Exception) { }
    if (candidates.isEmpty()) return candidates
    val existing = candidates.map { it.path }
        .chunked(SQLITE_BATCH_CHUNK_SIZE)
        .flatMap { context.mediaDB.getExistingPaths(it) }
        .toSet()
    return candidates.filterNot { it.path in existing }
}

private fun MediaRepository.scanFile(dir: File, result: MutableList<Medium>, seen: MutableSet<String>, depth: Int, exts: Set<String>) {
    if (depth > 4 || !dir.isDirectory) return
    val files = dir.listFiles() ?: return
    for (file in files) {
        if (file.isDirectory && !file.name.startsWith(".")) {
            scanFile(file, result, seen, depth + 1, exts)
        } else if (file.isFile) {
            val ext = file.extension.lowercase()
            if (ext in exts && file.path !in seen) {
                seen.add(file.path)
                result.add(Medium(null, file.name, file.absolutePath, file.parent ?: "", file.lastModified(), file.lastModified(), file.length(), if (ext in videoExts) 2 else 1, 0, false, 0L, 0L, 0))
            }
        }
    }
}
