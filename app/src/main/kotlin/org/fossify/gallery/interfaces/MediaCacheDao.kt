package org.fossify.gallery.interfaces

import androidx.room.*
import org.fossify.gallery.models.MediaCache

@Dao
interface MediaCacheDao {
    @Query("SELECT * FROM media_cache WHERE tags != ''")
    suspend fun getAllTagged(): List<MediaCache>

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

    /** Bounded lookup used by large metadata scans; avoids materializing the complete cache. */
    @Query("SELECT * FROM media_cache WHERE full_path IN (:paths)")
    suspend fun getByPaths(paths: List<String>): List<MediaCache>

    @Query("DELETE FROM media_cache WHERE full_path = :path")
    fun deleteByPathSync(path: String)

    /** Batch variant for mass deletions - one transaction instead of one per path. */
    @Query("DELETE FROM media_cache WHERE full_path IN (:paths)")
    fun deleteByPathsBatch(paths: List<String>)

    @Upsert
    suspend fun upsertAll(cache: List<MediaCache>)

    /**
     * Writes a cache row only when it is at least as new as the row already stored for that path.
     * A metadata scan can finish after a user edit; comparing the scan timestamp prevents that
     * older snapshot from undoing the edit in the rating/CSV compatibility cache.
     */
    @Query(
        """
        INSERT INTO media_cache(full_path, tags, rating, last_scanned)
        VALUES (:fullPath, :tags, :rating, :lastScanned)
        ON CONFLICT(full_path) DO UPDATE SET
            tags = excluded.tags,
            rating = excluded.rating,
            last_scanned = excluded.last_scanned
        WHERE media_cache.last_scanned <= excluded.last_scanned
        """
    )
    suspend fun upsertIfNewer(fullPath: String, tags: String, rating: Int, lastScanned: Long)

    @Transaction
    suspend fun upsertAllIfNewer(cache: List<MediaCache>) {
        cache.forEach { entry ->
            upsertIfNewer(entry.fullPath, entry.tags, entry.rating, entry.lastScanned)
        }
    }
}
