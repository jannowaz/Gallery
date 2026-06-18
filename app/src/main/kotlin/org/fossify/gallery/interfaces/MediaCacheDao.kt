package org.fossify.gallery.interfaces

import androidx.room.*
import org.fossify.gallery.models.MediaCache

@Dao
interface MediaCacheDao {
    @Query("SELECT * FROM media_cache WHERE tags != ''")
    suspend fun getAllTagged(): List<MediaCache>

    @Query("SELECT * FROM media_cache WHERE tags != '' ORDER BY last_scanned DESC LIMIT :limit")
    suspend fun getRecentTagged(limit: Int): List<MediaCache>

    @Query("SELECT * FROM media_cache WHERE tags != '' LIMIT :limit OFFSET :offset")
    suspend fun getTaggedPaged(limit: Int, offset: Int): List<MediaCache>

    @Query("SELECT COUNT(*) FROM media_cache WHERE tags != ''")
    suspend fun getTaggedCount(): Int

    @Query("SELECT * FROM media_cache WHERE rating >= :minRating")
    suspend fun getByRating(minRating: Int): List<MediaCache>

    @Query("SELECT * FROM media_cache")
    suspend fun getAll(): List<MediaCache>

    @Query("SELECT * FROM media_cache WHERE full_path = :path LIMIT 1")
    suspend fun getByPath(path: String): MediaCache?

    @Upsert
    suspend fun upsertAll(cache: List<MediaCache>)
}
