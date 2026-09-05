package com.pocketsloth.app.presentation.viewmodel

/**
 * Presentation-layer stubs / contracts that mirror expected domain & native APIs.
 *
 * When real implementations land, inject:
 * - [com.pocketsloth.app.domain.model.InstructionExample]
 * - [com.pocketsloth.app.domain.repository.DatasetRepository]
 * - [com.pocketsloth.app.domain.model.TrainingHyperparams]
 * - [com.pocketsloth.app.domain.model.TrainingMetrics]
 * - [com.pocketsloth.app.native.NativeLoRAEngine]
 * - [com.pocketsloth.app.service.FineTuningService]
 *
 * TODO: replace these stubs with DI-provided domain/data/native types.
 */

/** UI draft/list model mirroring com.pocketsloth.app.domain.model.InstructionExample */
data class UiInstructionExample(
    val id: String,
    val instruction: String,
    val input: String = "",
    val output: String,
)

/** UI form model mirroring com.pocketsloth.app.domain.model.TrainingHyperparams (+ model path) */
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

/** Dashboard UI metrics; map from domain TrainingMetrics + ThermalMonitor / FineTuningService */
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
 * Contract for com.pocketsloth.app.domain.DatasetRepository / data layer.
 * TODO: inject real repository from com.pocketsloth.app.data.*
 */
interface DatasetRepositoryGateway {
    suspend fun listExamples(): List<UiInstructionExample>
    suspend fun upsert(example: UiInstructionExample)
    suspend fun delete(id: String)
    suspend fun importFromUri(uri: String): Int
}

/**
 * Contract bridging to com.pocketsloth.app.service.FineTuningService
 * and com.pocketsloth.app.native.NativeLoRAEngine.
 * TODO: inject FineTuningService / NativeLoRAEngine
 */
interface FineTuningGateway {
    suspend fun start(config: UiTrainingConfig)
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    fun metricsFlow(): kotlinx.coroutines.flow.Flow<UiTrainingMetrics>
}

/**
 * Contract for chat inference via NativeLoRAEngine (base vs adapter).
 * TODO: inject com.pocketsloth.app.native.NativeLoRAEngine
 */
interface ChatInferenceGateway {
    fun streamCompletion(
        prompt: String,
        useAdapter: Boolean,
    ): kotlinx.coroutines.flow.Flow<String>

    suspend fun exportAdapter(): String?
    suspend fun shareAdapterPath(): String?
}

/** In-memory DatasetRepository until data layer is wired. */
class InMemoryDatasetRepository : DatasetRepositoryGateway {
    private val items = linkedMapOf<String, UiInstructionExample>()

    init {
        // Seed samples for UI development
        listOf(
            UiInstructionExample(
                id = "sample-1",
                instruction = "Summarize the following text in one sentence.",
                input = "PocketSloth trains LoRA adapters on-device.",
                output = "PocketSloth fine-tunes models locally with LoRA.",
            ),
            UiInstructionExample(
                id = "sample-2",
                instruction = "Rewrite politely.",
                input = "Send me the file now.",
                output = "Could you please send me the file when you have a moment?",
            ),
        ).forEach { items[it.id] = it }
    }

    override suspend fun listExamples(): List<UiInstructionExample> = items.values.toList()

    override suspend fun upsert(example: UiInstructionExample) {
        items[example.id] = example
    }

    override suspend fun delete(id: String) {
        items.remove(id)
    }

    override suspend fun importFromUri(uri: String): Int {
        // TODO: parse JSONL/Alpaca via com.pocketsloth.app.data.*
        return 0
    }
}
