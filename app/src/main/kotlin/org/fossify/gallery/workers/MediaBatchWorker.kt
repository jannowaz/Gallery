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
import org.fossify.gallery.extensions.batchJobItemDB
import org.fossify.gallery.extensions.deleteMediumWithPath
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.MediaStoreOps
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.UndoAction
import org.fossify.gallery.helpers.UndoManager
import org.fossify.gallery.helpers.UndoType
import org.fossify.gallery.models.BatchJobItem
import java.io.File

enum class BatchOperation { RENAME, MOVE_FAST, MOVE_COPY_DELETE, COPY }

/**
 * Runs a batch rename/move/copy job whose items were already persisted to `batch_job_items` by the
 * UI (WorkManager's Data is too small to carry a large path list). Consent for MediaStore writes on
 * items the app doesn't own must already have been granted by the UI before this worker is enqueued -
 * a background worker cannot itself resolve the system consent dialog (see MediaStoreConsent.kt).
 * Each write is attempted regardless and failures are counted rather than crashing, since it isn't
 * knowable here whether a consent grant survives a process restart between enqueue and execution.
 */
class MediaBatchWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0, 0)

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        val jobId = inputData.getString(KEY_JOB_ID) ?: ""
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, applicationContext.getString(org.fossify.gallery.R.string.notif_channel_batch_ops), NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(titleFor(inputData.getString(KEY_OPERATION)))
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

    private fun titleFor(operation: String?) = when (operation) {
        BatchOperation.RENAME.name -> applicationContext.getString(org.fossify.gallery.R.string.notif_batch_renaming)
        BatchOperation.COPY.name -> applicationContext.getString(org.fossify.gallery.R.string.notif_batch_copying)
        else -> applicationContext.getString(org.fossify.gallery.R.string.notif_batch_moving)
    }

    override suspend fun doWork(): Result {
        // Ensures this actually runs as a promoted foreground service (not just a look-alike
        // notification) - a batch of large media files is exactly the long-running, screen-off case
        // that needs real FGS protection from Doze/background execution limits.
        setForeground(createForegroundInfo(0, 0))

        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val operation = inputData.getString(KEY_OPERATION)?.let { runCatching { BatchOperation.valueOf(it) }.getOrNull() } ?: return Result.failure()

        return try {
            val items = withContext(Dispatchers.IO) { applicationContext.batchJobItemDB.getForJob(jobId) }
            val total = items.size
            var done = 0
            var failed = 0
            var lastNotify = 0L
            val succeededPaths = mutableListOf<String>()
            setProgress(androidx.work.workDataOf("done" to 0, "total" to total))

            for (item in items) {
                if (isStopped) break
                val success = withContext(Dispatchers.IO) { processItem(operation, item) }
                if (success) {
                    withContext(Dispatchers.IO) { applicationContext.batchJobItemDB.deleteItem(item.id ?: 0L) }
                    succeededPaths.add(item.sourcePath)
                    done++
                } else {
                    failed++
                }
                val now = System.currentTimeMillis()
                if (now - lastNotify > 500) {
                    lastNotify = now
                    setForeground(createForegroundInfo(done + failed, total))
                    setProgress(androidx.work.workDataOf("done" to (done + failed), "total" to total))
                }
            }
            setProgress(androidx.work.workDataOf("done" to (done + failed), "total" to total))

            if (succeededPaths.isNotEmpty() && (operation == BatchOperation.MOVE_FAST || operation == BatchOperation.MOVE_COPY_DELETE)) {
                UndoManager.push(UndoAction(paths = succeededPaths.toSet(), type = UndoType.MOVE))
            }
            RefreshBus.trigger()
            showResultNotification(operation, done, failed, isStopped)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("MediaBatchWorker", "Job $jobId failed", e)
            Result.failure()
        }
    }

    private fun processItem(operation: BatchOperation, item: BatchJobItem): Boolean = try {
        when (operation) {
            BatchOperation.RENAME -> {
                val uri = MediaStoreOps.uriForPath(applicationContext, item.sourcePath) ?: return false
                val newName = File(item.targetPath).name
                if (!MediaStoreOps.rename(applicationContext, uri, newName)) return false
                val parent = File(item.targetPath).parent ?: ""
                applicationContext.mediaDB.updateMedium(item.sourcePath, parent, newName, item.targetPath)
                applicationContext.mediaCacheDB.deleteByPathSync(item.sourcePath)
                true
            }
            BatchOperation.MOVE_FAST -> {
                val uri = MediaStoreOps.uriForPath(applicationContext, item.sourcePath) ?: return false
                val targetRel = MediaStoreOps.relativePathFor(File(item.targetPath).parent ?: "")
                if (!MediaStoreOps.move(applicationContext, uri, targetRel)) return false
                applicationContext.deleteMediumWithPath(item.sourcePath)
                true
            }
            BatchOperation.MOVE_COPY_DELETE -> {
                val uri = MediaStoreOps.uriForPath(applicationContext, item.sourcePath) ?: return false
                val targetRel = MediaStoreOps.relativePathFor(File(item.targetPath).parent ?: "")
                val newUri = MediaStoreOps.copy(applicationContext, uri, File(item.targetPath).name, targetRel, MediaStoreOps.isVideoPath(item.sourcePath))
                    ?: return false
                applicationContext.contentResolver.delete(uri, null, null)
                applicationContext.deleteMediumWithPath(item.sourcePath)
                true
            }
            BatchOperation.COPY -> {
                val uri = MediaStoreOps.uriForPath(applicationContext, item.sourcePath) ?: return false
                val targetRel = MediaStoreOps.relativePathFor(File(item.targetPath).parent ?: "")
                MediaStoreOps.copy(applicationContext, uri, File(item.targetPath).name, targetRel, MediaStoreOps.isVideoPath(item.sourcePath)) != null
            }
        }
    } catch (_: Exception) { false }

    private fun showResultNotification(operation: BatchOperation, done: Int, failed: Int, cancelled: Boolean) {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val text = when {
                cancelled -> "Abgebrochen: $done erledigt"
                failed > 0 -> "$done erledigt, $failed fehlgeschlagen"
                else -> "$done erledigt"
            }
            nm.notify(NOTIFICATION_ID, NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle(titleFor(operation.name))
                .setContentText(text)
                .setAutoCancel(true)
                .build())
        } catch (_: Exception) { }
    }

    companion object {
        private const val CHANNEL_ID = "media_batch_ops"
        private const val NOTIFICATION_ID = 2003
        const val KEY_JOB_ID = "job_id"
        const val KEY_OPERATION = "operation"

        /** Persists [items] under a fresh jobId and enqueues the worker; the caller must already have
         * obtained MediaStore write consent for MOVE_FAST/MOVE_COPY_DELETE/RENAME before calling this. */
        suspend fun enqueue(context: Context, operation: BatchOperation, items: List<BatchJobItem>): String {
            val jobId = java.util.UUID.randomUUID().toString()
            withContext(Dispatchers.IO) {
                context.batchJobItemDB.insertAll(items.map { it.copy(jobId = jobId) })
            }
            val request = OneTimeWorkRequestBuilder<MediaBatchWorker>()
                .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).putString(KEY_OPERATION, operation.name).build())
                .addTag(jobId)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(jobId, ExistingWorkPolicy.REPLACE, request)
            return jobId
        }
    }
}
