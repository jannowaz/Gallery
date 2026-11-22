package org.fossify.gallery.interfaces

import androidx.paging.PagingSource
import androidx.room.*
import org.fossify.gallery.models.Medium

/** Per-folder aggregate over the live (non-deleted) media rows - the source of truth the
 * `directories` cache is rebuilt from, see MediaRepository.syncDirectoriesFromMedia. */
data class DirectoryAggregate(
    @ColumnInfo(name = "parent_path") val parentPath: String,
    @ColumnInfo(name = "cnt") val count: Int,
    @ColumnInfo(name = "max_modified") val maxModified: Long,
    @ColumnInfo(name = "max_taken") val maxTaken: Long,
    @ColumnInfo(name = "total_size") val totalSize: Long,
    @ColumnInfo(name = "has_images") val hasImages: Int,
    @ColumnInfo(name = "has_videos") val hasVideos: Int,
    @ColumnInfo(name = "image_count") val imageCount: Int,
    @ColumnInfo(name = "video_count") val videoCount: Int,
    @ColumnInfo(name = "thumbnail") val thumbnail: String?,
)

/** Path+size projection for the duplicate finder's DB-first candidate selection. */
data class PathSize(
    @ColumnInfo(name = "full_path") val path: String,
    @ColumnInfo(name = "size") val size: Long,
)

/** The small subset needed to repair rows whose MediaStore metadata was observed as zero. */
data class MetadataRepairRow(
    @ColumnInfo(name = "full_path") val path: String,
    @ColumnInfo(name = "last_modified") val modified: Long,
    @ColumnInfo(name = "date_taken") val taken: Long,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "date_added") val added: Long,
    @ColumnInfo(name = "type") val type: Int,
)

/** Small, rowid-paged projection used by metadata scans instead of materialising full Medium rows. */
data class MetadataScanRow(
    @ColumnInfo(name = "row_id") val rowId: Long,
    @ColumnInfo(name = "full_path") val path: String,
    @ColumnInfo(name = "last_modified") val modified: Long,
    @ColumnInfo(name = "date_taken") val taken: Long,
    @ColumnInfo(name = "rating") val rating: Int,
)

/** Bounded, ID-paged input for the local person-presence worker. */
data class PersonScanRow(
    @ColumnInfo(name = "row_id") val rowId: Long,
    @ColumnInfo(name = "full_path") val path: String,
    @ColumnInfo(name = "parent_path") val parentPath: String,
    @ColumnInfo(name = "last_modified") val modified: Long,
    @ColumnInfo(name = "size") val size: Long,
)

@Dao
interface MediumDao {

    @Query("SELECT id AS row_id, full_path, parent_path, last_modified, size FROM media WHERE deleted_ts = 0 AND parent_path = :folderPath COLLATE NOCASE AND type = 1 AND id > :afterRowId ORDER BY id LIMIT :limit")
    fun getPersonScanRowsForFolderAfter(folderPath: String, afterRowId: Long, limit: Int): List<PersonScanRow>

    @Query("SELECT COUNT(*) FROM media WHERE deleted_ts = 0 AND parent_path = :folderPath COLLATE NOCASE AND type = 1")
    fun getPersonScanRowCountForFolder(folderPath: String): Int

    @Query("SELECT id AS row_id, full_path, parent_path, last_modified, size FROM media WHERE deleted_ts = 0 AND (parent_path = :folderPath COLLATE NOCASE OR parent_path LIKE :subfolderPattern ESCAPE '\\') AND type = 1 AND id > :afterRowId ORDER BY id LIMIT :limit")
    fun getPersonScanRowsForFolderTreeAfter(folderPath: String, subfolderPattern: String, afterRowId: Long, limit: Int): List<PersonScanRow>

    @Query("SELECT COUNT(*) FROM media WHERE deleted_ts = 0 AND (parent_path = :folderPath COLLATE NOCASE OR parent_path LIKE :subfolderPattern ESCAPE '\\') AND type = 1")
    fun getPersonScanRowCountForFolderTree(folderPath: String, subfolderPattern: String): Int

    /** Media added or modified since [since] - the probes of the recent-vs-library duplicate scan. */
    @Query("SELECT full_path, size FROM media WHERE deleted_ts = 0 AND (date_added >= :since OR last_modified >= :since)")
    fun getRecentLivePathSizes(since: Long): List<PathSize>

