package com.pocketsloth.app.native

/**
 * Snapshot of training progress emitted from the native engine via JNI.
 */
data class TrainingMetrics(
    val step: Int,
    val totalSteps: Int,
    val epoch: Int,
    val loss: Float,
    val learningRate: Float,
    val memoryMb: Float,
    val tokensPerSec: Float,
    val status: Status = Status.RUNNING,
    val message: String = ""
) {
    enum class Status {
        IDLE,
        INITIALIZING,
        RUNNING,
        COMPLETED,
        CANCELLED,
        ERROR
    }

    val progress: Float
        get() = if (totalSteps <= 0) 0f else (step.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
}
