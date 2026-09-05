package com.pocketsloth.app.domain.repository

import com.pocketsloth.app.domain.model.Dataset
import com.pocketsloth.app.domain.model.InstructionExample
import kotlinx.coroutines.flow.Flow

interface DatasetRepository {
    fun observeDatasets(): Flow<List<Dataset>>
    fun observeDataset(id: Long): Flow<Dataset?>
    suspend fun getDataset(id: Long): Dataset?
    suspend fun createDataset(
        name: String,
        description: String? = null,
        sourceFormat: com.pocketsloth.app.domain.model.DatasetSourceFormat =
            com.pocketsloth.app.domain.model.DatasetSourceFormat.MANUAL,
    ): Long

    suspend fun deleteDataset(id: Long)
    suspend fun renameDataset(id: Long, name: String)

    fun observeExamples(datasetId: Long): Flow<List<InstructionExample>>
    suspend fun getExamples(datasetId: Long): List<InstructionExample>
    suspend fun getExampleCount(datasetId: Long): Int
    suspend fun addExamples(datasetId: Long, examples: List<InstructionExample>): Int
    suspend fun clearExamples(datasetId: Long)
    suspend fun deleteExample(exampleId: Long)
}
