package com.pocketsloth.app.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pocketsloth.app.domain.model.TrainingHyperparams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.hyperparamsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "training_hyperparams",
)

/**
 * DataStore-backed persistence for [TrainingHyperparams].
 */
class HyperparamsDataStore(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.hyperparamsDataStore)

    val hyperparams: Flow<TrainingHyperparams> = dataStore.data.map { prefs -> prefs.toHyperparams() }

    suspend fun get(): TrainingHyperparams = hyperparams.first()

    suspend fun save(hyperparams: TrainingHyperparams) {
        dataStore.edit { prefs ->
            prefs[Keys.LORA_R] = hyperparams.loraR
            prefs[Keys.LORA_ALPHA] = hyperparams.loraAlpha
            prefs[Keys.BATCH_SIZE] = hyperparams.batchSize
            prefs[Keys.GRAD_ACCUM] = hyperparams.gradAccum
            prefs[Keys.CONTEXT_LENGTH] = hyperparams.contextLength
            prefs[Keys.LEARNING_RATE] = hyperparams.learningRate
            prefs[Keys.EPOCHS] = hyperparams.epochs
        }
    }

    suspend fun resetToDefaults() = save(TrainingHyperparams.Default)

    private object Keys {
        val LORA_R = intPreferencesKey("lora_r")
        val LORA_ALPHA = intPreferencesKey("lora_alpha")
        val BATCH_SIZE = intPreferencesKey("batch_size")
        val GRAD_ACCUM = intPreferencesKey("grad_accum")
        val CONTEXT_LENGTH = intPreferencesKey("context_length")
        val LEARNING_RATE = floatPreferencesKey("learning_rate")
        val EPOCHS = intPreferencesKey("epochs")
    }

    private fun Preferences.toHyperparams(): TrainingHyperparams {
        val defaults = TrainingHyperparams.Default
        val batch = this[Keys.BATCH_SIZE] ?: defaults.batchSize
        val contextLen = this[Keys.CONTEXT_LENGTH] ?: defaults.contextLength
        return TrainingHyperparams(
            loraR = this[Keys.LORA_R] ?: defaults.loraR,
            loraAlpha = this[Keys.LORA_ALPHA] ?: defaults.loraAlpha,
            batchSize = if (batch == 1 || batch == 2) batch else defaults.batchSize,
            gradAccum = this[Keys.GRAD_ACCUM] ?: defaults.gradAccum,
            contextLength = contextLen.coerceIn(
                TrainingHyperparams.MIN_CONTEXT_LENGTH,
                TrainingHyperparams.MAX_CONTEXT_LENGTH,
            ),
            learningRate = this[Keys.LEARNING_RATE] ?: defaults.learningRate,
            epochs = this[Keys.EPOCHS] ?: defaults.epochs,
        )
    }
}
