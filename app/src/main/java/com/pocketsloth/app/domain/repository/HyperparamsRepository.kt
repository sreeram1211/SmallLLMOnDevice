package com.pocketsloth.app.domain.repository

import com.pocketsloth.app.domain.model.TrainingHyperparams
import kotlinx.coroutines.flow.Flow

interface HyperparamsRepository {
    fun observeHyperparams(): Flow<TrainingHyperparams>
    suspend fun getHyperparams(): TrainingHyperparams
    suspend fun saveHyperparams(hyperparams: TrainingHyperparams)
    suspend fun resetToDefaults()
}
