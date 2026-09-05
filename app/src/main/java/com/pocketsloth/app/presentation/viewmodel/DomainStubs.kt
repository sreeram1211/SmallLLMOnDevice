package com.pocketsloth.app.presentation.viewmodel

/**
 * Presentation-layer UI models shared by screens / ViewModels.
 * Domain/data types live under com.pocketsloth.app.domain.* and com.pocketsloth.app.data.*.
 */

/** UI draft/list model mirroring com.pocketsloth.app.domain.model.InstructionExample */
data class UiInstructionExample(
    val id: String,
    val instruction: String,
    val input: String = "",
    val output: String,
)

/** UI form model mirroring TrainingHyperparams (+ model path / dataset) */
data class UiTrainingConfig(
    val modelPath: String = "",
    val loraRank: Int = 8,
    val loraAlpha: Int = 16,
    val batchSize: Int = 1,
    val gradAccumulation: Int = 4,
    val contextLength: Int = 512,
    val learningRate: Float = 2e-4f,
    val epochs: Int = 3,
    val datasetId: String? = null,
)

enum class ThermalState {
    Nominal,
    Fair,
    Serious,
    Critical,
}

/** Dashboard UI metrics mapped from domain TrainingMetrics + ThermalMonitor / FineTuningService */
data class UiTrainingMetrics(
    val step: Int = 0,
    val epoch: Int = 0,
    val loss: Float = 0f,
    val lossHistory: List<Float> = emptyList(),
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val thermalState: ThermalState = ThermalState.Nominal,
    val batteryPercent: Int = 100,
    val etaSeconds: Long = -1L,
    val isPaused: Boolean = false,
    val isRunning: Boolean = false,
    val statusMessage: String = "Idle",
)

enum class ChatModelMode {
    Base,
    FineTunedAdapter,
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val isStreaming: Boolean = false,
    val modelMode: ChatModelMode? = null,
)

enum class ChatRole {
    User,
    Assistant,
    System,
}

/**
 * Thin inference contract for chat. Real llama.cpp generation is still stubbed;
 * adapter path comes from the last [com.pocketsloth.app.domain.model.TrainingRun].
 */
interface ChatInferenceGateway {
    fun streamCompletion(
        prompt: String,
        useAdapter: Boolean,
    ): kotlinx.coroutines.flow.Flow<String>

    suspend fun exportAdapter(): String?
    suspend fun shareAdapterPath(): String?
}
