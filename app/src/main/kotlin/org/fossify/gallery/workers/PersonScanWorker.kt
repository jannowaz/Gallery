package org.fossify.gallery.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.fossify.gallery.R
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.mediaPersonScanDB
import org.fossify.gallery.helpers.PersonPresenceDetector
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.LibraryActivityKind
import org.fossify.gallery.helpers.LibraryActivityStatus
import org.fossify.gallery.helpers.personScanSignature
import org.fossify.gallery.models.MediaPersonScan
import org.fossify.gallery.models.PersonPresence
import java.io.File

/** Background, folder-local scan that creates a review queue; it never changes or deletes media. */
class PersonScanWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(0, 0, 0)

    override suspend fun doWork(): Result {
        val folderPath = normalizeFolderPath(inputData.getString(KEY_FOLDER_PATH).orEmpty())
        if (folderPath.isBlank()) return Result.failure()
        val includeSubfolders = inputData.getBoolean(KEY_INCLUDE_SUBFOLDERS, false)
        val activityKey = "person:$folderPath"

        var processed = 0
        var noPerson = 0
        var afterRowId = 0L
        val detector = PersonPresenceDetector()
        try {
            LibraryActivityStatus.started(activityKey, LibraryActivityKind.PERSON_SCAN, folderPath, includeSubfolders)
            val total = if (includeSubfolders) {
                applicationContext.mediaDB.getPersonScanRowCountForFolderTree(folderPath, descendantPattern(folderPath))
            } else {
                applicationContext.mediaDB.getPersonScanRowCountForFolder(folderPath)
            }
            setForeground(foregroundInfo(0, total, 0))
            setProgress(progressData(0, total, 0))
            while (!isStopped) {
                val page = if (includeSubfolders) {
                    applicationContext.mediaDB.getPersonScanRowsForFolderTreeAfter(
                        folderPath,
                        descendantPattern(folderPath),
                        afterRowId,
                        PAGE_SIZE,
                    )
                } else {
                    applicationContext.mediaDB.getPersonScanRowsForFolderAfter(folderPath, afterRowId, PAGE_SIZE)
                }
                if (page.isEmpty()) break
                afterRowId = page.last().rowId
                val existing = applicationContext.mediaPersonScanDB.getByPaths(page.map { it.path }).associateBy { it.mediaPath }
                val writes = ArrayList<MediaPersonScan>(page.size)
                page.forEach { item ->
                    if (isStopped) return@forEach
                    val signature = personScanSignature(item.path, item.modified, item.size)
                    val cached = existing[item.path]
                    // "Keep" is a deliberate cleanup decision, not merely a cache entry. Leave it
                    // untouched even when a file's timestamp changes, so a later scan cannot put it
                    // back into the suggestion queue.
                    val result = if (cached?.isKept == true || cached?.contentSignature == signature) {
                        runCatching { PersonPresence.valueOf(cached.result) }.getOrDefault(PersonPresence.UNCERTAIN)
                    } else {
                        detector.analyze(item.path).let { outcome ->
                            writes += MediaPersonScan(
                                mediaPath = item.path,
                                folderPath = item.parentPath,
                                contentSignature = signature,
                                result = outcome.presence.databaseValue,
                                personCoverage = outcome.coverage,
                                isKept = false,
                            )
                            outcome.presence
                        }
                    }
                    if (result == PersonPresence.NO_PERSON && cached?.isKept != true) noPerson++
                    processed++
                }
                if (writes.isNotEmpty()) applicationContext.mediaPersonScanDB.upsertAll(writes)
                setProgress(progressData(processed, total, noPerson))
                setForeground(foregroundInfo(processed, total, noPerson))
                LibraryActivityStatus.progress(activityKey, processed, total, noPerson)
                // Keep each ML page bounded and cooperative with foreground browsing/thumbnail
                // work. Page size is deliberately small; yielding here prevents a long review
                // scan from monopolising the app's worker threads on large folders.
                kotlinx.coroutines.yield()
            }
            if (!isStopped) {
                RefreshBus.trigger(pathsChanged = false, collectionsChanged = false)
                LibraryActivityStatus.completed(activityKey, processed, total, noPerson)
                showFinished(folderPath, processed, noPerson)
            } else {
                LibraryActivityStatus.cancelled(activityKey, processed, total, noPerson)
            }
            return Result.success(progressData(processed, total, noPerson))
        } catch (e: kotlinx.coroutines.CancellationException) {
            LibraryActivityStatus.cancelled(activityKey, processed, 0, noPerson)
            throw e
        } catch (e: Exception) {
            android.util.Log.e("PersonScan", "Person scan failed", e)
            LibraryActivityStatus.failed(activityKey, e, processed, 0, noPerson)
            return Result.retry()
        } finally {
            detector.close()
            if (isStopped) cancelNotification()
        }
    }

    private fun foregroundInfo(done: Int, total: Int, noPerson: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.person_scan_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(applicationContext.getString(R.string.person_scan_notification_title))
            .setContentText(applicationContext.getString(R.string.person_scan_notification_progress, done, noPerson))
            .setProgress(total.coerceAtLeast(1), done.coerceAtMost(total.coerceAtLeast(1)), total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun showFinished(folderPath: String, done: Int, noPerson: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(applicationContext.getString(R.string.person_scan_finished_title))
                .setContentText(applicationContext.getString(R.string.person_scan_finished_text, File(folderPath).name, noPerson, done))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun cancelNotification() {
        runCatching { (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID) }
    }

    private fun progressData(done: Int, total: Int, noPerson: Int): Data = Data.Builder()
        .putInt(KEY_DONE, done)
        .putInt(KEY_TOTAL, total)
        .putInt(KEY_NO_PERSON, noPerson)
        .build()

    companion object {
        private const val WORK_PREFIX = "person_scan"
        private const val CHANNEL_ID = "person_scan"
        private const val NOTIFICATION_ID = 2011
        private const val PAGE_SIZE = 64
        const val KEY_FOLDER_PATH = "folder_path"
        const val KEY_INCLUDE_SUBFOLDERS = "include_subfolders"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_NO_PERSON = "no_person"

        fun uniqueWorkName(folderPath: String) = "${WORK_PREFIX}_${folderPath.hashCode()}"

        /** Keeps Explorer, albums and database rows on one canonical folder key. */
        fun normalizeFolderPath(path: String): String {
            val trimmed = path.trim()
            return if (trimmed.length > 1) trimmed.trimEnd('/') else trimmed
        }

        fun scheduleFolderScan(context: Context, folderPath: String, includeSubfolders: Boolean = false) {
            val normalizedFolderPath = normalizeFolderPath(folderPath)
            val request = OneTimeWorkRequestBuilder<PersonScanWorker>()
                // This is an explicit user action, so it must start promptly. The worker is a
                // foreground data-sync task and processes small pages, rather than waiting for
                // battery/storage constraints intended for automatic maintenance jobs.
                .setInputData(
                    Data.Builder()
                        .putString(KEY_FOLDER_PATH, normalizedFolderPath)
                        .putBoolean(KEY_INCLUDE_SUBFOLDERS, includeSubfolders)
                        .build(),
                )
                .addTag(WORK_PREFIX)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueWorkName(normalizedFolderPath), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_PREFIX)
        }

        private fun descendantPattern(folderPath: String): String {
            val escapedPath = folderPath
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return if (folderPath == "/") "/%" else "$escapedPath/%"
        }
    }
}
