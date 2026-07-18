package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.FileHash

@Dao
interface FileHashDao {
    @Query("SELECT * FROM file_hashes WHERE full_path IN (:paths)")
    fun getByPaths(paths: List<String>): List<FileHash>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(entries: List<FileHash>)

    /** Housekeeping: drop cache rows for files that no longer exist in the media library. */
    @Query("DELETE FROM file_hashes WHERE full_path NOT IN (SELECT full_path FROM media)")
    fun pruneOrphans()
}
