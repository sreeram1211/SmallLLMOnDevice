package com.pocketsloth.app.domain.repository

import com.pocketsloth.app.domain.model.TrainingMetrics
import com.pocketsloth.app.domain.model.TrainingRun
import com.pocketsloth.app.domain.model.TrainingRunStatus
import kotlinx.coroutines.flow.Flow

interface TrainingRunRepository {
    fun observeRuns(): Flow<List<TrainingRun>>
    fun observeRun(id: Long): Flow<TrainingRun?>
    suspend fun getRun(id: Long): TrainingRun?
    suspend fun createRun(run: TrainingRun): Long
    suspend fun updateStatus(id: Long, status: TrainingRunStatus, errorMessage: String? = null)
    suspend fun updateProgress(id: Long, metrics: TrainingMetrics)
    suspend fun markStarted(id: Long, startedAtEpochMs: Long = System.currentTimeMillis())
    suspend fun markFinished(
        id: Long,
        status: TrainingRunStatus,
        finishedAtEpochMs: Long = System.currentTimeMillis(),
        errorMessage: String? = null,
    )
    suspend fun deleteRun(id: Long)
}
