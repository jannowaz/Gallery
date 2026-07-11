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
import org.fossify.gallery.extensions.updateDirectoryPath
import org.fossify.gallery.helpers.MediaFetcher
import java.util.concurrent.TimeUnit

// Runs the post-boot directory refresh that used to happen directly inside BootCompletedReceiver on
// a raw, unconstrained background thread - meaning it competed for CPU/IO with every other app's own
// boot-time work, right when the system is already most loaded, with no way for the OS to defer it.
// getFoldersToScan() itself queries the entire MediaStore, and updateDirectoryPath() re-scans each
// returned folder's full metadata (sort/group fields, album covers, hidden-folder state) - genuinely
// non-trivial work for a large library. Routing it through WorkManager lets the system schedule it
// once conditions are reasonable (battery not low) instead of forcing it to run immediately during
// the boot storm.
class BootScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // A full scan's actual purpose is catching changes that happened while the device was
            // off (e.g. an SD card's contents changed) - that can't have happened again within
            // minutes of the last one, so this throttle only skips back-to-back reboots (crash
            // loops, OTA update reboots, troubleshooting) rather than weakening the real guarantee.
            val config = applicationContext.config
            val now = System.currentTimeMillis()
            if (now - config.lastBootScanTimestamp < MIN_INTERVAL_MS) {
                return Result.success()
            }

            MediaFetcher(applicationContext).getFoldersToScan().forEach {
                applicationContext.updateDirectoryPath(it)
            }
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
