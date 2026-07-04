package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import org.fossify.gallery.models.BatchJobItem

@Dao
interface BatchJobItemDao {
    @Insert
    suspend fun insertAll(items: List<BatchJobItem>)

    @Query("SELECT * FROM batch_job_items WHERE job_id = :jobId")
    suspend fun getForJob(jobId: String): List<BatchJobItem>

    @Query("DELETE FROM batch_job_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM batch_job_items WHERE job_id = :jobId")
    suspend fun deleteJob(jobId: String)

    @Query("DELETE FROM batch_job_items WHERE created_at < :olderThan")
    suspend fun deleteStale(olderThan: Long)
}