    /** All live media sharing one of the given sizes - exact duplicates must match in size, so
     * this is the complete candidate set without touching the disk. */
    @Query("SELECT full_path, size FROM media WHERE deleted_ts = 0 AND size IN (:sizes)")
    fun getLivePathSizesBySizes(sizes: List<Long>): List<PathSize>

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 AND parent_path = :path COLLATE NOCASE")
    fun getMediaFromPath(path: String): List<Medium>

    /** Just the paths, capped - for thumbnail/preview strips that only ever show a handful per
     * folder. Same predicate (and same (deleted_ts, parent_path) index) as [getMediaFromPath], but
     * one column instead of fourteen and LIMIT pushed into SQLite instead of materialising every
     * row of the folder into a Medium and then dropping all but the first few. On a library with
     * many folders that difference is the whole query cost - see AlbumsScreen's preview strip. */
    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 AND parent_path = :path COLLATE NOCASE LIMIT :limit")
    fun getPreviewPathsFromPath(path: String, limit: Int): List<String>

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 AND full_path IN (:paths)")
    fun getMediaByPaths(paths: List<String>): List<Medium>

    @Query("SELECT rating FROM media WHERE deleted_ts = 0 AND full_path = :path COLLATE NOCASE LIMIT 1")
    fun getRatingForPath(path: String): Int?

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 AND is_favorite = 1")
    fun getFavorites(): List<Medium>

    @Query("SELECT COUNT(filename) FROM media WHERE deleted_ts = 0 AND is_favorite = 1")
    fun getFavoritesCount(): Long

    /** Bounded projection for the permanent, automatically maintained rating collections. */
    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 AND rating = :rating ORDER BY date_sort_key DESC, full_path COLLATE NOCASE ASC")
    fun getPathsWithExactRating(rating: Int): List<String>

    // Recycle bin is a chronological inbox: the most recently deleted items must be first. Keeping
    // this ordering in SQLite (rather than only in the composable) also makes every caller agree.
    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts != 0 ORDER BY deleted_ts DESC, id DESC")
    fun getDeletedMedia(): List<Medium>

    /** Paths-only projection for sync/filter lookups that never need full Medium rows. */
    @Query("SELECT full_path FROM media WHERE deleted_ts != 0")
    fun getDeletedPaths(): List<String>

    /** Live (non-recycle-bin) row count, for the library health check in Settings. */
    @Query("SELECT COUNT(filename) FROM media WHERE deleted_ts = 0")
    fun getLiveMediaCount(): Int

    /** Count media whose stable date-sort key is newer than the user's inbox watermark. */
    @Query("SELECT COUNT(filename) FROM media WHERE deleted_ts = 0 AND date_sort_key > :since")
    fun getNewMediaCountSince(since: Long): Int

    /** All live paths + the fields the Explorer's folder-tree reconstruction needs, straight from
     * the indexed media table instead of a full-device MediaStore cursor. See MediaStoreOps.MediaEntry. */
    @Query("SELECT full_path AS path, filename AS name, last_modified AS modified, size, date_added AS dateAdded, type FROM media WHERE deleted_ts = 0")
    fun getAllLiveEntriesForExplorer(): List<org.fossify.gallery.helpers.MediaStoreOps.MediaEntry>

    /** Targeted Explorer cache patch after a sync/move/delete. Keeps the common one/few-file path
     * change off the full-library SELECT used only for an unknown/bulk invalidation. */
    @Query("SELECT full_path AS path, filename AS name, last_modified AS modified, size, date_added AS dateAdded, type FROM media WHERE deleted_ts = 0 AND full_path IN (:paths)")
    fun getLiveEntriesForExplorerPaths(paths: List<String>): List<org.fossify.gallery.helpers.MediaStoreOps.MediaEntry>

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

    /** Rows with incomplete provider metadata. Kept deliberately narrow so the repair pass never
     * materializes the complete library just to find a handful of zero-valued rows. */
    @Query("SELECT full_path, last_modified, date_taken, size, date_added, type FROM media WHERE deleted_ts = 0 AND (last_modified <= 0 OR date_taken <= 0 OR date_added <= 0 OR (size <= 0 AND type = 1))")
    fun getMetadataRepairRows(): List<MetadataRepairRow>

