package org.fossify.gallery.interfaces

import androidx.paging.PagingSource
import androidx.room.*
import org.fossify.gallery.models.Medium

@Dao
interface MediumDao {
    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 AND parent_path = :path COLLATE NOCASE")
    fun getMediaFromPath(path: String): List<Medium>

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 AND full_path IN (:paths)")
    fun getMediaByPaths(paths: List<String>): List<Medium>

    @Query("SELECT rating FROM media WHERE deleted_ts = 0 AND full_path = :path COLLATE NOCASE LIMIT 1")
    fun getRatingForPath(path: String): Int?

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 AND is_favorite = 1")
    fun getFavorites(): List<Medium>

    @Query("SELECT COUNT(filename) FROM media WHERE deleted_ts = 0 AND is_favorite = 1")
    fun getFavoritesCount(): Long

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts != 0")
    fun getDeletedMedia(): List<Medium>

    @Query("SELECT COUNT(filename) FROM media WHERE deleted_ts != 0")
    fun getDeletedMediaCount(): Long

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts < :timestmap AND deleted_ts != 0")
    fun getOldRecycleBinItems(timestmap: Long): List<Medium>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(medium: Medium)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(media: List<Medium>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAllKeepingExisting(media: List<Medium>)

    @Query("SELECT full_path FROM media")
    fun getAllPaths(): List<String>

    // Used to dedup a bounded candidate batch (e.g. a MediaStore incremental-sync page) against the
    // DB without materializing every path in the library - see MediaRepository.syncNewMediaFromStore().
    // full_path's column-level COLLATE NOCASE (see Medium.kt) applies here automatically.
    @Query("SELECT full_path FROM media WHERE full_path IN (:paths)")
    fun getExistingPaths(paths: List<String>): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM media LIMIT 1)")
    fun hasAnyMedia(): Boolean

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

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 ORDER BY date_taken DESC, last_modified DESC LIMIT :limit")
    fun getNewestMedia(limit: Int): List<Medium>

    /** Includes recycle-bin rows - the "is this database empty?" bootstrap check must count them,
     * see ExplorerViewModel.initializeDatabase. */
    @Query("SELECT COUNT(filename) FROM media")
    fun getTotalCountIncludingDeleted(): Int

    @Query("UPDATE media SET rating = :rating WHERE full_path = :path COLLATE NOCASE")
    fun updateRating(path: String, rating: Int)

    @Query("UPDATE media SET rating = :rating WHERE full_path IN (:paths)")
    fun updateRatingBatch(paths: Collection<String>, rating: Int)

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 AND rating >= :minRating ORDER BY date_taken DESC, last_modified DESC")
    fun getByMinRating(minRating: Int): List<Medium>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0")
    suspend fun getActivePaths(): List<String>

    // date_sort_key is a denormalized, auto-maintained (via SQLite triggers, see GalleryDatabase)
    // mirror of `date_taken > 0 ? date_taken : last_modified` - the actual "effective date" this app
    // sorts/groups by (matches MediaViewModel.groupByMonth's grouping key). It exists purely so this
    // sort can be indexed at all: SQLite can only use an index to skip a sort step when the ORDER BY
    // key is a plain column the index covers, never a computed CASE expression - and an index built
    // directly over that expression is not an option, since Room's own schema validation can't
    // represent expression indices (confirmed live: crashes with "Migration didn't properly handle"
    // every time, because its PRAGMA-based introspection silently drops the expression term instead
    // of matching it). Before this column existed, every open of the Viewer from the Media tab
    // (default sort) re-scanned and sorted the *entire* media table from scratch - confirmed to be
    // the real cause of a reported real-device bug where opening a photo took several seconds, every
    // time, regardless of library section (a naive in-memory cache alone wasn't enough either: any
    // rating/tag/favorite edit touches the file, which MediaStore reports as a change, which
    // invalidates that cache almost immediately in normal browse-and-rate use).
    //
    // Every sort below (not just date) is split into two literal-direction (ASC/DESC) @Query methods
    // instead of one query multiplying the sort key by a runtime `(CASE WHEN :desc THEN -1 ELSE 1
    // END)` sign flip (what this used to be) - SQLite can only use an index to satisfy an ORDER BY
    // when the direction is known at query-plan time; a bound parameter deciding the sign defeats
    // that regardless of what indices exist on the sorted columns. The `desc: Boolean` public method
    // signature is kept as a plain (non-@Query) default method dispatching to the two literal ones,
    // so no call site needs to change.
    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY date_sort_key ASC
        """
    )
    fun getMediaPagedByDateAsc(): PagingSource<Int, Medium>

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY date_sort_key DESC
        """
    )
    fun getMediaPagedByDateDesc(): PagingSource<Int, Medium>

    fun getMediaPagedByDate(desc: Boolean): PagingSource<Int, Medium> = if (desc) getMediaPagedByDateDesc() else getMediaPagedByDateAsc()

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY size ASC
        """
    )
    fun getMediaPagedBySizeAsc(): PagingSource<Int, Medium>

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY size DESC
        """
    )
    fun getMediaPagedBySizeDesc(): PagingSource<Int, Medium>

    fun getMediaPagedBySize(desc: Boolean): PagingSource<Int, Medium> = if (desc) getMediaPagedBySizeDesc() else getMediaPagedBySizeAsc()

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY rating ASC, last_modified ASC
        """
    )
    fun getMediaPagedByRatingAsc(): PagingSource<Int, Medium>

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY rating DESC, last_modified DESC
        """
    )
    fun getMediaPagedByRatingDesc(): PagingSource<Int, Medium>

    fun getMediaPagedByRating(desc: Boolean): PagingSource<Int, Medium> = if (desc) getMediaPagedByRatingDesc() else getMediaPagedByRatingAsc()

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE ASC")
    fun getMediaPagedByNameAsc(): PagingSource<Int, Medium>

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE DESC")
    fun getMediaPagedByNameDesc(): PagingSource<Int, Medium>

    // Paths-only mirrors of the getMediaPagedBy* queries above (same ordering, no LIMIT/OFFSET) -
    // used to hand the Viewer the full sorted path list on demand so swipe-through isn't limited to
    // whatever the grid has paged in so far. Same ASC/DESC split rationale as above.
    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY date_sort_key ASC")
    suspend fun getActivePathsByDateAsc(): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY date_sort_key DESC")
    suspend fun getActivePathsByDateDesc(): List<String>

    suspend fun getActivePathsByDate(desc: Boolean): List<String> = if (desc) getActivePathsByDateDesc() else getActivePathsByDateAsc()

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY size ASC")
    suspend fun getActivePathsBySizeAsc(): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY size DESC")
    suspend fun getActivePathsBySizeDesc(): List<String>

    suspend fun getActivePathsBySize(desc: Boolean): List<String> = if (desc) getActivePathsBySizeDesc() else getActivePathsBySizeAsc()

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY rating ASC, last_modified ASC")
    suspend fun getActivePathsByRatingAsc(): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY rating DESC, last_modified DESC")
    suspend fun getActivePathsByRatingDesc(): List<String>

    suspend fun getActivePathsByRating(desc: Boolean): List<String> = if (desc) getActivePathsByRatingDesc() else getActivePathsByRatingAsc()

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE ASC")
    suspend fun getActivePathsByNameAsc(): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE DESC")
    suspend fun getActivePathsByNameDesc(): List<String>
}
