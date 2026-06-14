package org.fossify.gallery.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.fossify.gallery.extensions.mediaDB
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
                    val file = File(medium.path)
                    if (file.exists()) file.delete()
                    applicationContext.mediaDB.deleteMediumPath(medium.path)
                    deletedCount++
                } catch (_: Exception) { }
            }

            android.util.Log.i("RecycleBinCleanup", "Cleaned $deletedCount expired items")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("RecycleBinCleanup", "Cleanup failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "recycle_bin_cleanup"
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

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
