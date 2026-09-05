package com.pocketsloth.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pocketsloth.app.data.local.db.entity.DatasetEntity
import kotlinx.coroutines.flow.Flow

/**
 * Flat projection used by [DatasetDao] queries that join example counts.
 */
data class DatasetCountRow(
    @Embedded
    val dataset: DatasetEntity,
    val exampleCount: Int,
)

@Dao
interface DatasetDao {
    @Query(
        """
        SELECT d.*,
               (SELECT COUNT(*) FROM examples e WHERE e.dataset_id = d.id) AS exampleCount
        FROM datasets d
        ORDER BY d.created_at DESC
        """,
    )
    fun observeAllWithCount(): Flow<List<DatasetCountRow>>

    @Query(
        """
        SELECT d.*,
               (SELECT COUNT(*) FROM examples e WHERE e.dataset_id = d.id) AS exampleCount
        FROM datasets d
        WHERE d.id = :id
        """,
    )
    fun observeByIdWithCount(id: Long): Flow<DatasetCountRow?>

    @Query("SELECT * FROM datasets WHERE id = :id")
    suspend fun getById(id: Long): DatasetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DatasetEntity): Long

    @Update
    suspend fun update(entity: DatasetEntity)

    @Query("UPDATE datasets SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM datasets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM examples WHERE dataset_id = :datasetId")
    suspend fun countExamples(datasetId: Long): Int
}
