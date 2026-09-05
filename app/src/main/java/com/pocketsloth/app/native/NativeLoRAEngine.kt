package com.pocketsloth.app.native

import android.util.Log

/**
 * Kotlin façade over `libpocketsloth_native.so`.
 *
 * JNI contract (see native-lib.cpp):
 * - [nativeInitTrainingSession]
 * - [nativeStartEpoch]
 * - [nativeCancelTraining]
 * - [nativeDestroySession]
 * - [nativeIsStubMode]
 *
 * Progress is delivered asynchronously via [TrainingBridge.onNativeProgress].
 */
class NativeLoRAEngine(
    private val libraryLoaded: Boolean = true,
) {
    private val bridge = TrainingBridge()

    @Volatile
    private var sessionHandle: Long = 0L

    fun isLibraryLoaded(): Boolean = libraryLoaded

    fun isStubMode(): Boolean =
        if (libraryLoaded) {
            runCatching { nativeIsStubMode() }.getOrDefault(true)
        } else {
            true
        }

    fun addProgressListener(listener: TrainingBridge.ProgressListener) {
        bridge.addListener(listener)
    }

    fun removeProgressListener(listener: TrainingBridge.ProgressListener) {
        bridge.removeListener(listener)
    }

    fun clearProgressListeners() {
        bridge.clearListeners()
    }

    /**
     * Creates a native training session.
     * @return true if a non-zero session handle was allocated
     */
    @Synchronized
    fun initSession(
        modelPath: String,
        trainDataPath: String,
        params: LoraParams,
    ): Boolean {
        check(libraryLoaded) { "Native library not loaded" }
        destroySession()
        val handle = nativeInitTrainingSession(
            modelPath = modelPath,
            trainDataPath = trainDataPath,
            loraParamsJSON = params.toJson(),
            bridge = bridge,
        )
        sessionHandle = handle
        return handle != 0L
    }

    /** Starts (or restarts) an asynchronous training epoch on a worker thread. */
    @Synchronized
    fun startEpoch(): Boolean {
        check(libraryLoaded) { "Native library not loaded" }
        val handle = sessionHandle
        check(handle != 0L) { "Call initSession() before startEpoch()" }
        return nativeStartEpoch(handle)
    }

    /** Requests cancellation of the in-flight epoch. */
    @Synchronized
    fun cancelTraining() {
        val handle = sessionHandle
        if (handle != 0L && libraryLoaded) {
            nativeCancelTraining(handle)
        }
    }

    /** Destroys the native session and releases the bridge global ref. */
    @Synchronized
    fun destroySession() {
        val handle = sessionHandle
        if (handle != 0L && libraryLoaded) {
            runCatching { nativeDestroySession(handle) }
                .onFailure { Log.w(TAG, "nativeDestroySession failed", it) }
        }
        sessionHandle = 0L
    }

    fun hasActiveSession(): Boolean = sessionHandle != 0L

    private external fun nativeIsStubMode(): Boolean

    private external fun nativeInitTrainingSession(
        modelPath: String,
        trainDataPath: String,
        loraParamsJSON: String,
        bridge: TrainingBridge,
    ): Long

    private external fun nativeStartEpoch(handle: Long): Boolean

    private external fun nativeCancelTraining(handle: Long)

    private external fun nativeDestroySession(handle: Long)

    companion object {
        private const val TAG = "NativeLoRAEngine"
        private const val LIB_NAME = "pocketsloth_native"

        @Volatile
        private var loaded: Boolean = false

        /** Idempotent load of [LIB_NAME]. */
        @JvmStatic
        fun loadLibrary() {
            if (!loaded) {
                synchronized(this) {
                    if (!loaded) {
                        System.loadLibrary(LIB_NAME)
                        loaded = true
                    }
                }
            }
        }
    }
}
