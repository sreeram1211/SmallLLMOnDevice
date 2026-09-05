package com.pocketsloth.app.domain.model

/**
 * Metadata for a single fine-tuning job.
 */
data class TrainingRun(
    val id: Long = 0L,
    val datasetId: Long,
    val status: TrainingRunStatus = TrainingRunStatus.PENDING,
    val hyperparams: TrainingHyperparams = TrainingHyperparams.Default,
    val promptFormat: PromptFormat = PromptFormat.CHAT_ML,
    val modelPath: String,
    val adapterOutputPath: String,
    val startedAtEpochMs: Long? = null,
    val finishedAtEpochMs: Long? = null,
    val lastLoss: Float? = null,
    val lastEpoch: Int = 0,
    val lastStep: Int = 0,
    val totalSteps: Int = 0,
    val errorMessage: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

enum class TrainingRunStatus {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    STOPPED,
    FAILED,
    THERMAL_PAUSED,
}

enum class PromptFormat {
    CHAT_ML,
    ALPACA,
}
