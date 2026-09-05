package com.pocketsloth.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pocketsloth.app.data.local.db.entity.ExampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExampleDao {
    @Query("SELECT * FROM examples WHERE dataset_id = :datasetId ORDER BY id ASC")
    fun observeByDataset(datasetId: Long): Flow<List<ExampleEntity>>

    @Query("SELECT * FROM examples WHERE dataset_id = :datasetId ORDER BY id ASC")
    suspend fun getByDataset(datasetId: Long): List<ExampleEntity>

    @Query("SELECT COUNT(*) FROM examples WHERE dataset_id = :datasetId")
    suspend fun countByDataset(datasetId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ExampleEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExampleEntity): Long

    @Update
    suspend fun update(entity: ExampleEntity)

    @Query("DELETE FROM examples WHERE dataset_id = :datasetId")
    suspend fun deleteByDataset(datasetId: Long)

    @Query("DELETE FROM examples WHERE id = :id")
    suspend fun deleteById(id: Long)
}
