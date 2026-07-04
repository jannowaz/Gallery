package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * One (file, tag) pair. Replaces the old comma-separated `media_cache.tags` column so tag lookups
 * are exact-match SQL instead of substring checks on a CSV blob (which false-matched e.g. a tag
 * "Auto" against a file tagged "Autobahn") and so counting/browsing tags doesn't require loading
 * every tagged file's row into memory to split and recount client-side.
 */
@Entity(tableName = "media_tags", primaryKeys = ["media_path", "tag"], indices = [Index(value = ["tag"])])
data class MediaTag(
    @ColumnInfo(name = "media_path") val mediaPath: String,
    @ColumnInfo(name = "tag") val tag: String,
)

data class TagCount(val tag: String, val cnt: Int)

data class TagPathRow(val tag: String, @ColumnInfo(name = "media_path") val path: String)