    /** Updates only fields that are safe to derive from the existing on-disk file. Tags, ratings,
     * favorites and the stable MediaStore id remain untouched. */
    @Query("UPDATE media SET last_modified = :modified, date_taken = :taken, size = :size, date_added = :added WHERE full_path = :path COLLATE NOCASE AND deleted_ts = 0")
    fun repairMetadata(path: String, modified: Long, taken: Long, size: Long, added: Long)

    // Used to dedup a bounded candidate batch (e.g. a MediaStore incremental-sync page) against the
    // DB without materializing every path in the library - see MediaRepository.syncNewMediaFromStore().
    // full_path's column-level COLLATE NOCASE (see Medium.kt) applies here automatically.
    @Query("SELECT full_path FROM media WHERE full_path IN (:paths)")
    fun getExistingPaths(paths: List<String>): List<String>

    // Dedup that ignores soft-deleted (recycle-bin) rows: a path that exists ONLY as a deleted_ts!=0
    // row must count as "not present" so a freshly re-created file there flows into the new-media set
    // and gets revived, instead of being deduped away and then IGNOREd by insertAllKeepingExisting -
    // which left it permanently invisible (see MediaRepository.reviveSoftDeleted).
    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 AND full_path IN (:paths)")
    fun getLivePaths(paths: List<String>): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts != 0 AND full_path IN (:paths)")
    fun getSoftDeletedPaths(paths: List<String>): List<String>

    // Soft-deleted rows with the fields reviveSoftDeleted needs to tell a genuinely re-created file
    // (different size/mtime on disk) from the very file the user just deleted (identical) - only the
    // former should be un-deleted; resurrecting the latter defeats the deletion.
    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts != 0 AND full_path IN (:paths)")
    fun getSoftDeletedMedia(paths: List<String>): List<Medium>

    // Un-deletes a row and refreshes it to a re-created file's metadata. Only safe to call for paths
    // the timestamp-filtered incremental sync actually matched (a fresh DATE_ADDED/DATE_MODIFIED), so
    // an untouched recycle-bin item - whose file keeps its old timestamps - is never resurrected. The
    // date/last_modified writes fire trg_media_date_sort_key_upd, re-sorting it to its new date.
    @Query("UPDATE media SET deleted_ts = 0, last_modified = :lastModified, date_taken = :dateTaken, size = :size, type = :type, date_added = :dateAdded WHERE full_path = :path COLLATE NOCASE")
    fun reviveMedium(path: String, lastModified: Long, dateTaken: Long, size: Long, type: Int, dateAdded: Long)

    @Transaction
    fun reviveMediaBatch(items: List<Medium>) {
        items.forEach { m ->
            reviveMedium(m.path, m.modified, m.taken, m.size, m.type, m.dateAdded)
        }
    }

    @Query("SELECT EXISTS(SELECT 1 FROM media LIMIT 1)")
    fun hasAnyMedia(): Boolean

    @Query("DELETE FROM media WHERE full_path IN (:paths)")
    fun deleteByPaths(paths: List<String>)

    @Delete
    fun deleteMedia(vararg medium: Medium)

    @Query("DELETE FROM media WHERE full_path = :path COLLATE NOCASE")
    fun deleteMediumPath(path: String)

    // Never replace a row at the destination path implicitly. A path collision must fail and be
    // resolved by the move operation; UPDATE OR REPLACE could silently drop the existing medium.
    @Query("UPDATE media SET filename = :newFilename, full_path = :newFullPath, parent_path = :newParentPath WHERE full_path = :oldPath COLLATE NOCASE")
    fun updateMedium(oldPath: String, newParentPath: String, newFilename: String, newFullPath: String): Int

    @Query("UPDATE media SET full_path = :newPath, deleted_ts = :deletedTS WHERE full_path = :oldPath COLLATE NOCASE")
    fun updateDeleted(newPath: String, deletedTS: Long, oldPath: String): Int

    @Query("UPDATE media SET deleted_ts = :deletedTS WHERE full_path = :path COLLATE NOCASE")
    fun softDelete(path: String, deletedTS: Long): Int

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

