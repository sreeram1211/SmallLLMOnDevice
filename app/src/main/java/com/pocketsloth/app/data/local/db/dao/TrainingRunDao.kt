package com.pocketsloth.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pocketsloth.app.data.local.db.entity.TrainingRunEntity
import com.pocketsloth.app.domain.model.TrainingRunStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingRunDao {
    @Query("SELECT * FROM training_runs ORDER BY created_at DESC")
    fun observeAll(): Flow<List<TrainingRunEntity>>

    @Query("SELECT * FROM training_runs WHERE id = :id")
    fun observeById(id: Long): Flow<TrainingRunEntity?>

    @Query("SELECT * FROM training_runs WHERE id = :id")
    suspend fun getById(id: Long): TrainingRunEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TrainingRunEntity): Long

    @Update
    suspend fun update(entity: TrainingRunEntity)

    @Query(
        """
        UPDATE training_runs
        SET status = :status, error_message = :errorMessage
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(id: Long, status: TrainingRunStatus, errorMessage: String?)

    @Query(
        """
        UPDATE training_runs
        SET last_loss = :loss,
            last_epoch = :epoch,
            last_step = :step,
            total_steps = :totalSteps
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(
        id: Long,
        loss: Float,
        epoch: Int,
        step: Int,
        totalSteps: Int,
    )

    @Query(
        """
        UPDATE training_runs
        SET status = :status, started_at = :startedAt
        WHERE id = :id
        """,
    )
    suspend fun markStarted(id: Long, status: TrainingRunStatus, startedAt: Long)

    @Query(
        """
        UPDATE training_runs
        SET status = :status,
            finished_at = :finishedAt,
            error_message = :errorMessage
        WHERE id = :id
        """,
    )
    suspend fun markFinished(
        id: Long,
        status: TrainingRunStatus,
        finishedAt: Long,
        errorMessage: String?,
    )

    @Query("DELETE FROM training_runs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
