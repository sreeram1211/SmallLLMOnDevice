package com.pocketsloth.app.native

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Thin JNI-facing bridge that fans out native progress callbacks to Kotlin listeners.
 * Called from the native training thread — listeners must be thread-safe or post to main.
 */
class TrainingBridge internal constructor() {

    fun interface ProgressListener {
        fun onProgress(metrics: TrainingMetrics)
    }

    private val listeners = CopyOnWriteArrayList<ProgressListener>()

    fun addListener(listener: ProgressListener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: ProgressListener) {
        listeners.remove(listener)
    }

    fun clearListeners() {
        listeners.clear()
    }

    /**
     * Invoked from JNI (native-lib.cpp) on the training worker thread.
     * Signature must stay in sync with the native RegisterNatives / FindClass path.
     */
    @Suppress("unused") // called via JNI
    fun onNativeProgress(
        step: Int,
        totalSteps: Int,
        epoch: Int,
        loss: Float,
        learningRate: Float,
        memoryMb: Float,
        tokensPerSec: Float,
        statusOrdinal: Int,
        message: String
    ) {
        val status = TrainingMetrics.Status.entries.getOrElse(statusOrdinal) {
            TrainingMetrics.Status.RUNNING
        }
        val metrics = TrainingMetrics(
            step = step,
            totalSteps = totalSteps,
            epoch = epoch,
            loss = loss,
            learningRate = learningRate,
            memoryMb = memoryMb,
            tokensPerSec = tokensPerSec,
            status = status,
            message = message
        )
        for (listener in listeners) {
            try {
                listener.onProgress(metrics)
            } catch (t: Throwable) {
                Log.w(TAG, "Progress listener threw", t)
            }
        }
    }

    companion object {
        private const val TAG = "TrainingBridge"
    }
}
