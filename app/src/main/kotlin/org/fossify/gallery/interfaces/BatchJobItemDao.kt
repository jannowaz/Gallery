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

    /** Everything except the given job's rows. Used when enqueuing a new batch: the single-slot
     * unique work means at most one batch runs at a time, so any rows left from an earlier job are
     * orphans (its worker was replaced/cancelled and will never process them). Clearing them stops
     * the pile-up that a repeated "move all" produced - 2328 dead rows across 8 stacked jobs on a
     * real device. */
    @Query("DELETE FROM batch_job_items WHERE job_id != :keepJobId")
    suspend fun deleteAllExcept(keepJobId: String)
}
