package org.fossify.gallery.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_cache")
data class MediaCache(
    @PrimaryKey @ColumnInfo(name = "full_path") var fullPath: String,
    @ColumnInfo(name = "tags") var tags: String = "",
    @ColumnInfo(name = "rating") var rating: Int = 0,
    @ColumnInfo(name = "last_scanned") var lastScanned: Long = 0L,
)
