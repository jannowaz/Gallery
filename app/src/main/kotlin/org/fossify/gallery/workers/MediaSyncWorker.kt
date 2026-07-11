package org.fossify.gallery.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.LOCATION_INTERNAL
import org.fossify.gallery.helpers.MediaRepository
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.models.Directory
import java.util.concurrent.TimeUnit

class MediaSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // Delegate the actual MediaStore query to MediaRepository.syncNewMediaFromStore(), which
            // filters by "DATE_MODIFIED > lastSyncTimestamp" instead of re-scanning every image/video
            // on the device on every run (this worker used to do its own unfiltered full-volume query
            // here, which meant every ContentObserver-triggered "incremental" sync was actually a full
            // scan). Reusing it also means both call paths (this worker and MediaViewModel) share one
            // tested implementation instead of two that can silently drift apart.
            val newMedia = MediaRepository(applicationContext).syncNewMediaFromStore()

            if (newMedia.isNotEmpty()) {
                // Only rebuild directory rows for folders touched by this batch, but read the *full*
                // current contents of each from the DB (not just the newMedia subset) - otherwise a
                // small incremental batch would overwrite media_count/thumbnail with a partial count
                // for folders that already contained many older files.
                val affectedDirs = newMedia.map { it.parentPath }.distinct()
                affectedDirs.forEach { dirPath ->
                    val dirMedia = applicationContext.mediaDB.getMediaFromPath(dirPath)
                    if (dirMedia.isNotEmpty()) {
                        val dirName = java.io.File(dirPath).name
                        val hasImage = dirMedia.any { it.type == 1 }
                        val hasVideo = dirMedia.any { it.type == 2 }
                        val types = if (hasImage && hasVideo) 3 else if (hasVideo) 2 else 1
                        applicationContext.directoryDB.insertAll(listOf(Directory(
                            id = null, path = dirPath, tmb = dirMedia.maxByOrNull { if (it.dateSortKey > 0) it.dateSortKey else if (it.dateAdded > 0) it.dateAdded else if (it.taken > 0) it.taken else it.modified }?.path ?: "",
                            name = dirName, mediaCnt = dirMedia.size,
                            modified = dirMedia.maxOf { it.modified },
                            taken = dirMedia.maxOf { it.taken },
                            size = dirMedia.size.toLong(),
                            location = LOCATION_INTERNAL, types = types, sortValue = "",
                        )))
                    }
                }
                // The ContentObserver in ComposeExplorerActivity already fires RefreshBus.trigger()
                // the instant MediaStore changes, well before this worker's deliberate delay (see
                // scheduleIncrementalSync) has actually written the new rows into the `media`/
                // `directories` tables - so a RefreshBus subscriber reloading on that first signal
                // (e.g. AlbumsViewModel, which reads the `directories` table) can end up reloading
                // stale data and missing the new media entirely. Firing again here, once the DB
                // writes above are actually committed, closes that race for every RefreshBus
                // subscriber, not just Albums.
                RefreshBus.trigger()
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("MediaSyncWorker", "Sync failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "media_sync"
        private const val INITIAL_WORK_NAME = "media_sync_initial"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<MediaSyncWorker>(168, TimeUnit.HOURS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
        }

        fun scheduleInitialSync(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<MediaSyncWorker>()
                .addTag(INITIAL_WORK_NAME)
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                INITIAL_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
        }

        fun scheduleIncrementalSync(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<MediaSyncWorker>()
                .addTag("media_sync_incremental")
                .setInitialDelay(2, TimeUnit.SECONDS)
                .build()
            // KEEP, not REPLACE: a burst of MediaStore writes (e.g. a chat app saving several photos
            // in quick succession) fires the ContentObserver repeatedly, and each call here used to
            // cancel-and-reschedule the pending 2s-delayed job from scratch. If writes kept landing
            // less than 2s apart, the sync's actual execution kept getting pushed back indefinitely -
            // exactly why newly downloaded media could take a long time (or need a manual refresh) to
            // show up in the Room-DB-backed Media/Albums tabs, while the Explorer tab (which queries
            // MediaStore directly, no worker/DB round trip) always showed them immediately. With KEEP,
            // the first trigger's job stays scheduled and fires on time regardless of later triggers;
            // syncNewMediaFromStore() catches up on everything up to that point via its timestamp
            // filter, and any writes landing after it starts get picked up by the next trigger.
            WorkManager.getInstance(context).enqueueUniqueWork("media_sync_incremental", ExistingWorkPolicy.KEEP, workRequest)
        }
    }
}
