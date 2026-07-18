package org.fossify.gallery.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.MediaRepository
import java.util.concurrent.TimeUnit

// Runs the post-boot directory refresh that used to happen directly inside BootCompletedReceiver on
// a raw, unconstrained background thread - meaning it competed for CPU/IO with every other app's own
// boot-time work, right when the system is already most loaded, with no way for the OS to defer it.
// Routing it through WorkManager lets the system schedule it once conditions are reasonable (battery
// not low) instead of forcing it to run immediately during the boot storm.
//
// This used to walk MediaFetcher.getFoldersToScan()'s full folder list (every folder on the device)
// and re-derive each one's metadata from scratch via updateDirectoryPath() - on a ~200k-media,
// ~2900-folder real-device library that's thousands of individual ContentResolver/DB round trips in
// one doWork() call, which routinely ran past what the system gives a background job before it's
// killed. Killed mid-loop meant lastBootScanTimestamp (set only at the very end) never got written,
// so the very next retry started the same full walk over from folder zero - a real device was
// observed stuck retrying this 30 times without ever finishing. MediaRepository.syncNewMediaFromStore()
// (the same call MediaSyncWorker's incremental sync already uses) covers the actual goal - catching
// media that changed while the device was off - incrementally and boundedly (capped at 10k rows,
// filtered to images/videos, only touches what's new since the last sync), and already rebuilds the
// directories table itself. The per-folder enrichment updateDirectoryPath() used to do eagerly for
// every folder (EXIF-accurate sort dates, hidden-folder state, album covers) now happens on demand,
// only for folders that actually changed, the first time Albums is opened (AlbumsViewModel.recheckDirectories).
class BootScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // That catch-up can't have anything new within minutes of the last one, so this throttle
            // only skips back-to-back reboots (crash loops, OTA update reboots, troubleshooting).
            val config = applicationContext.config
            val now = System.currentTimeMillis()
            if (now - config.lastBootScanTimestamp < MIN_INTERVAL_MS) {
                return Result.success()
            }

            MediaRepository(applicationContext).syncNewMediaFromStore()
            config.lastBootScanTimestamp = now
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("BootScanWorker", "Boot scan failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "boot_scan"
        private const val MIN_INTERVAL_MS = 60L * 60 * 1000

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val work = OneTimeWorkRequestBuilder<BootScanWorker>()
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
        }
    }
}
