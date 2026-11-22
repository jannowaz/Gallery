package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Persisted local result of the conservative on-device person-presence scan. */
@Entity(
    tableName = "media_person_scans",
    indices = [
        Index(value = ["folder_path", "result"]),
        Index(value = ["content_signature"]),
    ],
)
data class MediaPersonScan(
    @PrimaryKey
    @ColumnInfo(name = "media_path", collate = ColumnInfo.NOCASE)
    val mediaPath: String,
    @ColumnInfo(name = "folder_path", collate = ColumnInfo.NOCASE)
    val folderPath: String,
    /** Path + modified time + byte count; a changed file is rescanned automatically. */
    @ColumnInfo(name = "content_signature")
    val contentSignature: String,
    @ColumnInfo(name = "result")
    val result: String,
    /** Percentage of the analyzed frame that the human mask covered, 0 for a face-only hit. */
    @ColumnInfo(name = "person_coverage")
    val personCoverage: Float,
    /** A deliberate user decision: keep this medium out of future cleanup suggestions. */
    @ColumnInfo(name = "is_kept")
    val isKept: Boolean = false,
    @ColumnInfo(name = "scanned_at")
    val scannedAt: Long = System.currentTimeMillis(),
)

enum class PersonPresence(val databaseValue: String) {
    PERSON("PERSON"),
    NO_PERSON("NO_PERSON"),
    UNCERTAIN("UNCERTAIN"),
}

data class PersonScanCount(
    @ColumnInfo(name = "result") val result: String,
    @ColumnInfo(name = "count") val count: Int,
)
