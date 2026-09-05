package com.pocketsloth.app.data.repository

import com.pocketsloth.app.data.local.db.dao.DatasetDao
import com.pocketsloth.app.data.local.db.dao.ExampleDao
import com.pocketsloth.app.data.local.db.entity.DatasetEntity
import com.pocketsloth.app.data.local.db.entity.toDomain
import com.pocketsloth.app.data.local.db.entity.toEntity
import com.pocketsloth.app.domain.model.Dataset
import com.pocketsloth.app.domain.model.DatasetSourceFormat
import com.pocketsloth.app.domain.model.InstructionExample
import com.pocketsloth.app.domain.repository.DatasetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DatasetRepositoryImpl(
    private val datasetDao: DatasetDao,
    private val exampleDao: ExampleDao,
) : DatasetRepository {

    override fun observeDatasets(): Flow<List<Dataset>> =
        datasetDao.observeAllWithCount().map { rows ->
            rows.map { it.dataset.toDomain(exampleCount = it.exampleCount) }
        }

    override fun observeDataset(id: Long): Flow<Dataset?> =
        datasetDao.observeByIdWithCount(id).map { row ->
            row?.dataset?.toDomain(exampleCount = row.exampleCount)
        }

    override suspend fun getDataset(id: Long): Dataset? {
        val entity = datasetDao.getById(id) ?: return null
        val count = exampleDao.countByDataset(id)
        return entity.toDomain(exampleCount = count)
    }

    override suspend fun createDataset(
        name: String,
        description: String?,
        sourceFormat: DatasetSourceFormat,
    ): Long {
        val entity = DatasetEntity(
            name = name.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            createdAtEpochMs = System.currentTimeMillis(),
            sourceFormat = sourceFormat,
        )
        return datasetDao.insert(entity)
    }

    override suspend fun deleteDataset(id: Long) {
        datasetDao.deleteById(id)
    }

    override suspend fun renameDataset(id: Long, name: String) {
        require(name.isNotBlank()) { "name must not be blank" }
        datasetDao.rename(id, name.trim())
    }

    override fun observeExamples(datasetId: Long): Flow<List<InstructionExample>> =
        exampleDao.observeByDataset(datasetId).map { list -> list.map { it.toDomain() } }

    override suspend fun getExamples(datasetId: Long): List<InstructionExample> =
        exampleDao.getByDataset(datasetId).map { it.toDomain() }

    override suspend fun getExampleCount(datasetId: Long): Int =
        exampleDao.countByDataset(datasetId)

    override suspend fun addExamples(datasetId: Long, examples: List<InstructionExample>): Int {
        if (examples.isEmpty()) return 0
        require(datasetDao.getById(datasetId) != null) {
            "Dataset $datasetId does not exist"
        }
        val entities = examples.map { it.toEntity(datasetId = datasetId).copy(id = 0L) }
        return exampleDao.insertAll(entities).size
    }

    override suspend fun clearExamples(datasetId: Long) {
        exampleDao.deleteByDataset(datasetId)
    }

    override suspend fun deleteExample(exampleId: Long) {
        exampleDao.deleteById(exampleId)
    }
}
