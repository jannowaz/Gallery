package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One file being probe-compressed by [org.fossify.gallery.workers.CompressionWorker] for later
 * user review. Unlike [BatchJobItem], this never touches the original or MediaStore on its own -
 * the row just tracks a temp result file until the user explicitly picks original or new version
 * in the review screen. Persisted (not in-memory) because a video transcode can take a while and
 * the review queue must survive the user leaving and coming back to the app. */
@Entity(tableName = "compression_review_items", indices = [Index(value = ["job_id"])])
data class CompressionReviewItem(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long? = null,
    @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "original_path") val originalPath: String,
    @ColumnInfo(name = "temp_result_path") val tempResultPath: String = "",
    @ColumnInfo(name = "original_size") val originalSize: Long = 0,
    @ColumnInfo(name = "result_size") val resultSize: Long = 0,
    /** 1 = image, 2 = video - mirrors [Medium.type]. */
    @ColumnInfo(name = "media_type") val mediaType: Int,
    @ColumnInfo(name = "status") val status: String = STATUS_PENDING,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}
