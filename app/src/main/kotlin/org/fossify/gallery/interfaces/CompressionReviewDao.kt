package org.fossify.gallery.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.fossify.gallery.models.CompressionReviewItem

@Dao
interface CompressionReviewDao {
    @Insert
    suspend fun insertAll(items: List<CompressionReviewItem>): List<Long>

    @Query("SELECT * FROM compression_review_items ORDER BY created_at DESC")
    fun getAllLive(): Flow<List<CompressionReviewItem>>

    @Query("SELECT * FROM compression_review_items WHERE job_id = :jobId")
    suspend fun getForJob(jobId: String): List<CompressionReviewItem>

    @Update
    suspend fun update(item: CompressionReviewItem)

    @Query("DELETE FROM compression_review_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("SELECT * FROM compression_review_items WHERE created_at < :olderThan")
    suspend fun getStale(olderThan: Long): List<CompressionReviewItem>

    @Query("SELECT temp_result_path FROM compression_review_items")
    suspend fun getAllTempPaths(): List<String>

    @Query("DELETE FROM compression_review_items WHERE created_at < :olderThan")
    suspend fun deleteStale(olderThan: Long)
}
