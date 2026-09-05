package com.pocketsloth.app.data.repository

import com.pocketsloth.app.data.local.datastore.HyperparamsDataStore
import com.pocketsloth.app.domain.model.TrainingHyperparams
import com.pocketsloth.app.domain.repository.HyperparamsRepository
import kotlinx.coroutines.flow.Flow

class HyperparamsRepositoryImpl(
    private val dataStore: HyperparamsDataStore,
) : HyperparamsRepository {
    override fun observeHyperparams(): Flow<TrainingHyperparams> = dataStore.hyperparams

    override suspend fun getHyperparams(): TrainingHyperparams = dataStore.get()

    override suspend fun saveHyperparams(hyperparams: TrainingHyperparams) {
        dataStore.save(hyperparams)
    }

    override suspend fun resetToDefaults() {
        dataStore.resetToDefaults()
    }
}
