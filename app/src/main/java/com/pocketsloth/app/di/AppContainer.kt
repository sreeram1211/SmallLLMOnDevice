package com.pocketsloth.app.di

import android.content.Context
import com.pocketsloth.app.data.importer.CsvDatasetImporter
import com.pocketsloth.app.data.importer.JsonlDatasetImporter
import com.pocketsloth.app.data.local.datastore.HyperparamsDataStore
import com.pocketsloth.app.data.local.db.AppDatabase
import com.pocketsloth.app.data.repository.DatasetRepositoryImpl
import com.pocketsloth.app.data.repository.HyperparamsRepositoryImpl
import com.pocketsloth.app.data.repository.TrainingRunRepositoryImpl
import com.pocketsloth.app.domain.repository.DatasetRepository
import com.pocketsloth.app.domain.repository.HyperparamsRepository
import com.pocketsloth.app.domain.repository.TrainingRunRepository
import com.pocketsloth.app.native.NativeLoRAEngine

/**
 * Manual DI graph rooted at [com.pocketsloth.app.PocketSlothApp].
 * Constructs Room, DataStore, repositories, and the process-scoped native engine.
 */
class AppContainer(
    context: Context,
    val loraEngine: NativeLoRAEngine,
) {
    val appContext: Context = context.applicationContext

    val database: AppDatabase = AppDatabase.getInstance(appContext)

    val hyperparamsDataStore: HyperparamsDataStore = HyperparamsDataStore(appContext)

    val datasetRepository: DatasetRepository =
        DatasetRepositoryImpl(database.datasetDao(), database.exampleDao())

    val hyperparamsRepository: HyperparamsRepository =
        HyperparamsRepositoryImpl(hyperparamsDataStore)

    val trainingRunRepository: TrainingRunRepository =
        TrainingRunRepositoryImpl(database.trainingRunDao())

    val jsonlImporter: JsonlDatasetImporter = JsonlDatasetImporter()
    val csvImporter: CsvDatasetImporter = CsvDatasetImporter()
}