    /** Rowid-keyset pages keep metadata scans bounded even when the library has six figures of media. */
    @Query("SELECT rowid AS row_id, full_path, last_modified, date_taken, rating FROM media WHERE deleted_ts = 0 AND rowid > :afterRowId ORDER BY rowid LIMIT :limit")
    fun getMetadataScanRowsAfter(afterRowId: Long, limit: Int): List<MetadataScanRow>

    /** Bounded automatic-scan page, retaining the previous newest-first selection semantics. */
    @Query("SELECT rowid AS row_id, full_path, last_modified, date_taken, rating FROM media WHERE deleted_ts = 0 ORDER BY date_taken DESC, last_modified DESC LIMIT :limit OFFSET :offset")
    fun getNewestMetadataScanRows(limit: Int, offset: Int): List<MetadataScanRow>

    /** Folder variant; the prefix includes the slash so similarly named sibling folders are excluded. */
    @Query("SELECT rowid AS row_id, full_path, last_modified, date_taken, rating FROM media WHERE deleted_ts = 0 AND (full_path = :folderPath OR full_path LIKE :folderPrefix) AND rowid > :afterRowId ORDER BY rowid LIMIT :limit")
    fun getMetadataScanRowsForFolderAfter(folderPath: String, folderPrefix: String, afterRowId: Long, limit: Int): List<MetadataScanRow>

    /** Includes recycle-bin rows - the "is this database empty?" bootstrap check must count them,
     * see ExplorerViewModel.initializeDatabase. */
    @Query("SELECT COUNT(filename) FROM media")
    fun getTotalCountIncludingDeleted(): Int

    /** Stable rowid-keyed pagination for the missing-file sweep - OFFSET paging would skip rows
     * once earlier ones get deleted. See MediaRepository.pruneMissingMedia. */
    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 AND rowid > :afterRowId ORDER BY rowid LIMIT :limit")
    fun getLivePathsAfter(afterRowId: Long, limit: Int): List<String>

    @Query("SELECT rowid FROM media WHERE full_path = :path COLLATE NOCASE LIMIT 1")
    fun getRowId(path: String): Long?

    @Query("DELETE FROM media WHERE full_path IN (:paths)")
    fun deleteByPathsBatch(paths: List<String>)

    /** Thumbnail subselect mirrors the newest-first key the grids use (auto-maintained
     * date_sort_key via triggers), using the composite (deleted_ts, parent_path, date_sort_key)
     * index directly instead of evaluating a CASE expression per row. */
    @Query("SELECT parent_path, COUNT(filename) AS cnt, MAX(last_modified) AS max_modified, MAX(date_taken) AS max_taken, SUM(size) AS total_size, MAX(CASE WHEN type = 2 THEN 0 ELSE 1 END) AS has_images, MAX(CASE WHEN type = 2 THEN 1 ELSE 0 END) AS has_videos, SUM(CASE WHEN type = 1 THEN 1 ELSE 0 END) AS image_count, SUM(CASE WHEN type = 2 THEN 1 ELSE 0 END) AS video_count, (SELECT m2.full_path FROM media m2 WHERE m2.deleted_ts = 0 AND m2.parent_path = media.parent_path ORDER BY m2.date_sort_key DESC LIMIT 1) AS thumbnail FROM media WHERE deleted_ts = 0 GROUP BY parent_path")
    fun getDirectoryAggregates(): List<DirectoryAggregate>

    @Query("UPDATE media SET rating = :rating WHERE full_path = :path COLLATE NOCASE")
    fun updateRating(path: String, rating: Int)

    @Query("UPDATE media SET video_duration = :duration WHERE full_path = :path COLLATE NOCASE AND video_duration != :duration")
    fun updateVideoDuration(path: String, duration: Int)

