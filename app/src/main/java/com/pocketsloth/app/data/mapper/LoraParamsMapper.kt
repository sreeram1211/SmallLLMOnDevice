package com.pocketsloth.app.data.mapper

import com.pocketsloth.app.domain.model.TrainingHyperparams
import com.pocketsloth.app.domain.model.TrainingMetrics as DomainMetrics
import com.pocketsloth.app.native.LoraParams
import com.pocketsloth.app.native.TrainingMetrics as NativeMetrics

fun TrainingHyperparams.toLoraParams(
    maxSteps: Int = epochs * 100,
    warmupSteps: Int = (maxSteps * 0.1f).toInt().coerceAtLeast(1),
): LoraParams = LoraParams(
    rank = loraR,
    alpha = loraAlpha.toFloat(),
    learningRate = learningRate,
    batchSize = batchSize,
    epochs = epochs,
    maxSteps = maxSteps.coerceAtLeast(1),
    warmupSteps = warmupSteps,
)

fun NativeMetrics.toDomain(runId: Long, elapsedMs: Long = 0L, etaMs: Long = -1L): DomainMetrics =
    DomainMetrics(
        runId = runId,
        epoch = epoch,
        step = step,
        totalSteps = totalSteps,
        loss = loss,
        learningRate = learningRate,
        tokensPerSecond = tokensPerSec,
        elapsedMs = elapsedMs,
        etaMs = etaMs,
    )
