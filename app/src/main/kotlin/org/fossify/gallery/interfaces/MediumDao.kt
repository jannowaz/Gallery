package org.fossify.gallery.interfaces

import androidx.paging.PagingSource
import androidx.room.*
import org.fossify.gallery.models.Medium

@Dao
interface MediumDao {
    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts = 0 AND parent_path = :path COLLATE NOCASE")
    fun getMediaFromPath(path: String): List<Medium>

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts = 0 AND full_path IN (:paths)")
    fun getMediaByPaths(paths: List<String>): List<Medium>

    @Query("SELECT rating FROM media WHERE deleted_ts = 0 AND full_path = :path COLLATE NOCASE LIMIT 1")
    fun getRatingForPath(path: String): Int?

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts = 0 AND is_favorite = 1")
    fun getFavorites(): List<Medium>

    @Query("SELECT COUNT(filename) FROM media WHERE deleted_ts = 0 AND is_favorite = 1")
    fun getFavoritesCount(): Long

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts != 0")
    fun getDeletedMedia(): List<Medium>

    @Query("SELECT COUNT(filename) FROM media WHERE deleted_ts != 0")
    fun getDeletedMediaCount(): Long

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts < :timestmap AND deleted_ts != 0")
    fun getOldRecycleBinItems(timestmap: Long): List<Medium>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(medium: Medium)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(media: List<Medium>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAllKeepingExisting(media: List<Medium>)

    @Query("SELECT full_path FROM media")
    fun getAllPaths(): List<String>

    @Query("DELETE FROM media WHERE full_path IN (:paths)")
    fun deleteByPaths(paths: List<String>)

    @Delete
    fun deleteMedia(vararg medium: Medium)

    @Query("DELETE FROM media WHERE full_path = :path COLLATE NOCASE")
    fun deleteMediumPath(path: String)

    @Query("UPDATE OR REPLACE media SET filename = :newFilename, full_path = :newFullPath, parent_path = :newParentPath WHERE full_path = :oldPath COLLATE NOCASE")
    fun updateMedium(oldPath: String, newParentPath: String, newFilename: String, newFullPath: String)

    @Query("UPDATE OR REPLACE media SET full_path = :newPath, deleted_ts = :deletedTS WHERE full_path = :oldPath COLLATE NOCASE")
    fun updateDeleted(newPath: String, deletedTS: Long, oldPath: String)

    @Query("UPDATE media SET deleted_ts = :deletedTS WHERE full_path = :path COLLATE NOCASE")
    fun softDelete(path: String, deletedTS: Long)

    @Query("UPDATE media SET deleted_ts = :deletedTS WHERE full_path IN (:paths)")
    fun softDeleteBatch(paths: Collection<String>, deletedTS: Long)

    @Query("UPDATE media SET deleted_ts = 0 WHERE full_path = :path COLLATE NOCASE")
    fun restoreDeleted(path: String)

    @Query("UPDATE media SET deleted_ts = 0 WHERE full_path IN (:paths)")
    fun restoreDeletedBatch(paths: Collection<String>)

    @Query("UPDATE media SET date_taken = :dateTaken WHERE full_path = :path COLLATE NOCASE")
    fun updateFavoriteDateTaken(path: String, dateTaken: Long)

    @Query("UPDATE media SET is_favorite = :isFavorite WHERE full_path = :path COLLATE NOCASE")
    fun updateFavorite(path: String, isFavorite: Boolean)

    @Query("UPDATE media SET is_favorite = 0")
    fun clearFavorites()

    @Query("DELETE FROM media WHERE deleted_ts != 0")
    fun clearRecycleBin()

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts = 0 ORDER BY date_taken DESC, last_modified DESC LIMIT :limit")
    fun getNewestMedia(limit: Int): List<Medium>

    @Query("UPDATE media SET rating = :rating WHERE full_path = :path COLLATE NOCASE")
    fun updateRating(path: String, rating: Int)

    @Query("UPDATE media SET rating = :rating WHERE full_path IN (:paths)")
    fun updateRatingBatch(paths: Collection<String>, rating: Int)

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts = 0 AND rating >= :minRating ORDER BY date_taken DESC, last_modified DESC")
    fun getByMinRating(minRating: Int): List<Medium>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0")
    suspend fun getActivePaths(): List<String>

    // The `date_taken > 0` fallback to `last_modified` mirrors MediaViewModel.groupByMonth's grouping
    // key, so month headers computed from this order line up with the actual sort order instead of
    // silently disagreeing with it.
    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating
        FROM media WHERE deleted_ts = 0
        ORDER BY (CASE WHEN :desc THEN -1 ELSE 1 END) * (CASE WHEN date_taken > 0 THEN date_taken ELSE last_modified END)
        """
    )
    fun getMediaPagedByDate(desc: Boolean): PagingSource<Int, Medium>

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating
        FROM media WHERE deleted_ts = 0
        ORDER BY (CASE WHEN :desc THEN -1 ELSE 1 END) * size
        """
    )
    fun getMediaPagedBySize(desc: Boolean): PagingSource<Int, Medium>

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating
        FROM media WHERE deleted_ts = 0
        ORDER BY (CASE WHEN :desc THEN -1 ELSE 1 END) * rating, (CASE WHEN :desc THEN -1 ELSE 1 END) * last_modified
        """
    )
    fun getMediaPagedByRating(desc: Boolean): PagingSource<Int, Medium>

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE ASC")
    fun getMediaPagedByNameAsc(): PagingSource<Int, Medium>

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE DESC")
    fun getMediaPagedByNameDesc(): PagingSource<Int, Medium>

    // Paths-only mirrors of the getMediaPagedBy* queries above (same ordering, no LIMIT/OFFSET) -
    // used to hand the Viewer the full sorted path list on demand so swipe-through isn't limited to
    // whatever the grid has paged in so far.
    @Query(
        """
        SELECT full_path FROM media WHERE deleted_ts = 0
        ORDER BY (CASE WHEN :desc THEN -1 ELSE 1 END) * (CASE WHEN date_taken > 0 THEN date_taken ELSE last_modified END)
        """
    )
    suspend fun getActivePathsByDate(desc: Boolean): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY (CASE WHEN :desc THEN -1 ELSE 1 END) * size")
    suspend fun getActivePathsBySize(desc: Boolean): List<String>

    @Query(
        """
        SELECT full_path FROM media WHERE deleted_ts = 0
        ORDER BY (CASE WHEN :desc THEN -1 ELSE 1 END) * rating, (CASE WHEN :desc THEN -1 ELSE 1 END) * last_modified
        """
    )
    suspend fun getActivePathsByRating(desc: Boolean): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE ASC")
    suspend fun getActivePathsByNameAsc(): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE DESC")
    suspend fun getActivePathsByNameDesc(): List<String>
}
