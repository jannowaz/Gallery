package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.MediaReviewState

@Dao
interface MediaReviewStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(state: MediaReviewState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(states: List<MediaReviewState>)

    @Query("SELECT status FROM media_review_states WHERE media_path = :path COLLATE NOCASE LIMIT 1")
    fun getStatus(path: String): String?

    @Query("DELETE FROM media_review_states WHERE media_path = :path COLLATE NOCASE")
    fun clear(path: String)

    @Query("DELETE FROM media_review_states WHERE media_path IN (:paths)")
    fun clearAll(paths: Collection<String>)

    @Query("UPDATE media_review_states SET media_path = :newPath WHERE media_path = :oldPath COLLATE NOCASE")
    fun updatePath(oldPath: String, newPath: String): Int
}
