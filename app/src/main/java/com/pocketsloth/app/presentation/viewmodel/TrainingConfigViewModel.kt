package com.pocketsloth.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * TODO: inject FineTuningService (com.pocketsloth.app.service.FineTuningService)
 *       and NativeLoRAEngine (com.pocketsloth.app.native.NativeLoRAEngine)
 */
class TrainingConfigViewModel : ViewModel() {

    // TODO: inject FineTuningService / NativeLoRAEngine via FineTuningGateway
    private val gateway: FineTuningGateway? = null


    private val _uiState = MutableStateFlow(TrainingConfigUiState())
    val uiState: StateFlow<TrainingConfigUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TrainingConfigEvent>()
    val events: SharedFlow<TrainingConfigEvent> = _events.asSharedFlow()

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
        _uiState.update { it.copy(config = it.config.copy(batchSize = size)) }
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
                // TODO: FineTuningService.start(cfg) / NativeLoRAEngine.configure(...)
                gateway?.start(cfg)
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
}
