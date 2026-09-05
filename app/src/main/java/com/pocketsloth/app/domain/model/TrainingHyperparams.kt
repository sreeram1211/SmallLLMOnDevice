package com.pocketsloth.app.domain.model

/**
 * LoRA / training hyperparameters persisted via DataStore and applied per run.
 *
 * Constraints:
 * - [batchSize] is 1 or 2 (memory-bound on-device).
 * - [contextLength] is in 256..512.
 */
data class TrainingHyperparams(
    val loraR: Int = DEFAULT_LORA_R,
    val loraAlpha: Int = DEFAULT_LORA_ALPHA,
    val batchSize: Int = DEFAULT_BATCH_SIZE,
    val gradAccum: Int = DEFAULT_GRAD_ACCUM,
    val contextLength: Int = DEFAULT_CONTEXT_LENGTH,
    val learningRate: Float = DEFAULT_LEARNING_RATE,
    val epochs: Int = DEFAULT_EPOCHS,
) {
    init {
        require(loraR > 0) { "loraR must be > 0" }
        require(loraAlpha > 0) { "loraAlpha must be > 0" }
        require(batchSize == 1 || batchSize == 2) { "batchSize must be 1 or 2" }
        require(gradAccum >= 1) { "gradAccum must be >= 1" }
        require(contextLength in MIN_CONTEXT_LENGTH..MAX_CONTEXT_LENGTH) {
            "contextLength must be in $MIN_CONTEXT_LENGTH..$MAX_CONTEXT_LENGTH"
        }
        require(learningRate > 0f) { "learningRate must be > 0" }
        require(epochs >= 1) { "epochs must be >= 1" }
    }

    companion object {
        const val MIN_CONTEXT_LENGTH = 256
        const val MAX_CONTEXT_LENGTH = 512
        const val DEFAULT_LORA_R = 8
        const val DEFAULT_LORA_ALPHA = 16
        const val DEFAULT_BATCH_SIZE = 1
        const val DEFAULT_GRAD_ACCUM = 4
        const val DEFAULT_CONTEXT_LENGTH = 512
        const val DEFAULT_LEARNING_RATE = 2e-4f
        const val DEFAULT_EPOCHS = 1

        val Default = TrainingHyperparams()
    }
}
