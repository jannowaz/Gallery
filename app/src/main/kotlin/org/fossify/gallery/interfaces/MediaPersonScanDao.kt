package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.gallery.models.MediaPersonScan
import org.fossify.gallery.models.PersonScanCount

@Dao
interface MediaPersonScanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(scans: List<MediaPersonScan>)

    @Query("SELECT * FROM media_person_scans WHERE media_path IN (:paths)")
    fun getByPaths(paths: List<String>): List<MediaPersonScan>

    @Query(
        """
        SELECT media_path
        FROM media_person_scans
        WHERE (
              folder_path = :folderPath COLLATE NOCASE
              OR (:includeSubfolders = 1 AND folder_path LIKE :subfolderPattern ESCAPE '\')
          )
          AND result = :result
          AND is_kept = 0
        ORDER BY scanned_at DESC, media_path COLLATE NOCASE ASC
        """,
    )
    fun getPathsForFolderAndResult(
        folderPath: String,
        subfolderPattern: String,
        includeSubfolders: Boolean,
        result: String,
    ): List<String>

    @Query("SELECT result, COUNT(*) AS count FROM media_person_scans WHERE (folder_path = :folderPath COLLATE NOCASE OR (:includeSubfolders = 1 AND folder_path LIKE :subfolderPattern ESCAPE '\\')) AND is_kept = 0 GROUP BY result")
    fun getCountsForFolder(folderPath: String, subfolderPattern: String, includeSubfolders: Boolean): List<PersonScanCount>

    @Query("UPDATE media_person_scans SET is_kept = 1 WHERE media_path IN (:paths)")
    fun markKept(paths: List<String>): Int

    @Query("UPDATE media_person_scans SET media_path = :newPath, folder_path = :newFolderPath WHERE media_path = :oldPath COLLATE NOCASE")
    fun updatePath(oldPath: String, newPath: String, newFolderPath: String): Int

    @Query("DELETE FROM media_person_scans WHERE media_path = :path COLLATE NOCASE")
    fun deleteByPath(path: String): Int
}
