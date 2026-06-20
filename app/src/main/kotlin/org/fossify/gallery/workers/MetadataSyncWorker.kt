package org.fossify.gallery.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
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

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0, 0, 0, 0)

    private fun createForegroundInfo(done: Int, total: Int, tags: Int, ratings: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "metadata_sync"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, "Metadaten-Scan", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Tags & Bewertungen scannen")
            .setContentText(if (total > 0) "$done/$total · $tags Tags · $ratings Bewertungen" else "Vorbereiten…")
            .setProgress(total.coerceAtLeast(1), done.coerceAtMost(total.coerceAtLeast(1)), total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            ForegroundInfo(2002, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            ForegroundInfo(2002, notification)
    }

    override suspend fun doWork(): Result {
        val fullScan = inputData.getBoolean("full_scan", false)
        val folderPath = inputData.getString("folder_path")
        return try {
            if (fullScan && folderPath == null) fullScanFromMediaStore() else dbScan(fullScan, folderPath)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("MetadataSync", "Sync failed", e)
            cancelProgress()
            Result.failure()
        }
    }

    private suspend fun fullScanFromMediaStore() {
        val now = System.currentTimeMillis()
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(MediaStore.MediaColumns.DATA)
        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        val paths = ArrayList<String>()
        applicationContext.contentResolver.query(uri, proj, sel, args, null)?.use { c ->
            val col = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            while (c.moveToNext()) c.getString(col)?.let { paths.add(it) }
        }
        val total = paths.size
        try { setForeground(createForegroundInfo(0, total, 0, 0)) } catch (_: Exception) { }
        val batch = mutableListOf<MediaCache>()
        var processed = 0; var foundTags = 0; var foundRatings = 0; var lastNotify = 0L
        for (p in paths) {
            if (isStopped) break
            try {
                val xmp = XmpWriter.read(p)
                if (xmp.tags.isNotEmpty()) foundTags++
                if (xmp.rating > 0) { foundRatings++; try { applicationContext.mediaDB.updateRating(p, xmp.rating) } catch (_: Exception) { } }
                batch.add(MediaCache(fullPath = p, tags = xmp.tags.joinToString(","), rating = xmp.rating, lastScanned = now))
                if (batch.size >= 200) { applicationContext.mediaCacheDB.upsertAll(batch.toList()); batch.clear() }
            } catch (_: Exception) { }
            processed++
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastNotify > 500) { lastNotify = nowMs; try { setForeground(createForegroundInfo(processed, total, foundTags, foundRatings)) } catch (_: Exception) { } }
        }
        if (batch.isNotEmpty()) applicationContext.mediaCacheDB.upsertAll(batch.toList())
        RefreshBus.trigger()
        showNotification("Scan abgeschlossen", "$total Dateien · $foundTags mit Tags · $foundRatings bewertet")
    }

    private suspend fun dbScan(fullScan: Boolean, folderPath: String?) {
        val logTag = "MetadataSync"
        val now = System.currentTimeMillis()
        val staleThreshold = if (fullScan) 0L else now - 6 * 60 * 60 * 1000L

        val allMedia = when {
            folderPath != null -> {
                applicationContext.mediaDB.getNewestMedia(Int.MAX_VALUE)
                    .filter { it.path.startsWith("$folderPath/") || it.parentPath == folderPath }
            }
            fullScan -> applicationContext.mediaDB.getNewestMedia(Int.MAX_VALUE)
            else -> applicationContext.mediaDB.getNewestMedia(10000)
        }

        val existingCache = applicationContext.mediaCacheDB.getAllTagged().associateBy { it.fullPath }
        val batch = mutableListOf<MediaCache>()
        var processed = 0
        var tagged = 0

        for (m in allMedia) {
            if (isStopped) break
            try {
                val cached = existingCache[m.path]
                if (!fullScan && folderPath == null && cached != null && cached.lastScanned > staleThreshold) continue
                if (!fullScan && folderPath == null && cached == null) continue

                val xmp = XmpWriter.read(m.path)
                val hasData = xmp.tags.isNotEmpty() || xmp.rating > 0
                if (hasData) tagged++
                if (xmp.rating > 0) { try { applicationContext.mediaDB.updateRating(m.path, xmp.rating) } catch (_: Exception) { } }

                if (!fullScan && folderPath == null && !hasData) continue

                batch.add(MediaCache(fullPath = m.path, tags = xmp.tags.joinToString(","), rating = xmp.rating, lastScanned = now))
                processed++
                if (batch.size >= 200) { applicationContext.mediaCacheDB.upsertAll(batch.toList()); batch.clear() }
            } catch (_: Exception) { }
        }
        if (batch.isNotEmpty()) applicationContext.mediaCacheDB.upsertAll(batch.toList())
        android.util.Log.i(logTag, "Done: ${allMedia.size} files, $processed synced, $tagged tagged")
        if (processed > 0) RefreshBus.trigger()
        if (folderPath != null) {
            showNotification("Scan abgeschlossen", "${File(folderPath).name}: $tagged mit Tags/Bewertungen, $processed synchronisiert")
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

    private fun showProgress(done: Int, total: Int, tags: Int, ratings: Int) {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "metadata_sync"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(channelId, "Metadaten-Scan", NotificationManager.IMPORTANCE_LOW))
            }
            nm.notify(2002, NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Tags & Bewertungen scannen")
                .setContentText("$done/$total · $tags Tags · $ratings Bewertungen")
                .setProgress(total.coerceAtLeast(1), done.coerceAtMost(total.coerceAtLeast(1)), total == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build())
        } catch (_: Exception) { }
    }

    private fun cancelProgress() {
        try { (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(2002) } catch (_: Exception) { }
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
