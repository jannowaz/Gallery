package org.fossify.gallery.interfaces

import androidx.room.*
import org.fossify.gallery.models.MediaTag
import org.fossify.gallery.models.TagCount
import org.fossify.gallery.models.TagPathRow

@Dao
interface MediaTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: MediaTag)

    @Query("DELETE FROM media_tags WHERE media_path = :path AND tag = :tag")
    suspend fun delete(path: String, tag: String)

    @Query("DELETE FROM media_tags WHERE media_path = :path")
    suspend fun deleteAllForPath(path: String)

    /** Batch variant for mass deletions - one transaction instead of one per path. */
    @Query("DELETE FROM media_tags WHERE media_path IN (:paths)")
    fun deleteAllForPathsBatch(paths: List<String>)

    /** Replaces one file's tags without exposing the delete/insert gap to another DB reader. */
    @Transaction
    suspend fun replaceTagsForPath(path: String, tags: List<String>) {
        deleteAllForPath(path)
        tags.filter { it.isNotBlank() }.distinct().forEach { tag ->
            insert(MediaTag(mediaPath = path, tag = tag))
        }
    }

    @Query("DELETE FROM media_tags WHERE tag = :tag")
    suspend fun deleteTagEverywhere(tag: String)

    @Query("SELECT tag FROM media_tags WHERE media_path = :path")
    suspend fun getTagsForPath(path: String): List<String>

    @Query("SELECT DISTINCT media_path FROM media_tags")
    suspend fun getTaggedPaths(): List<String>

    @Query("SELECT tag, COUNT(*) as cnt FROM media_tags GROUP BY tag ORDER BY cnt DESC")
    suspend fun getTagCounts(): List<TagCount>

    @Query("SELECT tag, COUNT(*) as cnt FROM media_tags WHERE media_path LIKE :pattern GROUP BY tag ORDER BY cnt DESC")
    suspend fun getTagCountsInFolder(pattern: String): List<TagCount>

    @Query("SELECT DISTINCT media_path FROM media_tags WHERE tag = :tag")
    suspend fun getPathsForTag(tag: String): List<String>

    @Query("SELECT DISTINCT media_path FROM media_tags WHERE tag IN (:tags)")
    suspend fun getPathsForTags(tags: List<String>): List<String>

    @Query("SELECT tag, media_path FROM media_tags")
    suspend fun getAllTagPathPairs(): List<TagPathRow>

    @Query("SELECT tag, media_path FROM media_tags WHERE media_path IN (:paths)")
    suspend fun getTagsForPaths(paths: List<String>): List<TagPathRow>

    /** Moves tag rows without allowing a destination collision to delete either side. */
    @Query("SELECT tag FROM media_tags WHERE media_path = :path")
    fun getTagsForPathSync(path: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSync(tag: MediaTag)

    @Query("DELETE FROM media_tags WHERE media_path = :path")
    fun deleteAllForPathSync(path: String)

    @Transaction
    fun migratePath(oldPath: String, newPath: String) {
        if (oldPath.equals(newPath, ignoreCase = true)) return
        getTagsForPathSync(oldPath).forEach { insertSync(MediaTag(newPath, it)) }
        deleteAllForPathSync(oldPath)
    }

    @Transaction
    fun copyPath(sourcePath: String, targetPath: String) {
        getTagsForPathSync(sourcePath).forEach { insertSync(MediaTag(targetPath, it)) }
    }
}
