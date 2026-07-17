package org.fossify.gallery.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.fossify.gallery.compose.screens.analysis.CompressionEngine
import org.fossify.gallery.extensions.compressionReviewDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.resolveRecycleBinFile
import java.io.File
import java.util.concurrent.TimeUnit

class RecycleBinCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val cutoff = System.currentTimeMillis() - RETENTION_MS
            val expired = applicationContext.mediaDB.getOldRecycleBinItems(cutoff)
            var deletedCount = 0

            for (medium in expired) {
                try {
                    // resolveRecycleBinFile: a legacy (widget/camera-review-only) recycle-bin
                    // row's stored path is a "recycle_bin/..." placeholder, not a real one -
                    // File(medium.path) on that string was never found, so this auto-purge used
                    // to silently treat every such item as "already gone" and clear its DB rows
                    // without ever freeing the actual bytes still sitting in the app's internal
                    // recycle-bin folder - a guaranteed, fully automatic storage leak with zero
                    // user interaction. DB/favorites/cache rows still key on medium.path (the
                    // DB's real column value); only the on-disk file location is resolved
                    // differently.
                    val file = applicationContext.resolveRecycleBinFile(medium.path)
                    val gone = !file.exists() || file.delete()
                    if (gone) {
                        applicationContext.mediaDB.deleteMediumPath(medium.path)
                        try { applicationContext.favoritesDB.deleteFavoritePath(medium.path) } catch (e: Exception) { android.util.Log.e("RecycleBinCleanup", "Failed to delete favorite path", e) }
                        try { applicationContext.mediaCacheDB.deleteByPathSync(medium.path) } catch (e: Exception) { android.util.Log.e("RecycleBinCleanup", "Failed to delete cache path", e) }
                        try { File("${file.path}.xmp").delete() } catch (e: Exception) { android.util.Log.e("RecycleBinCleanup", "Failed to delete XMP sidecar", e) }
                        deletedCount++
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RecycleBinCleanup", "Failed to clean item: ${medium.path}", e)
                }
            }

            android.util.Log.i("RecycleBinCleanup", "Cleaned $deletedCount expired items")

            try {
                applicationContext.config.pruneOrphanedVideoPositions()
            } catch (e: Exception) {
                android.util.Log.e("RecycleBinCleanup", "Failed to prune orphaned video positions", e)
            }

            try {
                cleanupCompressionReviewCache()
            } catch (e: Exception) {
                android.util.Log.e("RecycleBinCleanup", "Failed to clean compression review cache", e)
            }

            try {
                // Daily ghost-row sweep: files deleted outside the app (PC/MTP, other apps) leave
                // their DB rows behind because the store sync only ever adds.
                org.fossify.gallery.helpers.MediaRepository(applicationContext).pruneMissingMedia()
            } catch (e: Exception) {
                android.util.Log.e("RecycleBinCleanup", "Missing-media sweep failed", e)
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("RecycleBinCleanup", "Cleanup failed", e)
            Result.failure()
        }
    }

    /** Review items the user never acted on keep a (potentially huge) transcoded temp file in the
     * cache dir forever, and a transform that crashed mid-export leaves a file no DB row points to.
     * Expire abandoned review rows (deleting their temp files), then sweep unreferenced cache files
     * - but only ones old enough that they cannot belong to a transform running right now. */
    private suspend fun cleanupCompressionReviewCache() {
        val dao = applicationContext.compressionReviewDB
        val cutoff = System.currentTimeMillis() - REVIEW_RETENTION_MS
        for (item in dao.getStale(cutoff)) {
            if (item.tempResultPath.isNotBlank()) {
                try { File(item.tempResultPath).delete() } catch (e: Exception) { android.util.Log.e("RecycleBinCleanup", "Failed to delete stale review file", e) }
            }
        }
        dao.deleteStale(cutoff)

        val referenced = dao.getAllTempPaths().toSet()
        val orphanCutoff = System.currentTimeMillis() - ORPHAN_FILE_AGE_MS
        CompressionEngine.cacheDir(applicationContext).listFiles()?.forEach { file ->
            if (file.absolutePath !in referenced && file.lastModified() < orphanCutoff) {
                file.delete()
            }
        }
    }

    companion object {
        private const val WORK_NAME = "recycle_bin_cleanup"
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000
        private const val REVIEW_RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        private const val ORPHAN_FILE_AGE_MS = 24L * 60 * 60 * 1000

        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<RecycleBinCleanupWorker>(24, TimeUnit.HOURS)
                .addTag(WORK_NAME)
                .setInitialDelay(1, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work,
            )
        }
    }
}
