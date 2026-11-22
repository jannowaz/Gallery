package org.fossify.gallery.helpers

import android.util.Log
import org.fossify.gallery.databases.GalleryDatabase
import org.fossify.gallery.extensions.dateTakensDB
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.models.MediaReviewState
import org.fossify.gallery.models.MediaReviewStatus
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.PersonPresence
import java.io.File

internal fun MediaRepository.setReviewStatusImpl(path: String, status: MediaReviewStatus?): Boolean = try {
    val dao = GalleryDatabase.getInstance(context.applicationContext).MediaReviewStateDao()
    if (status == null) dao.clear(path)
    else dao.upsert(MediaReviewState(path, status.databaseValue))
    RefreshBus.trigger(pathsChanged = false, collectionsChanged = false)
    true
} catch (e: Exception) {
    Log.e("MediaRepository", "setReviewStatus failed for $path", e)
    false
}

internal fun MediaRepository.setReviewStatusBatchImpl(paths: Collection<String>, status: MediaReviewStatus?): Boolean = try {
    val distinctPaths = paths.map(String::trim).filter(String::isNotBlank).distinct()
    if (distinctPaths.isEmpty()) return true
    val dao = GalleryDatabase.getInstance(context.applicationContext).MediaReviewStateDao()
    if (status == null) dao.clearAll(distinctPaths)
    else dao.upsertAll(distinctPaths.map { MediaReviewState(it, status.databaseValue) })
    RefreshBus.trigger(pathsChanged = false, collectionsChanged = false)
    true
} catch (e: Exception) {
    Log.e("MediaRepository", "setReviewStatus batch failed", e)
    false
}

internal fun MediaRepository.getReviewStatusImpl(path: String): MediaReviewStatus? = try {
    when (GalleryDatabase.getInstance(context.applicationContext).MediaReviewStateDao().getStatus(path)) {
        MediaReviewStatus.SKIPPED.databaseValue -> MediaReviewStatus.SKIPPED
        MediaReviewStatus.DECIDED.databaseValue -> MediaReviewStatus.DECIDED
        else -> null
    }
} catch (_: Exception) {
    null
}

internal fun MediaRepository.updateReviewPathImpl(oldPath: String, newPath: String) {
    if (oldPath.equals(newPath, ignoreCase = true)) return
    runCatching {
        GalleryDatabase.getInstance(context.applicationContext).MediaReviewStateDao().updatePath(oldPath, newPath)
    }.onFailure { Log.w("MediaRepository", "updateReviewPath failed", it) }
}

private fun MediaRepository.updatePersonScanPath(oldPath: String, newPath: String) {
    if (oldPath.equals(newPath, ignoreCase = true)) return
    runCatching {
        GalleryDatabase.getInstance(context.applicationContext).MediaPersonScanDao()
            .updatePath(oldPath, newPath, File(newPath).parent.orEmpty())
    }.onFailure { Log.w("MediaRepository", "person scan path migration failed", it) }
}

internal fun MediaRepository.getPersonScanMediaImpl(
    folderPath: String,
    includeSubfolders: Boolean,
    result: PersonPresence,
): List<Medium> = try {
    val paths = GalleryDatabase.getInstance(context.applicationContext).MediaPersonScanDao()
        .getPathsForFolderAndResult(folderPath, personScanDescendantPattern(folderPath), includeSubfolders, result.databaseValue)
    val resolved = paths.chunked(SQLITE_BATCH_CHUNK_SIZE).flatMap { batch ->
        runCatching { context.mediaDB.getMediaByPaths(batch) }
            .onFailure { Log.e("PersonScan", "Could not resolve a ${batch.size}-item result batch", it) }
            .getOrDefault(emptyList())
    }
    if (resolved.size != paths.size) {
        Log.w("PersonScan", "Resolved ${resolved.size} of ${paths.size} ${result.databaseValue} paths (recursive=$includeSubfolders)")
    }
    val byPath = resolved.associateBy { it.path.lowercase() }
    paths.mapNotNull { path -> byPath[path.lowercase()] }
        .sortedWith(compareByDescending<Medium> { it.size }.thenBy { it.name.lowercase() })
} catch (e: Exception) {
    Log.w("MediaRepository", "getPersonScanMedia failed", e)
    emptyList()
}

internal fun MediaRepository.getPersonScanCountsImpl(
    folderPath: String,
    includeSubfolders: Boolean,
): Map<PersonPresence, Int> = try {
    GalleryDatabase.getInstance(context.applicationContext).MediaPersonScanDao()
        .getCountsForFolder(folderPath, personScanDescendantPattern(folderPath), includeSubfolders)
        .associate { row -> runCatching { PersonPresence.valueOf(row.result) }.getOrDefault(PersonPresence.UNCERTAIN) to row.count }
} catch (e: Exception) {
    Log.w("MediaRepository", "getPersonScanCounts failed", e)
    emptyMap()
}

internal fun MediaRepository.keepPersonScanMediaImpl(paths: Set<String>) {
    if (paths.isEmpty()) return
    runCatching {
        val dao = GalleryDatabase.getInstance(context.applicationContext).MediaPersonScanDao()
        paths.chunked(SQLITE_BATCH_CHUNK_SIZE).forEach(dao::markKept)
        RefreshBus.trigger(pathsChanged = false, collectionsChanged = false)
    }.onFailure { Log.w("MediaRepository", "keepPersonScanMedia failed", it) }
}

private fun personScanDescendantPattern(folderPath: String): String {
    val escapedPath = folderPath
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return if (folderPath == "/") "/%" else "$escapedPath/%"
}

internal fun MediaRepository.migratePathMetadataImpl(oldPath: String, newPath: String) {
    if (oldPath.equals(newPath, ignoreCase = true)) return
    val filename = File(newPath).name
    val parent = File(newPath).parent ?: ""
    XmpWriter.migrateSidecars(oldPath, newPath)
    runCatching { context.favoritesDB.updateFavorite(filename, newPath, parent, oldPath) }
        .onFailure { Log.w("MediaRepository", "favorite path migration failed", it) }
    runCatching { context.mediaTagDB.migratePath(oldPath, newPath) }
        .onFailure { Log.w("MediaRepository", "tag path migration failed", it) }
    runCatching { context.dateTakensDB.updatePath(oldPath, newPath, filename, parent) }
        .onFailure { Log.w("MediaRepository", "date-taken path migration failed", it) }
    runCatching { context.mediaCacheDB.deleteByPathSync(oldPath) }
        .onFailure { Log.w("MediaRepository", "metadata cache migration failed", it) }
    updateReviewPathImpl(oldPath, newPath)
    updatePersonScanPath(oldPath, newPath)
}
