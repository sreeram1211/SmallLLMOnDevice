package com.pocketsloth.app.domain.model

/**
 * Live or final metrics streamed from the native LoRA engine during a run.
 */
data class TrainingMetrics(
    val runId: Long,
    val epoch: Int,
    val step: Int,
    val totalSteps: Int,
    val loss: Float,
    val learningRate: Float,
    val tokensPerSecond: Float = 0f,
    val elapsedMs: Long = 0L,
    val etaMs: Long = -1L,
) {
    val progressFraction: Float
        get() = if (totalSteps <= 0) 0f else (step.toFloat() / totalSteps).coerceIn(0f, 1f)

    val progressPercent: Int
        get() = (progressFraction * 100f).toInt()
}
