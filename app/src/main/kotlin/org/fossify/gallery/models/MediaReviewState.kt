package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The explicit review state of one media item.
 *
 * There is deliberately no persisted UNDECIDED row. Missing rows mean that the user has not made
 * a review decision yet. This keeps the table small and makes resetting a decision a real delete.
 */
@Entity(tableName = "media_review_states")
data class MediaReviewState(
    @PrimaryKey
    @ColumnInfo(name = "media_path", collate = ColumnInfo.NOCASE)
    val mediaPath: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

enum class MediaReviewStatus(val databaseValue: String) {
    SKIPPED("SKIPPED"),
    DECIDED("DECIDED"),
}

/** UI-only filter. A missing review row is the undecided state. */
enum class MediaReviewFilter {
    NONE,
    UNDECIDED,
}

/** Pure counterpart of the SQL review-inbox predicate, useful for state previews and tests. */
fun MediaReviewStatus?.matches(filter: MediaReviewFilter): Boolean = when (filter) {
    MediaReviewFilter.NONE -> true
    // Missing rows are the only undecided state. Both explicit outcomes leave the inbox.
    MediaReviewFilter.UNDECIDED -> this == null
}
