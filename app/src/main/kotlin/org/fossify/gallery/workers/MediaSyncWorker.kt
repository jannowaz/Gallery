package org.fossify.gallery.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.fossify.gallery.extensions.config
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
            val repo = MediaRepository(applicationContext)
            val isGapRepair = inputData.getBoolean(KEY_FULL_RESCAN, false)
            val newMedia = repo.syncNewMediaFromStore(fullRescan = isGapRepair)
            if (isGapRepair) {
                // Only after the pass actually completed - a failure throws out of here and leaves
                // the flag unset, so the repair is retried on the next launch instead of being
                // silently marked done with rows still missing.
                applicationContext.config.syncGapRepairDone = true
                android.util.Log.i("MediaSyncWorker", "Gap repair done, recovered ${newMedia.size} rows")
            }
            // Externally deleted files leave ghost rows (sync only adds) - sweep at most every 6h,
            // piggybacked here because this worker already runs off the ContentObserver.
            repo.pruneMissingMediaIfDue()

            if (newMedia.isNotEmpty()) {
                // Directory rows are rebuilt inside syncNewMediaFromStore() itself now (see
                // MediaRepository.syncDirectoriesFromMedia) - the per-affected-dir rebuild that
                // used to live here only ran on this worker's path, while the MediaViewModel sync
                // path consumed lastSyncTimestamp first and left new folders out of Albums forever.
                //
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
        const val KEY_FULL_RESCAN = "full_rescan"
        private const val GAP_REPAIR_WORK_NAME = "media_sync_gap_repair"
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

        /**
         * One-shot repair for installs whose media table is missing rows an older, truncating
         * version of the incremental sync dropped. Those rows sit below lastSyncTimestamp, so no
         * amount of normal incremental syncing brings them back - this ignores the watermark once
         * and re-examines everything MediaStore has.
         *
         * No-op after it has succeeded once (config.syncGapRepairDone). KEEP policy so a relaunch
         * while it is still running doesn't restart it from the top, and battery-not-low because on
         * a six-figure library this walks the whole store.
         */
        fun scheduleGapRepair(context: Context) {
            if (context.config.syncGapRepairDone) return
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<MediaSyncWorker>()
                .setInputData(Data.Builder().putBoolean(KEY_FULL_RESCAN, true).build())
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.SECONDS)
                .addTag(GAP_REPAIR_WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(GAP_REPAIR_WORK_NAME, ExistingWorkPolicy.KEEP, workRequest)
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
