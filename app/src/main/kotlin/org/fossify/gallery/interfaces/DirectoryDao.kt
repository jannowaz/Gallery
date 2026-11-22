package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.models.Directory
import java.util.Locale

@Dao
interface DirectoryDao {
    @Query("SELECT path, thumbnail, filename, media_count, last_modified, date_taken, size, location, media_types, sort_value FROM directories")
    fun getAll(): List<Directory>

    @Query("SELECT path FROM directories")
    fun getAllPaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(directory: Directory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(directories: List<Directory>)

    @Query("DELETE FROM directories WHERE path = :path COLLATE NOCASE")
    fun deleteDirPath(path: String)

    @Query("DELETE FROM directories WHERE path IN (:paths)")
    fun deleteByPaths(paths: List<String>)

    @Query("UPDATE OR REPLACE directories SET thumbnail = :thumbnail, media_count = :mediaCnt, last_modified = :lastModified, date_taken = :dateTaken, size = :size, media_types = :mediaTypes, sort_value = :sortValue WHERE path = :path COLLATE NOCASE")
    fun updateDirectory(path: String, thumbnail: String, mediaCnt: Int, lastModified: Long, dateTaken: Long, size: Long, mediaTypes: Int, sortValue: String)

    @Query("DELETE FROM directories WHERE path = \'$RECYCLE_BIN\' COLLATE NOCASE")
    fun deleteRecycleBin()

    /**
     * Atomically replaces filesystem directory rows and prunes stale folders using the already
     * computed [directories] aggregate instead of running a second full scan of the `media` table
     * and emitting two separate Room invalidations. Virtual rows (recycle bin, favorites) don't
     * start with '/' and are left alone.
     */
    @Transaction
    fun syncFilesystemDirectories(directories: List<Directory>) {
        val liveLower = directories.mapTo(HashSet(directories.size)) { it.path.lowercase(Locale.ROOT) }
        val stalePaths = getAllPaths().filter { path ->
            path.startsWith("/") && path.lowercase(Locale.ROOT) !in liveLower
        }
        stalePaths.chunked(SQLITE_BIND_CHUNK_SIZE).forEach(::deleteByPaths)
        insertAll(directories)
    }

    companion object {
        private const val SQLITE_BIND_CHUNK_SIZE = 900
    }
}
