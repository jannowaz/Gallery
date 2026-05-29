package org.fossify.gallery.interfaces

import androidx.room.*
import org.fossify.gallery.models.MediaCache

@Dao
interface MediaCacheDao {
    @Query("SELECT * FROM media_cache WHERE tags != ''")
    suspend fun getAllTagged(): List<MediaCache>

    @Query("SELECT * FROM media_cache WHERE rating >= :minRating")
    suspend fun getByRating(minRating: Int): List<MediaCache>

    @Query("SELECT * FROM media_cache")
    suspend fun getAll(): List<MediaCache>

    @Upsert
    suspend fun upsertAll(cache: List<MediaCache>)
}
