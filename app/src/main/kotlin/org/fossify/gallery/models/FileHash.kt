package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent per-file hash cache for the duplicate finder. An entry is valid while [size] and
 * [modified] still match the file on disk - any edit changes both/either, invalidating it
 * naturally. Battery rationale: hashing is the expensive part of every duplicate scan; caching it
 * means repeat scans (and the recent-vs-library mode) only ever hash files never seen before.
 */
@Entity(tableName = "file_hashes")
data class FileHash(
    @PrimaryKey @ColumnInfo(name = "full_path") val path: String,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "last_modified") val modified: Long,
    @ColumnInfo(name = "partial_hash") val partialHash: String? = null,
    @ColumnInfo(name = "full_hash") val fullHash: String? = null,
    @ColumnInfo(name = "phash") val phash: Long? = null,
)
