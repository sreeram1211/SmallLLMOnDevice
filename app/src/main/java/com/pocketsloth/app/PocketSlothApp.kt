package com.pocketsloth.app

import android.app.Application
import android.util.Log
import com.pocketsloth.app.native.NativeLoRAEngine

/**
 * Application entry point. Loads the native training library once and keeps
 * a process-scoped [NativeLoRAEngine] handle for UI / ViewModel layers.
 */
class PocketSlothApp : Application() {

    lateinit var loraEngine: NativeLoRAEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            NativeLoRAEngine.loadLibrary()
            loraEngine = NativeLoRAEngine()
            Log.i(TAG, "Native LoRA engine ready (stub mode=${loraEngine.isStubMode()})")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize native LoRA engine", t)
            // Still construct a Kotlin-side engine so callers can observe the error path.
            loraEngine = NativeLoRAEngine(libraryLoaded = false)
        }
    }

    companion object {
        private const val TAG = "PocketSlothApp"

        @Volatile
        private var instance: PocketSlothApp? = null

        fun get(): PocketSlothApp =
            instance ?: error("PocketSlothApp not initialized")
    }
}
