package org.fossify.gallery.workers

import android.content.Context
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.mediaDB
import java.util.concurrent.TimeUnit

class MediaSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val mediums = mutableListOf<org.fossify.gallery.models.Medium>()
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATE_TAKEN,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DURATION,
            )
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            )
            applicationContext.contentResolver.query(uri, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_TAKEN)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val name = java.io.File(path).name
                    val parentPath = java.io.File(path).parent ?: ""
                    val modified = cursor.getLong(dateCol) * 1000L
                    val taken = if (!cursor.isNull(takenCol)) cursor.getLong(takenCol) else modified
                    val size = cursor.getLong(sizeCol)
                    val mediaType = cursor.getInt(typeCol)
                    val type = if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) 2 else 1
                    val duration = if (!cursor.isNull(durCol)) (cursor.getInt(durCol) / 1000) else 0
                    mediums.add(org.fossify.gallery.models.Medium(
                        id = null, name = name, path = path, parentPath = parentPath,
                        modified = modified, taken = taken, size = size, type = type,
                        videoDuration = duration, isFavorite = false, deletedTS = 0L, mediaStoreId = 0, rating = 0,
                    ))
                }
            }

            if (mediums.isNotEmpty()) {
                applicationContext.mediaDB.insertAllKeepingExisting(mediums)
                val dirs = mediums.map { it.parentPath }.distinct()
                dirs.forEach { dirPath ->
                    val dirMedia = mediums.filter { it.parentPath == dirPath }
                    val dirName = java.io.File(dirPath).name
                    val hasImage = dirMedia.any { it.type == 1 }
                    val hasVideo = dirMedia.any { it.type == 2 }
                    val types = if (hasImage && hasVideo) 3 else if (hasVideo) 2 else 1
                    applicationContext.directoryDB.insertAll(listOf(org.fossify.gallery.models.Directory(
                        id = null, path = dirPath, tmb = dirMedia.maxByOrNull { it.modified }?.path ?: "",
                        name = dirName, mediaCnt = dirMedia.size,
                        modified = dirMedia.maxOf { it.modified },
                        taken = dirMedia.maxOf { it.taken },
                        size = dirMedia.size.toLong(),
                        location = org.fossify.gallery.helpers.LOCATION_INTERNAL, types = types, sortValue = "",
                    )))
                }
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("MediaSyncWorker", "Sync failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "media_sync"
        private const val INITIAL_WORK_NAME = "media_sync_initial"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<MediaSyncWorker>(168, TimeUnit.HOURS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest,
            )
        }

        fun scheduleInitialSync(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<MediaSyncWorker>()
                .addTag(INITIAL_WORK_NAME)
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                INITIAL_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                workRequest,
            )
        }

        fun scheduleIncrementalSync(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<MediaSyncWorker>()
                .addTag("media_sync_incremental")
                .setInitialDelay(2, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("media_sync_incremental", ExistingWorkPolicy.REPLACE, workRequest)
        }
    }
}
