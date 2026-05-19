package org.fossify.gallery.interfaces

import androidx.room.*
import org.fossify.gallery.models.MediaCollection

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sort_order")
    fun getAll(): List<MediaCollection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(collection: MediaCollection): Long

    @Delete
    fun delete(collection: MediaCollection)

    @Query("DELETE FROM collections WHERE id = :id")
    fun deleteById(id: Long)

    @Query("SELECT * FROM collections WHERE id = :id")
    fun getById(id: Long): MediaCollection?
}
