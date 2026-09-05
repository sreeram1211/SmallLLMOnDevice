package com.pocketsloth.app.presentation.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketsloth.app.domain.model.DatasetSourceFormat
import com.pocketsloth.app.domain.model.PromptFormat
import com.pocketsloth.app.domain.model.TrainingHyperparams
import com.pocketsloth.app.domain.model.TrainingRun
import com.pocketsloth.app.domain.model.TrainingRunStatus
import com.pocketsloth.app.domain.repository.DatasetRepository
import com.pocketsloth.app.domain.repository.HyperparamsRepository
import com.pocketsloth.app.domain.repository.TrainingRunRepository
import com.pocketsloth.app.service.FineTuningService
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainingConfigUiState(
    val config: UiTrainingConfig = UiTrainingConfig(),
    val isStarting: Boolean = false,
    val validationError: String? = null,
)

sealed interface TrainingConfigEvent {
    data object NavigateToDashboard : TrainingConfigEvent
}

/**
 * Training hyperparameter config VM.
 * Persists via [HyperparamsRepository] and starts [FineTuningService] with a Room [TrainingRun].
 */
class TrainingConfigViewModel(
    private val appContext: Context,
    private val hyperparamsRepository: HyperparamsRepository,
    private val datasetRepository: DatasetRepository,
    private val trainingRunRepository: TrainingRunRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrainingConfigUiState())
    val uiState: StateFlow<TrainingConfigUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TrainingConfigEvent>()
    val events: SharedFlow<TrainingConfigEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            hyperparamsRepository.observeHyperparams().collect { hp ->
                _uiState.update { state ->
                    state.copy(
                        config = state.config.copy(
                            loraRank = hp.loraR,
                            loraAlpha = hp.loraAlpha,
                            batchSize = hp.batchSize,
                            gradAccumulation = hp.gradAccum,
                            contextLength = hp.contextLength,
                            learningRate = hp.learningRate,
                            epochs = hp.epochs,
                        ),
                    )
                }
            }
        }
    }

    fun setModelPath(path: String) {
        _uiState.update { it.copy(config = it.config.copy(modelPath = path), validationError = null) }
    }

    fun setLoraRank(rank: Int) {
        _uiState.update { it.copy(config = it.config.copy(loraRank = rank)) }
    }

    fun setLoraAlpha(alpha: Int) {
        _uiState.update { it.copy(config = it.config.copy(loraAlpha = alpha.coerceAtLeast(1))) }
    }

    fun setBatchSize(size: Int) {
        val coerced = if (size == 2) 2 else 1
        _uiState.update { it.copy(config = it.config.copy(batchSize = coerced)) }
    }

    fun setGradAccumulation(steps: Int) {
        _uiState.update {
            it.copy(config = it.config.copy(gradAccumulation = steps.coerceIn(1, 64)))
        }
    }

    fun setContextLength(length: Int) {
        _uiState.update {
            it.copy(config = it.config.copy(contextLength = length.coerceIn(256, 512)))
        }
    }

    fun setLearningRate(lr: Float) {
        if (lr <= 0f) return
        _uiState.update { it.copy(config = it.config.copy(learningRate = lr)) }
    }

    fun setEpochs(epochs: Int) {
        _uiState.update {
            it.copy(config = it.config.copy(epochs = epochs.coerceIn(1, 100)))
        }
    }

    fun startTraining() {
        val cfg = _uiState.value.config
        if (cfg.modelPath.isBlank()) {
            _uiState.update { it.copy(validationError = "Select a base model path.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isStarting = true, validationError = null) }
            try {
                val hyperparams = TrainingHyperparams(
                    loraR = cfg.loraRank,
                    loraAlpha = cfg.loraAlpha,
                    batchSize = if (cfg.batchSize == 2) 2 else 1,
                    gradAccum = cfg.gradAccumulation.coerceAtLeast(1),
                    contextLength = cfg.contextLength.coerceIn(256, 512),
                    learningRate = cfg.learningRate,
                    epochs = cfg.epochs.coerceAtLeast(1),
                )
                hyperparamsRepository.saveHyperparams(hyperparams)

                val datasetId = resolveDatasetId(cfg.datasetId)
                val exampleCount = datasetRepository.getExampleCount(datasetId)
                if (exampleCount <= 0) {
                    _uiState.update {
                        it.copy(validationError = "Add or import examples before training.")
                    }
                    return@launch
                }

                val adapterDir = File(appContext.filesDir, "adapters").apply { mkdirs() }
                val adapterPath = File(
                    adapterDir,
                    "lora_${System.currentTimeMillis()}.safetensors",
                ).absolutePath

                val runId = trainingRunRepository.createRun(
                    TrainingRun(
                        datasetId = datasetId,
                        status = TrainingRunStatus.PENDING,
                        hyperparams = hyperparams,
                        promptFormat = PromptFormat.CHAT_ML,
                        modelPath = cfg.modelPath,
                        adapterOutputPath = adapterPath,
                    ),
                )

                val intent = FineTuningService.startIntent(appContext, runId)
                startForegroundServiceCompat(intent)

                _events.emit(TrainingConfigEvent.NavigateToDashboard)
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(validationError = t.message ?: "Failed to start training.")
                }
            } finally {
                _uiState.update { it.copy(isStarting = false) }
            }
        }
    }

    private suspend fun resolveDatasetId(configured: String?): Long {
        configured?.toLongOrNull()?.let { id ->
            if (datasetRepository.getDataset(id) != null) return id
        }
        val existing = datasetRepository.observeDatasets().first()
        existing.firstOrNull()?.id?.let { return it }
        return datasetRepository.createDataset(
            name = "Default",
            description = "Primary on-device fine-tuning dataset",
            sourceFormat = DatasetSourceFormat.MANUAL,
        )
    }

    private fun startForegroundServiceCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(appContext, intent)
        } else {
            appContext.startService(intent)
        }
    }
}
