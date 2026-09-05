package com.pocketsloth.app.data.repository

import com.pocketsloth.app.data.local.db.dao.TrainingRunDao
import com.pocketsloth.app.data.local.db.entity.toDomain
import com.pocketsloth.app.data.local.db.entity.toEntity
import com.pocketsloth.app.domain.model.TrainingMetrics
import com.pocketsloth.app.domain.model.TrainingRun
import com.pocketsloth.app.domain.model.TrainingRunStatus
import com.pocketsloth.app.domain.repository.TrainingRunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrainingRunRepositoryImpl(
    private val trainingRunDao: TrainingRunDao,
) : TrainingRunRepository {

    override fun observeRuns(): Flow<List<TrainingRun>> =
        trainingRunDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeRun(id: Long): Flow<TrainingRun?> =
        trainingRunDao.observeById(id).map { it?.toDomain() }

    override suspend fun getRun(id: Long): TrainingRun? =
        trainingRunDao.getById(id)?.toDomain()

    override suspend fun createRun(run: TrainingRun): Long =
        trainingRunDao.insert(run.toEntity().copy(id = 0L))

    override suspend fun updateStatus(
        id: Long,
        status: TrainingRunStatus,
        errorMessage: String?,
    ) {
        trainingRunDao.updateStatus(id, status, errorMessage)
    }

    override suspend fun updateProgress(id: Long, metrics: TrainingMetrics) {
        trainingRunDao.updateProgress(
            id = id,
            loss = metrics.loss,
            epoch = metrics.epoch,
            step = metrics.step,
            totalSteps = metrics.totalSteps,
        )
    }

    override suspend fun markStarted(id: Long, startedAtEpochMs: Long) {
        trainingRunDao.markStarted(id, TrainingRunStatus.RUNNING, startedAtEpochMs)
    }

    override suspend fun markFinished(
        id: Long,
        status: TrainingRunStatus,
        finishedAtEpochMs: Long,
        errorMessage: String?,
    ) {
        trainingRunDao.markFinished(id, status, finishedAtEpochMs, errorMessage)
    }

    override suspend fun deleteRun(id: Long) {
        trainingRunDao.deleteById(id)
    }
}
