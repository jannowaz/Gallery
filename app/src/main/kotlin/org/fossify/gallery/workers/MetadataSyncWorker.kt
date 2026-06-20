package org.fossify.gallery.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.XmpWriter
import org.fossify.gallery.models.MediaCache
import java.io.File
import java.util.concurrent.TimeUnit

class MetadataSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fullScan = inputData.getBoolean("full_scan", false)
        val folderPath = inputData.getString("folder_path")
        val logTag = "MetadataSync"
        return try {
            val now = System.currentTimeMillis()
            val staleThreshold = if (fullScan) 0L else now - 6 * 60 * 60 * 1000L

            val allMedia = when {
                folderPath != null -> {
                    applicationContext.mediaDB.getNewestMedia(Int.MAX_VALUE)
                        .filter { it.path.startsWith("$folderPath/") || it.parentPath == folderPath }
                }
                fullScan -> {
                    applicationContext.mediaDB.getNewestMedia(Int.MAX_VALUE)
                }
                else -> {
                    applicationContext.mediaDB.getNewestMedia(10000)
                }
            }

            android.util.Log.i(logTag, "Loaded ${allMedia.size} media files to scan (folder=$folderPath, full=$fullScan)")

            val existingCache = applicationContext.mediaCacheDB.getAllTagged().associateBy { it.fullPath }
            val batch = mutableListOf<MediaCache>()
            var processed = 0
            var tagged = 0
            var errors = 0

            for (m in allMedia) {
                if (isStopped) break
                try {
                    val cached = existingCache[m.path]
                    if (!fullScan && folderPath == null && cached != null && cached.lastScanned > staleThreshold) continue
                    if (!fullScan && folderPath == null && cached == null) continue

                    val xmp = XmpWriter.read(m.path)
                    val hasData = xmp.tags.isNotEmpty() || xmp.rating > 0
                    if (hasData) tagged++

                    if (!fullScan && folderPath == null && !hasData) continue

                    batch.add(MediaCache(
                        fullPath = m.path,
                        tags = xmp.tags.joinToString(","),
                        rating = xmp.rating,
                        lastScanned = now,
                    ))
                    processed++
                    if (batch.size >= 200) {
                        applicationContext.mediaCacheDB.upsertAll(batch.toList())
                        batch.clear()
                    }
                } catch (_: Exception) { errors++ }
            }
            if (batch.isNotEmpty()) {
                applicationContext.mediaCacheDB.upsertAll(batch.toList())
            }
            android.util.Log.i(logTag, "Done: ${allMedia.size} files, $processed synced, $tagged tagged, $errors errors")
            if (processed > 0) RefreshBus.trigger()

            if (folderPath != null) {
                showNotification("Scan abgeschlossen", "${File(folderPath).name}: $tagged Dateien mit Tags/Bewertungen gefunden, $processed synchronisiert")
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e(logTag, "Sync failed", e)
            Result.failure()
        }
    }

    private fun showNotification(title: String, text: String) {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "metadata_sync"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(channelId, "Metadaten-Scan", NotificationManager.IMPORTANCE_LOW))
            }
            nm.notify(2001, NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build())
        } catch (_: Exception) { }
    }

    companion object {
        private const val WORK_NAME = "metadata_sync"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<MetadataSyncWorker>(12, TimeUnit.HOURS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
        }

        fun scheduleNow(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<MetadataSyncWorker>()
                .addTag("${WORK_NAME}_now")
                .setInitialDelay(3, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("${WORK_NAME}_now", ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun scheduleFullScan(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<MetadataSyncWorker>()
                .addTag("${WORK_NAME}_full")
                .setInitialDelay(1, TimeUnit.SECONDS)
                .setInputData(Data.Builder().putBoolean("full_scan", true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("${WORK_NAME}_full", ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun scheduleFolderScan(context: Context, folderPath: String) {
            val workRequest = OneTimeWorkRequestBuilder<MetadataSyncWorker>()
                .addTag("${WORK_NAME}_folder")
                .setInitialDelay(500, TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString("folder_path", folderPath).putBoolean("full_scan", true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("${WORK_NAME}_folder", ExistingWorkPolicy.REPLACE, workRequest)
        }
    }
}
