package org.fossify.gallery.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.fossify.gallery.extensions.mediaCacheDB
import org.fossify.gallery.extensions.mediaTagDB
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.RefreshBus
import org.fossify.gallery.helpers.LibraryActivityKind
import org.fossify.gallery.helpers.LibraryActivityStatus
import org.fossify.gallery.helpers.MetadataWriteCoordinator
import org.fossify.gallery.helpers.XmpWriter
import org.fossify.gallery.interfaces.MetadataScanRow
import org.fossify.gallery.models.MediaCache
import java.io.File
import java.util.concurrent.TimeUnit

class MetadataSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private var activityKey = "metadata"
    private var activityDone = 0
    private var activityTotal = 0
    private var activityMatches = 0

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0, 0, 0, 0)

    private fun createForegroundInfo(done: Int, total: Int, tags: Int, ratings: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "metadata_sync"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(channelId, applicationContext.getString(org.fossify.gallery.R.string.notif_channel_metadata_sync), NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(applicationContext.getString(org.fossify.gallery.R.string.notif_metadata_scanning_title))
            .setContentText(if (total > 0) applicationContext.getString(org.fossify.gallery.R.string.notif_metadata_scanning_progress, done, total, tags, ratings) else applicationContext.getString(org.fossify.gallery.R.string.notif_preparing))
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
        return NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, applicationContext.getString(org.fossify.gallery.R.string.cancel), pi)
    }

    override suspend fun doWork(): Result {
        val fullScan = inputData.getBoolean("full_scan", false)
        val folderPath = inputData.getString("folder_path")
        val dateStart = inputData.getLong("date_start", 0L)
        val dateEnd = inputData.getLong("date_end", Long.MAX_VALUE)
        val incremental = inputData.getBoolean("incremental", false)
        activityKey = if (folderPath != null) "metadata:$folderPath" else if (fullScan) "metadata:full" else "metadata:incremental"
        LibraryActivityStatus.started(
            key = activityKey,
            kind = LibraryActivityKind.METADATA_SCAN,
            sourcePath = folderPath,
        )
        
        return try {
            if (fullScan && folderPath == null && dateStart == 0L && !incremental) {
                fullScanFromMediaStore()
            } else {
                dbScan(fullScan, folderPath, dateStart, dateEnd, incremental)
            }
            if (isStopped) {
                cancelProgress()
                LibraryActivityStatus.cancelled(activityKey, activityDone, activityTotal, activityMatches)
                showNotification(applicationContext.getString(org.fossify.gallery.R.string.notif_scan_cancelled_title), applicationContext.getString(org.fossify.gallery.R.string.notif_scan_cancelled_text))
            } else {
                LibraryActivityStatus.completed(activityKey, activityDone, activityTotal, activityMatches)
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            cancelProgress()
            LibraryActivityStatus.cancelled(activityKey, activityDone, activityTotal, activityMatches)
            showNotification(applicationContext.getString(org.fossify.gallery.R.string.notif_scan_cancelled_title), applicationContext.getString(org.fossify.gallery.R.string.notif_scan_cancelled_text))
            throw e
        } catch (e: Exception) {
            android.util.Log.e("MetadataSync", "Sync failed", e)
            cancelProgress()
            LibraryActivityStatus.failed(activityKey, e, activityDone, activityTotal, activityMatches)
            Result.failure()
        }
    }

    private class FullScanStats {
        var processed = 0
        var foundTags = 0
        var foundRatings = 0
        var peakHeapBytes = 0L
        var peakPssKb = 0
        var peakDalvikPssKb = 0
        var peakNativePssKb = 0
        val startedAt = System.currentTimeMillis()
    }

    private suspend fun fullScanFromMediaStore() {
        val now = System.currentTimeMillis()
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(MediaStore.MediaColumns.DATA)
        val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
        val batch = mutableListOf<MediaCache>()
        val stats = FullScanStats()
        val hierarchyAccum = mutableMapOf<String, String>()
        var total = 0
        var lastNotify = 0L
        applicationContext.contentResolver.query(uri, proj, sel, args, null)?.use { c ->
            val col = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            total = c.count.coerceAtLeast(0)
            showProgress(0, total, 0, 0)
            recordMemory(stats)
            // Keep both MediaStore paths and the cache lookup bounded. A 200k-item library used
            // to retain the complete path list plus every tagged cache row before the first XMP
            // file was processed, which created avoidable heap pressure during a full rescan.
            val pathChunk = ArrayList<String>(FULL_SCAN_CHUNK_SIZE)
            while (c.moveToNext() && !isStopped) {
                c.getString(col)?.let { pathChunk += it }
                if (pathChunk.size >= FULL_SCAN_CHUNK_SIZE) {
                    processFullScanChunk(pathChunk, now, batch, hierarchyAccum, stats)
                    pathChunk.clear()
                    recordMemory(stats)
                    kotlinx.coroutines.yield()
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastNotify > 500) {
                        lastNotify = nowMs
                        showProgress(stats.processed, total, stats.foundTags, stats.foundRatings)
                    }
                }
            }
            if (pathChunk.isNotEmpty() && !isStopped) {
                processFullScanChunk(pathChunk, now, batch, hierarchyAccum, stats)
                recordMemory(stats)
                showProgress(stats.processed, total, stats.foundTags, stats.foundRatings)
            }
        } ?: showProgress(0, 0, 0, 0)
        if (batch.isNotEmpty()) applicationContext.mediaCacheDB.upsertAllIfNewer(batch.toList())
        // Config is the app's source of truth after the user has assigned/re-parented a tag. Merge
        // newly discovered external hierarchy entries without replacing the complete config with
        // only the files that happened to be scanned in this run (which could be a retry subset).
        if (hierarchyAccum.isNotEmpty()) try {
            val configured = applicationContext.config.tagHierarchy
            val merged = configured.toMutableMap()
            hierarchyAccum.forEach { (child, parent) -> if (child !in merged) merged[child] = parent }
            if (merged != configured) applicationContext.config.tagHierarchy = merged
        } catch (_: Exception) { }
        RefreshBus.trigger(pathsChanged = false)
        android.util.Log.i("MetadataSync", "Full scan: ${stats.processed} files, ${stats.foundTags} tagged, ${stats.foundRatings} rated, ${System.currentTimeMillis() - stats.startedAt} ms, peak heap ${heapMb(stats.peakHeapBytes)} MB, peak PSS ${stats.peakPssKb / 1024} MB (Dalvik ${stats.peakDalvikPssKb / 1024} MB, native ${stats.peakNativePssKb / 1024} MB)")
        if (!isStopped) showNotification(
            applicationContext.getString(org.fossify.gallery.R.string.notif_scan_complete_title),
            applicationContext.getString(org.fossify.gallery.R.string.notif_scan_complete_full_text, total, stats.foundTags, stats.foundRatings),
        )
    }

    private suspend fun processFullScanChunk(
        paths: List<String>,
        now: Long,
        batch: MutableList<MediaCache>,
        hierarchyAccum: MutableMap<String, String>,
        stats: FullScanStats,
    ) {
        // A bounded IN query keeps retry detection exact without retaining the complete cache in
        // memory. FULL_SCAN_CHUNK_SIZE stays below SQLite's common bind-parameter limit.
        val existingCache = try {
            applicationContext.mediaCacheDB.getByPaths(paths).associateBy { it.fullPath }
        } catch (_: Exception) {
            emptyMap()
        }
        for (p in paths) {
            if (isStopped) break
            try {
                val cached = existingCache[p]
                // Only use the short retry window after WorkManager has actually retried this
                // worker. An explicit full scan must reread fresh metadata on its first run,
                // otherwise externally removed tags could remain cached for up to ten minutes.
                if (runAttemptCount > 0 && cached != null && now - cached.lastScanned < RETRY_SKIP_WINDOW_MS) {
                    if (cached.tags.isNotBlank()) stats.foundTags++
                    if (cached.rating > 0) stats.foundRatings++
                } else {
                    // readOrNull: skip a file whose XMP couldn't be read rather than caching it as
                    // empty (which would blank its tag cache) or leaving its rating stale.
                    // Keep the file read and the corresponding DB rating update under the same
                    // per-path lock as UI metadata edits. Otherwise a scan could read an old XMP
                    // snapshot and write that rating after the user had already changed it.
                    val xmp = MetadataWriteCoordinator.withLock(p) {
                        XmpWriter.readOrNull(p)?.also { current ->
                            if (cached?.rating != current.rating) {
                                try { applicationContext.mediaDB.updateRating(p, current.rating) } catch (_: Exception) { }
                            }
                        }
                    }
                    if (xmp != null) {
                        if (xmp.tags.isNotEmpty()) stats.foundTags++
                        if (xmp.rating > 0) stats.foundRatings++
                        if (xmp.hierarchy.isNotEmpty()) hierarchyAccum.putAll(xmp.hierarchy)
                        batch.add(MediaCache(fullPath = p, tags = xmp.tags.joinToString(","), rating = xmp.rating, lastScanned = now))
                        // Replace even an empty tag list. Otherwise removing the last keyword in
                        // another editor left the old normalized media_tags row searchable forever.
                        syncMediaTags(p)
                        if (batch.size >= 200) {
                            applicationContext.mediaCacheDB.upsertAllIfNewer(batch.toList())
                            batch.clear()
                        }
                    }
                }
            } catch (_: Exception) { }
            stats.processed++
        }
    }

    private suspend fun dbScan(fullScan: Boolean, folderPath: String?, dateStart: Long = 0L, dateEnd: Long = Long.MAX_VALUE, incremental: Boolean = false) {
        val logTag = "MetadataSync"
        val now = System.currentTimeMillis()
        val lastSync = if (incremental) applicationContext.config.lastSyncTimestamp else 0L
        val staleThreshold = if (fullScan) 0L else now - 6 * 60 * 60 * 1000L
        val stats = FullScanStats()
        val batch = ArrayList<MediaCache>(CACHE_WRITE_BATCH_SIZE)
        val folderPrefix = folderPath?.let { "$it/%" }
        val newestBoundedScan = !fullScan && folderPath == null && !incremental && dateStart == 0L && dateEnd == Long.MAX_VALUE
        val maxCandidates = if (newestBoundedScan) 10_000 else Int.MAX_VALUE
        var afterRowId = 0L
        var offset = 0
        var candidates = 0
        var tagged = 0

        while (!isStopped && candidates < maxCandidates) {
            val pageSize = minOf(FULL_SCAN_CHUNK_SIZE, maxCandidates - candidates)
            val page = if (newestBoundedScan) {
                applicationContext.mediaDB.getNewestMetadataScanRows(pageSize, offset)
            } else if (folderPath != null && folderPrefix != null) {
                applicationContext.mediaDB.getMetadataScanRowsForFolderAfter(folderPath, folderPrefix, afterRowId, pageSize)
            } else {
                applicationContext.mediaDB.getMetadataScanRowsAfter(afterRowId, pageSize)
            }
            if (page.isEmpty()) break
            candidates += page.size
            if (newestBoundedScan) offset += page.size else afterRowId = page.last().rowId
            val existingCache = applicationContext.mediaCacheDB.getByPaths(page.map { it.path }).associateBy { it.fullPath }
            processDbScanPage(page, existingCache, fullScan, folderPath, dateStart, dateEnd, incremental, lastSync, staleThreshold, now, batch, stats) { hasData ->
                if (hasData) tagged++
            }
            // Yield between bounded DB pages. This keeps a 200k-item maintenance scan friendly to
            // viewer decoding and user-triggered metadata writes on the same app process.
            activityDone = stats.processed
            activityTotal = 0
            activityMatches = tagged
            LibraryActivityStatus.progress(activityKey, activityDone, activityTotal, activityMatches)
            kotlinx.coroutines.yield()
            recordMemory(stats)
            if (page.size < pageSize) break
        }

        if (batch.isNotEmpty()) applicationContext.mediaCacheDB.upsertAllIfNewer(batch.toList())
        android.util.Log.i(logTag, "DB scan: $candidates candidates, ${stats.processed} synced, $tagged tagged, ${System.currentTimeMillis() - stats.startedAt} ms, peak heap ${heapMb(stats.peakHeapBytes)} MB, peak PSS ${stats.peakPssKb / 1024} MB (Dalvik ${stats.peakDalvikPssKb / 1024} MB, native ${stats.peakNativePssKb / 1024} MB)")
        if (stats.processed > 0) RefreshBus.trigger(pathsChanged = false)
        if (folderPath != null && !isStopped) {
            showNotification(
                applicationContext.getString(org.fossify.gallery.R.string.notif_scan_complete_title),
                applicationContext.getString(org.fossify.gallery.R.string.notif_scan_complete_folder_text, File(folderPath).name, tagged, stats.processed),
            )
        }
    }

    private suspend fun processDbScanPage(
        page: List<MetadataScanRow>,
        existingCache: Map<String, MediaCache>,
        fullScan: Boolean,
        folderPath: String?,
        dateStart: Long,
        dateEnd: Long,
        incremental: Boolean,
        lastSync: Long,
        staleThreshold: Long,
        now: Long,
        batch: MutableList<MediaCache>,
        stats: FullScanStats,
        onDataFound: (Boolean) -> Unit,
    ) {
        for (row in page) {
            if (isStopped) break
            try {
                val timestamp = maxOf(row.taken, row.modified)
                if ((dateStart > 0 || dateEnd < Long.MAX_VALUE) && timestamp !in dateStart..dateEnd) continue
                if (incremental && timestamp <= lastSync) continue
                val cached = existingCache[row.path]
                if (!fullScan && folderPath == null && cached != null && cached.lastScanned > staleThreshold) continue
                if (!fullScan && folderPath == null && cached == null) continue

                // readOrNull: a file whose XMP read failed is skipped, not cached as empty/stale.
                // Read and mirror the rating while holding the same lock as UI metadata edits.
                val xmp = MetadataWriteCoordinator.withLock(row.path) {
                    XmpWriter.readOrNull(row.path)?.also { current ->
                        if (cached?.rating != current.rating) {
                            try { applicationContext.mediaDB.updateRating(row.path, current.rating) } catch (_: Exception) { }
                        }
                    }
                } ?: continue
                val hasData = xmp.tags.isNotEmpty() || xmp.rating > 0
                onDataFound(hasData)
                if (!fullScan && folderPath == null && !hasData) continue

                batch.add(MediaCache(fullPath = row.path, tags = xmp.tags.joinToString(","), rating = xmp.rating, lastScanned = now))
                // Empty is meaningful: it clears tags removed externally instead of preserving
                // stale rows in the normalized tag index.
                syncMediaTags(row.path)
                stats.processed++
                if (batch.size >= CACHE_WRITE_BATCH_SIZE) {
                    applicationContext.mediaCacheDB.upsertAllIfNewer(batch.toList())
                    batch.clear()
                }
            } catch (_: Exception) { }
        }
    }

    private fun recordMemory(stats: FullScanStats) {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        if (used > stats.peakHeapBytes) stats.peakHeapBytes = used
        val info = android.os.Debug.MemoryInfo()
        android.os.Debug.getMemoryInfo(info)
        if (info.totalPss > stats.peakPssKb) stats.peakPssKb = info.totalPss
        if (info.dalvikPss > stats.peakDalvikPssKb) stats.peakDalvikPssKb = info.dalvikPss
        if (info.nativePss > stats.peakNativePssKb) stats.peakNativePssKb = info.nativePss
    }

    private fun heapMb(bytes: Long): Long = bytes / (1024L * 1024L)

    /** Mirrors a scanned file's XMP tags into the normalized `media_tags` table (replace-all for
     * that path), same as [org.fossify.gallery.helpers.MediaRepository]'s single-file sync. */
    private suspend fun syncMediaTags(path: String) {
        try {
            MetadataWriteCoordinator.withLockSuspend(path) {
                // Re-read under the same lock used by UI edits. The scan may have read an older
                // snapshot before the user tapped a tag; indexing that old snapshot would undo the
                // user's normalized media_tags row after the UI write completed.
                val latest = XmpWriter.readOrNull(path) ?: return@withLockSuspend
                applicationContext.mediaTagDB.replaceTagsForPath(path, latest.tags)
            }
        } catch (_: Exception) { }
    }

    private fun showNotification(title: String, text: String) {
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "metadata_sync"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(channelId, applicationContext.getString(org.fossify.gallery.R.string.notif_channel_metadata_sync), NotificationManager.IMPORTANCE_LOW))
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
        activityDone = done
        activityTotal = total
        activityMatches = tags
        LibraryActivityStatus.progress(activityKey, done, total, tags)
        try {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "metadata_sync"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                nm.createNotificationChannel(NotificationChannel(channelId, applicationContext.getString(org.fossify.gallery.R.string.notif_channel_metadata_sync), NotificationManager.IMPORTANCE_LOW))
            }
            nm.notify(2002, NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(applicationContext.getString(org.fossify.gallery.R.string.notif_metadata_scanning_title))
                .setContentText(applicationContext.getString(org.fossify.gallery.R.string.notif_metadata_scanning_progress, done, total, tags, ratings))
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
        private const val RETRY_SKIP_WINDOW_MS = 10 * 60 * 1000L
        private const val FULL_SCAN_CHUNK_SIZE = 500
        private const val CACHE_WRITE_BATCH_SIZE = 200

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
            // Unique name is per-folder (not one name shared by every call) - callers can request a
            // scan for several different folders in a row (e.g. a multi-select "scan metadata"
            // batch action), and a single shared name under ExistingWorkPolicy.REPLACE would have
            // each new folder's request cancel the previous folder's not-yet-started scan, so only
            // the last folder in the batch ever actually ran. Repeat-tapping the same folder still
            // coalesces via REPLACE, which is the behavior this was originally written for.
            WorkManager.getInstance(context).enqueueUniqueWork("${WORK_NAME}_folder_${folderPath.hashCode()}", ExistingWorkPolicy.REPLACE, workRequest)
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
            wm.cancelUniqueWork("${WORK_NAME}_advanced")
            // Per-folder scans no longer share one unique work name (see scheduleFolderScan), so
            // cancelUniqueWork can't reach them anymore - cancel by the tag every folder-scan
            // request still carries instead.
            wm.cancelAllWorkByTag("${WORK_NAME}_folder")
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