    /** One-time backfill of legacy video rows that were stored with video_duration = 0. In a single
     * transaction so Room's InvalidationTracker fires once (one paged-grid reload) instead of once
     * per updated row. */
    @Transaction
    fun updateVideoDurations(durations: Map<String, Int>) {
        durations.forEach { (path, dur) -> updateVideoDuration(path, dur) }
    }

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
        ORDER BY date_sort_key ASC, full_path COLLATE NOCASE ASC
        """
    )
    fun getMediaPagedByDateAsc(): PagingSource<Int, Medium>

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY date_sort_key DESC, full_path COLLATE NOCASE ASC
        """
    )
    fun getMediaPagedByDateDesc(): PagingSource<Int, Medium>

    fun getMediaPagedByDate(desc: Boolean): PagingSource<Int, Medium> = if (desc) getMediaPagedByDateDesc() else getMediaPagedByDateAsc()

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY size ASC, full_path COLLATE NOCASE ASC
        """
    )
    fun getMediaPagedBySizeAsc(): PagingSource<Int, Medium>

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY size DESC, full_path COLLATE NOCASE ASC
        """
    )
    fun getMediaPagedBySizeDesc(): PagingSource<Int, Medium>

    fun getMediaPagedBySize(desc: Boolean): PagingSource<Int, Medium> = if (desc) getMediaPagedBySizeDesc() else getMediaPagedBySizeAsc()

    // Within each rating bucket the secondary sort is ALWAYS last_modified DESC (newest-changed
    // first), regardless of the rating direction - so `rating ASC` intentionally pairs with
    // `last_modified DESC`, not ASC. Mirrored in getActivePathsByRatingAsc, the filtered query in
    // MediaRepository.buildFilteredMediaQuery, and MediaViewModel.applySort.
    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY rating ASC, last_modified DESC, full_path COLLATE NOCASE ASC
        """
    )
    fun getMediaPagedByRatingAsc(): PagingSource<Int, Medium>

    @Query(
        """
        SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added
        FROM media WHERE deleted_ts = 0
        ORDER BY rating DESC, last_modified DESC, full_path COLLATE NOCASE ASC
        """
    )
    fun getMediaPagedByRatingDesc(): PagingSource<Int, Medium>

    fun getMediaPagedByRating(desc: Boolean): PagingSource<Int, Medium> = if (desc) getMediaPagedByRatingDesc() else getMediaPagedByRatingAsc()

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE ASC, full_path COLLATE NOCASE ASC")
    fun getMediaPagedByNameAsc(): PagingSource<Int, Medium>

    @Query("SELECT filename, full_path, parent_path, last_modified, date_taken, size, type, video_duration, is_favorite, deleted_ts, media_store_id, rating, date_sort_key, date_added FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE DESC, full_path COLLATE NOCASE ASC")
    fun getMediaPagedByNameDesc(): PagingSource<Int, Medium>

    // Paths-only mirrors of the getMediaPagedBy* queries above (same ordering, no LIMIT/OFFSET) -
    // used to hand the Viewer the full sorted path list on demand so swipe-through isn't limited to
    // whatever the grid has paged in so far. Same ASC/DESC split rationale as above.
    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY date_sort_key ASC, full_path COLLATE NOCASE ASC")
    suspend fun getActivePathsByDateAsc(): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY date_sort_key DESC, full_path COLLATE NOCASE ASC")
    suspend fun getActivePathsByDateDesc(): List<String>

    suspend fun getActivePathsByDate(desc: Boolean): List<String> = if (desc) getActivePathsByDateDesc() else getActivePathsByDateAsc()

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY size ASC, full_path COLLATE NOCASE ASC")
    suspend fun getActivePathsBySizeAsc(): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY size DESC, full_path COLLATE NOCASE ASC")
    suspend fun getActivePathsBySizeDesc(): List<String>

    suspend fun getActivePathsBySize(desc: Boolean): List<String> = if (desc) getActivePathsBySizeDesc() else getActivePathsBySizeAsc()

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY rating ASC, last_modified DESC, full_path COLLATE NOCASE ASC")
    suspend fun getActivePathsByRatingAsc(): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY rating DESC, last_modified DESC, full_path COLLATE NOCASE ASC")
    suspend fun getActivePathsByRatingDesc(): List<String>

    suspend fun getActivePathsByRating(desc: Boolean): List<String> = if (desc) getActivePathsByRatingDesc() else getActivePathsByRatingAsc()

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE ASC, full_path COLLATE NOCASE ASC")
    suspend fun getActivePathsByNameAsc(): List<String>

    @Query("SELECT full_path FROM media WHERE deleted_ts = 0 ORDER BY filename COLLATE NOCASE DESC, full_path COLLATE NOCASE ASC")
    suspend fun getActivePathsByNameDesc(): List<String>
}
