package org.fossify.gallery.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.compose.screens.analysis.AnalysisCriteria
import org.fossify.gallery.compose.screens.analysis.CompressionEngine
import org.fossify.gallery.extensions.compressionReviewDB
import org.fossify.gallery.models.CompressionReviewItem
import java.io.File

/**
 * Probe-compresses each pending [CompressionReviewItem] for a job into a temp file, updating the
 * row with the result. Deliberately mirrors [MediaBatchWorker]'s foreground-notification/progress
 * shape, but never touches MediaStore or the original file - that only happens once the user picks
 * a version in the review screen, so a cancelled/killed job just leaves rows at PENDING/whatever
 * they last reached, with nothing to roll back.
 */
class CompressionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0, 0)

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val jobId = inputData.getString(KEY_JOB_ID) ?: ""
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, applicationContext.getString(org.fossify.gallery.R.string.notif_channel_compression), NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(applicationContext.getString(org.fossify.gallery.R.string.notif_compressing_title))
            .setContentText(if (total > 0) "$done/$total" else applicationContext.getString(org.fossify.gallery.R.string.notif_preparing))
            .setProgress(total.coerceAtLeast(1), done.coerceAtMost(total.coerceAtLeast(1)), total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(buildCancelAction(jobId))
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun buildCancelAction(jobId: String): NotificationCompat.Action {
        val intent = Intent(applicationContext, org.fossify.gallery.receivers.CancelBatchOpReceiver::class.java)
            .setAction(org.fossify.gallery.receivers.CancelBatchOpReceiver.ACTION)
            .putExtra(org.fossify.gallery.receivers.CancelBatchOpReceiver.EXTRA_JOB_ID, jobId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getBroadcast(applicationContext, jobId.hashCode(), intent, flags)
        return NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, applicationContext.getString(org.fossify.gallery.R.string.cancel), pi)
    }

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo(0, 0))
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val engine = CompressionEngine(applicationContext)

        return try {
            val items = withContext(Dispatchers.IO) { applicationContext.compressionReviewDB.getForJob(jobId) }
            val total = items.size
            var done = 0
            var failed = 0
            var lastNotify = 0L

            for (item in items) {
                if (isStopped) break
                val result = try {
                    withContext(Dispatchers.IO) {
                        val analysis = AnalysisCriteria.analyze(item.originalPath) ?: error("Analyze failed")
                        val outFile = if (item.mediaType == 2) {
                            val (w, h, kbps) = AnalysisCriteria.suggestedVideoTarget(analysis) ?: error("Already optimal")
                            engine.compressVideo(item.originalPath, w, h, kbps)
                        } else {
                            val (edge, quality) = AnalysisCriteria.suggestedImageTarget(analysis) ?: error("Already optimal")
                            engine.compressImage(item.originalPath, edge, quality)
                        }
                        item.copy(tempResultPath = outFile.absolutePath, resultSize = outFile.length(), status = CompressionReviewItem.STATUS_DONE)
                    }
                } catch (e: OutOfMemoryError) {
                    // Caught separately because OutOfMemoryError is an Error, not an Exception: it
                    // used to slip past the catch below AND the outer try, killing the worker with
                    // every remaining item still PENDING - so one oversized photo (compressImage
                    // holds the decoded bitmap, up to just under 2x the target edge, and the scaled
                    // copy at the same time) took down the whole "compress all" run. Now it fails
                    // that one item and the batch carries on. Same handling, and the same message,
                    // as StorageAnalysisViewModel's own compression path already uses.
                    android.util.Log.e("CompressionWorker", "OOM compressing ${item.originalPath}", e)
                    item.copy(
                        status = CompressionReviewItem.STATUS_FAILED,
                        errorMessage = applicationContext.getString(org.fossify.gallery.R.string.opt_err_image_too_large),
                    )
                } catch (e: Exception) {
                    item.copy(status = CompressionReviewItem.STATUS_FAILED, errorMessage = e.message)
                }
                withContext(Dispatchers.IO) { applicationContext.compressionReviewDB.update(result) }
                if (result.status == CompressionReviewItem.STATUS_DONE) done++ else failed++

                val now = System.currentTimeMillis()
                if (now - lastNotify > 500) {
                    lastNotify = now
                    setForeground(createForegroundInfo(done + failed, total))
                    setProgress(androidx.work.workDataOf("done" to (done + failed), "total" to total))
                }
            }
            setProgress(androidx.work.workDataOf("done" to (done + failed), "total" to total))
            showResultNotification(done, failed, isStopped)
            Result.success(androidx.work.workDataOf("done" to done, "failed" to failed, "total" to total))
        } catch (e: Exception) {
            android.util.Log.e("CompressionWorker", "Job $jobId failed", e)
            Result.failure()
        }
    }

    private fun showResultNotification(done: Int, failed: Int, cancelled: Boolean) {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val text = if (cancelled) {
                applicationContext.getString(org.fossify.gallery.R.string.notif_compression_cancelled, done)
            } else {
                applicationContext.getString(org.fossify.gallery.R.string.notif_compression_done, done, failed)
            }
            nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle(applicationContext.getString(org.fossify.gallery.R.string.notif_compressing_title))
                .setContentText(text)
                .setAutoCancel(true)
                .build())
        } catch (_: Exception) { }
    }

    companion object {
        private const val CHANNEL_ID = "media_compression"
        private const val NOTIFICATION_ID = 2004
        const val KEY_JOB_ID = "job_id"

        /** Persists [paths] as PENDING review rows under a fresh jobId and enqueues the worker. */
        suspend fun enqueue(context: Context, paths: List<Pair<String, Int>>): String {
            val jobId = java.util.UUID.randomUUID().toString()
            withContext(Dispatchers.IO) {
                context.compressionReviewDB.insertAll(paths.map { (path, mediaType) ->
                    CompressionReviewItem(jobId = jobId, originalPath = path, originalSize = File(path).length(), mediaType = mediaType)
                })
            }
            val request = OneTimeWorkRequestBuilder<CompressionWorker>()
                .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).build())
                .addTag(jobId)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(jobId, ExistingWorkPolicy.REPLACE, request)
            return jobId
        }
    }
}
