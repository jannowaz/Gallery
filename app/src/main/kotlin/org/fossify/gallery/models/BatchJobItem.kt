package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One file to process within a [org.fossify.gallery.workers.MediaBatchWorker] job. WorkManager's
 * Data has a ~10KB limit, too small for a large batch of paths, so the UI persists the batch here
 * and only passes the jobId through Data. Rows are deleted as each item completes successfully;
 * leftover rows (a cancelled/failed item, or an interrupted job never retried) are swept by
 * [org.fossify.gallery.interfaces.BatchJobItemDao.deleteStale]. */
@Entity(tableName = "batch_job_items", indices = [Index(value = ["job_id"])])
data class BatchJobItem(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long? = null,
    @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "source_path") val sourcePath: String,
    /** New absolute path (rename) or destination absolute path (move/copy). */
    @ColumnInfo(name = "target_path") val targetPath: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
