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
        try {
            setForeground(createForegroundInfo(0, 0))
        } catch (e: Exception) {
            // On Android 12+ a dataSync foreground service cannot be started while the app is in the
            // background (ForegroundServiceStartNotAllowedException). If the user taps "move all" and
            // then leaves the app before WorkManager actually starts this worker, setForeground()
            // throws here - which, when swallowed, looked exactly like "the whole batch failed with
            // nothing moved". Log it loudly; the retry below runs without the FGS promotion.
            android.util.Log.e(TAG, "setForeground failed (app likely backgrounded at worker start)", e)
        }

        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val operation = inputData.getString(KEY_OPERATION)?.let { runCatching { BatchOperation.valueOf(it) }.getOrNull() } ?: return Result.failure()

        // Give up (rather than retry forever) once WorkManager has already restarted this a few
        // times - each restart means the process was killed mid-run, and endless 30s-backoff retries
        // are exactly what left jobs stuck in TIMING_DELAY on-device. Clear this job's rows so a
        // failed batch doesn't leave dead queue entries behind.
        if (runAttemptCount >= MAX_ATTEMPTS) {
            android.util.Log.w(TAG, "giving up on job $jobId after $runAttemptCount attempts")
            withContext(Dispatchers.IO) { applicationContext.batchJobItemDB.deleteJob(jobId) }
            showResultNotification(operation, 0, 0, cancelled = true)
            return Result.failure()
        }

        return try {
            val items = withContext(Dispatchers.IO) { applicationContext.batchJobItemDB.getForJob(jobId) }
            val total = items.size
            var done = 0
            var failed = 0
            var lastNotify = 0L
            val succeededPaths = mutableListOf<String>()
            // sourcePath -> targetPath for every item that actually succeeded - the only thing that
            // makes Move undoable (RENAME/DELETE/etc don't need this, so it's built only when
            // relevant below). A flat Set<String> alone can't drive an undo: by the time Undo is
            // tapped, the file no longer lives at its old path, so the SET of old paths on its own
            // is useless for moving anything back - each one needs to be paired with where it
            // actually ended up.
            val movedPairs = mutableMapOf<String, String>()
            setProgress(androidx.work.workDataOf("done" to 0, "total" to total))

            for (item in items) {
                if (isStopped) break
                val success = withContext(Dispatchers.IO) { processItem(operation, item) }
                if (success) {
                    withContext(Dispatchers.IO) { applicationContext.batchJobItemDB.deleteItem(item.id ?: 0L) }
                    succeededPaths.add(item.sourcePath)
                    if (operation == BatchOperation.MOVE_FAST || operation == BatchOperation.MOVE_COPY_DELETE) {
                        movedPairs[item.targetPath] = item.sourcePath
                    }
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

            if (movedPairs.isNotEmpty()) {
                // paths = the files' CURRENT (post-move) locations - what the undo bar's count/label
                // actually refers to. extra maps each of those back to where it came from, which is
                // what the UndoType.MOVE handler (see GalleryNavHost) needs to move them back.
                UndoManager.push(UndoAction(paths = movedPairs.keys, type = UndoType.MOVE, extra = movedPairs))
            }
            RefreshBus.trigger()
            // A move/rename here can change what either home-screen widget should be showing (a
            // folder's thumbnail/count for MyWidgetProvider, the "recent media"/pending-move-count
            // for MoverWidgetProvider) - both otherwise only redraw on the OS's own schedule.
            org.fossify.gallery.helpers.MyWidgetProvider.requestImmediateUpdate(applicationContext)
            org.fossify.gallery.helpers.MoverWidgetProvider.requestImmediateUpdate(applicationContext)
            showResultNotification(operation, done, failed, isStopped)
            // WorkInfo.progress (set via setProgress above) is only readable while the worker is
            // RUNNING - WorkManager clears it back to Data.EMPTY once the worker reaches a terminal
            // state, so callers observing WorkInfo after completion (see FoldersMoverScreen) must read
            // the final counts from outputData instead, not progress.
            Result.success(androidx.work.workDataOf("done" to done, "failed" to failed, "total" to total))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Job $jobId failed", e)
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
                // Repoint the existing Room row's path columns in place (exactly what RENAME already
                // does below) instead of deleting it and waiting for a MediaStore rescan to reinsert
                // it - that rescan only knows MediaStore's own date_modified/date_taken, and Android's
                // MediaProvider resets date_modified to "now" on any write regardless of what's asked
                // for (confirmed live: an explicit ContentValues override for it is silently ignored),
                // which was the actual bug behind a moved file jumping to the top of the date-sorted
                // grid as if newly added. Updating in place keeps last_modified/date_taken/rating/
                // is_favorite exactly as they were.
                applicationContext.mediaDB.updateMedium(item.sourcePath, File(item.targetPath).parent ?: "", File(item.targetPath).name, item.targetPath)
                applicationContext.mediaCacheDB.deleteByPathSync(item.sourcePath)
                true
            }
            BatchOperation.MOVE_COPY_DELETE -> {
                val uri = MediaStoreOps.uriForPath(applicationContext, item.sourcePath)
                if (uri == null) {
                    // No MediaStore row for this path. The usual cause in a "move all pairs" run is a
                    // stale pair: the source folder was listed (flattenMoverPairs) before the batch
                    // started, but MediaStore never indexed some of those files (or indexed them
                    // under a different DATA path), so uriForPath finds nothing.
                    android.util.Log.w(TAG, "MOVE fail: no MediaStore uri for ${item.sourcePath}")
                    return false
                }
                val targetRel = MediaStoreOps.relativePathFor(File(item.targetPath).parent ?: "")
                // Same-volume moves (the common case - the Mover feature/widget used to always
                // byte-copy here even within internal storage, which was the actual cause of "why
                // is moving so slow") are just a RELATIVE_PATH update, exactly like MOVE_FAST above -
                // MediaProvider performs a real, near-instant filesystem rename for these instead of
                // the full read+write copy below. Only a genuine cross-volume move (e.g. internal
                // storage -> SD card) still needs the manual copy, since there's no single rename
                // syscall that can relocate bytes across two different filesystems.
                if (MediaStoreOps.sameVolume(item.sourcePath, item.targetPath)) {
                    if (!MediaStoreOps.move(applicationContext, uri, targetRel)) {
                        // update() returned 0 or threw. Most likely a name collision (target already
                        // has a file of that name) or MediaProvider rejecting the RELATIVE_PATH write.
                        android.util.Log.w(TAG, "MOVE fail: move() false for ${item.sourcePath} -> $targetRel")
                        return false
                    }
                } else {
                    val newUri = MediaStoreOps.copy(applicationContext, uri, File(item.targetPath).name, targetRel, MediaStoreOps.isVideoPath(item.sourcePath))
                    if (newUri == null) {
                        android.util.Log.w(TAG, "MOVE fail: copy() null for ${item.sourcePath} -> $targetRel")
                        return false
                    }
                    applicationContext.contentResolver.delete(uri, null, null)
                }
                // Same reasoning as MOVE_FAST above - this op additionally goes through a fresh
                // MediaStore insert() (see MediaStoreOps.copy()), whose own date_taken/date_modified
                // are best-effort at most (also confirmed live: MediaProvider's scanner can null out
                // date_taken again on a file with no real EXIF, which is the common case for e.g.
                // downloaded social-media images the Mover feature usually moves) - the local DB row
                // carrying the correct dates over directly is what actually matters for sort order.
                applicationContext.mediaDB.updateMedium(item.sourcePath, File(item.targetPath).parent ?: "", File(item.targetPath).name, item.targetPath)
                applicationContext.mediaCacheDB.deleteByPathSync(item.sourcePath)
                true
            }
            BatchOperation.COPY -> {
                val uri = MediaStoreOps.uriForPath(applicationContext, item.sourcePath) ?: return false
                val targetRel = MediaStoreOps.relativePathFor(File(item.targetPath).parent ?: "")
                MediaStoreOps.copy(applicationContext, uri, File(item.targetPath).name, targetRel, MediaStoreOps.isVideoPath(item.sourcePath)) != null
            }
        }
    } catch (e: Exception) {
        // Previously swallowed silently, which is why a batch could report "N failed" with no way
        // to tell why. A MediaProvider write throwing here (e.g. RecoverableSecurityException, or a
        // transient failure once the process has been hammering it for hundreds of moves) is a prime
        // suspect for "the later half of a big move-all run fails".
        android.util.Log.w(TAG, "processItem threw for ${item.sourcePath} -> ${item.targetPath}", e)
        false
    }

    private fun showResultNotification(operation: BatchOperation, done: Int, failed: Int, cancelled: Boolean) {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val text = when {
                cancelled -> applicationContext.getString(org.fossify.gallery.R.string.notif_batch_cancelled, done)
                failed > 0 -> applicationContext.getString(org.fossify.gallery.R.string.notif_batch_done_with_failures, done, failed)
                else -> applicationContext.getString(org.fossify.gallery.R.string.notif_batch_done, done)
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
        private const val TAG = "MediaBatchWorker"
        const val KEY_JOB_ID = "job_id"
        const val KEY_OPERATION = "operation"

        /** Fixed unique-work name for ALL batch ops. This is the fix for the stacking bug: it used to
         * be the random per-call jobId, so enqueueUniqueWork treated every "move all" tap as a
         * distinct job and REPLACE never replaced anything - a user re-tapping because nothing
         * appeared to happen piled up 8 concurrent 291-item jobs that then deadlocked each other in
         * WorkManager's retry backoff. A single fixed name means REPLACE actually cancels the
         * previous batch and runs exactly one at a time. Batch media ops touching MediaStore are
         * better serialized than run in parallel anyway (fewer provider write conflicts). */
        private const val UNIQUE_WORK_NAME = "media_batch_op"

        /** How many times WorkManager may retry after the worker's process is killed mid-run (a big
         * move on a memory-pressured device) before giving up. Without a cap the default 30s-backoff
         * retries accumulate into exactly the TIMING_DELAY-stuck jobs seen on-device. Read in doWork. */
        const val MAX_ATTEMPTS = 3

        /** Persists [items] under a fresh jobId and enqueues the worker; the caller must already have
         * obtained MediaStore write consent for MOVE_FAST/MOVE_COPY_DELETE/RENAME before calling this. */
        suspend fun enqueue(context: Context, operation: BatchOperation, items: List<BatchJobItem>): String {
            val jobId = java.util.UUID.randomUUID().toString()
            withContext(Dispatchers.IO) {
                context.batchJobItemDB.insertAll(items.map { it.copy(jobId = jobId) })
                // Drop any rows from a previous, now-superseded batch (its worker is about to be
                // REPLACEd and will never touch them). Keeps the table from accumulating dead rows.
                context.batchJobItemDB.deleteAllExcept(jobId)
            }
            val request = OneTimeWorkRequestBuilder<MediaBatchWorker>()
                .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).putString(KEY_OPERATION, operation.name).build())
                .addTag(jobId)
                .addTag(UNIQUE_WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            return jobId
        }
    }
}
