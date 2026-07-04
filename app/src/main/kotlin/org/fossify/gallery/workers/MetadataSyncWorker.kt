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
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.XmpWriter
import org.fossify.gallery.models.MediaCache
import org.fossify.gallery.models.MediaTag
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
            .addAction(buildCancelAction())
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            ForegroundInfo(2002, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            ForegroundInfo(2002, notification)
    }

    private fun buildCancelAction(): NotificationCompat.Action {
        val intent = android.content.Intent(applicationContext, org.fossify.gallery.receivers.CancelMetadataScanReceiver::class.java)
            .setAction(org.fossify.gallery.receivers.CancelMetadataScanReceiver.ACTION)
        val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        val pi = android.app.PendingIntent.getBroadcast(applicationContext, 0, intent, flags)
        return NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, "Abbrechen", pi)
    }

    override suspend fun doWork(): Result {
        val fullScan = inputData.getBoolean("full_scan", false)
        val folderPath = inputData.getString("folder_path")
        val dateStart = inputData.getLong("date_start", 0L)
        val dateEnd = inputData.getLong("date_end", Long.MAX_VALUE)
        val incremental = inputData.getBoolean("incremental", false)
        
        return try {
            if (fullScan && folderPath == null && dateStart == 0L && !incremental) {
                fullScanFromMediaStore()
            } else {
                dbScan(fullScan, folderPath, dateStart, dateEnd, incremental)
            }
            if (isStopped) {
                cancelProgress()
                showNotification("Scan abgebrochen", "Der Scan wurde gestoppt.")
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelProgress()
            showNotification("Scan abgebrochen", "Der Scan wurde gestoppt.")
            throw e
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
        showProgress(0, total, 0, 0)
        val batch = mutableListOf<MediaCache>()
        var processed = 0; var foundTags = 0; var foundRatings = 0; var lastNotify = 0L
        val hierarchyAccum = mutableMapOf<String, String>()
        for (p in paths) {
            if (isStopped) break
            try {
                val xmp = XmpWriter.read(p)
                if (xmp.tags.isNotEmpty()) foundTags++
                if (xmp.rating > 0) { foundRatings++; try { applicationContext.mediaDB.updateRating(p, xmp.rating) } catch (_: Exception) { } }
                if (xmp.hierarchy.isNotEmpty()) hierarchyAccum.putAll(xmp.hierarchy)
                batch.add(MediaCache(fullPath = p, tags = xmp.tags.joinToString(","), rating = xmp.rating, lastScanned = now))
                if (xmp.tags.isNotEmpty()) syncMediaTags(p, xmp.tags)
                if (batch.size >= 200) { applicationContext.mediaCacheDB.upsertAll(batch.toList()); batch.clear() }
            } catch (_: Exception) { }
            processed++
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastNotify > 500) { lastNotify = nowMs; showProgress(processed, total, foundTags, foundRatings) }
        }
        if (batch.isNotEmpty()) applicationContext.mediaCacheDB.upsertAll(batch.toList())
        if (hierarchyAccum.isNotEmpty()) try { applicationContext.config.tagHierarchy = hierarchyAccum.toMutableMap() } catch (_: Exception) { }
        RefreshBus.trigger()
        if (!isStopped) showNotification("Scan abgeschlossen", "$total Dateien · $foundTags mit Tags · $foundRatings bewertet")
    }

    private suspend fun dbScan(fullScan: Boolean, folderPath: String?, dateStart: Long = 0L, dateEnd: Long = Long.MAX_VALUE, incremental: Boolean = false) {
        val logTag = "MetadataSync"
        val now = System.currentTimeMillis()
        val lastSync = if (incremental) applicationContext.config.lastSyncTimestamp else 0L
        val staleThreshold = if (fullScan) 0L else now - 6 * 60 * 60 * 1000L

        var allMedia = when {
            folderPath != null -> {
                applicationContext.mediaDB.getNewestMedia(Int.MAX_VALUE)
                    .filter { it.path.startsWith("$folderPath/") || it.parentPath == folderPath }
            }
            fullScan -> applicationContext.mediaDB.getNewestMedia(Int.MAX_VALUE)
            else -> applicationContext.mediaDB.getNewestMedia(10000)
        }
        
        if (dateStart > 0 || dateEnd < Long.MAX_VALUE) {
            allMedia = allMedia.filter { m ->
                val t = maxOf(m.taken, m.modified)
                t in dateStart..dateEnd
            }
        }
        
        if (incremental) {
            allMedia = allMedia.filter { maxOf(it.taken, it.modified) > lastSync }
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
                if (xmp.tags.isNotEmpty()) syncMediaTags(m.path, xmp.tags)
                processed++
                if (batch.size >= 200) { applicationContext.mediaCacheDB.upsertAll(batch.toList()); batch.clear() }
            } catch (_: Exception) { }
        }
        if (batch.isNotEmpty()) applicationContext.mediaCacheDB.upsertAll(batch.toList())
        android.util.Log.i(logTag, "Done: ${allMedia.size} files, $processed synced, $tagged tagged")
        if (processed > 0) RefreshBus.trigger()
        if (folderPath != null && !isStopped) {
            showNotification("Scan abgeschlossen", "${File(folderPath).name}: $tagged mit Tags/Bewertungen, $processed synchronisiert")
        }
    }

    /** Mirrors a scanned file's XMP tags into the normalized `media_tags` table (replace-all for
     * that path), same as [org.fossify.gallery.helpers.MediaRepository]'s single-file sync. */
    private suspend fun syncMediaTags(path: String, tags: List<String>) {
        try {
            applicationContext.mediaTagDB.deleteAllForPath(path)
            tags.filter { it.isNotBlank() }.distinct().forEach { applicationContext.mediaTagDB.insert(MediaTag(mediaPath = path, tag = it)) }
        } catch (_: Exception) { }
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
                .addAction(buildCancelAction())
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

        fun scheduleAdvancedScan(context: Context, folderPath: String?, dateStart: Long, dateEnd: Long, incremental: Boolean, chargingOnly: Boolean) {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiresCharging(chargingOnly)
                .build()
            
            val data = Data.Builder()
                .putString("folder_path", folderPath)
                .putLong("date_start", dateStart)
                .putLong("date_end", dateEnd)
                .putBoolean("incremental", incremental)
                .putBoolean("full_scan", true)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<MetadataSyncWorker>()
                .addTag("${WORK_NAME}_advanced")
                .setConstraints(constraints)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("${WORK_NAME}_advanced", ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME)
            wm.cancelUniqueWork("${WORK_NAME}_now")
            wm.cancelUniqueWork("${WORK_NAME}_full")
            wm.cancelUniqueWork("${WORK_NAME}_folder")
            try {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(2002)
            } catch (_: Exception) { }
        }

        fun cancelAutomatic(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME)
            wm.cancelUniqueWork("${WORK_NAME}_now")
        }
    }
}
