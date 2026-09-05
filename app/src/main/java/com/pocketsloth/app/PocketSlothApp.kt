package com.pocketsloth.app

import android.app.Application
import android.util.Log
import com.pocketsloth.app.di.AppContainer
import com.pocketsloth.app.native.NativeLoRAEngine

/**
 * Application entry point. Loads the native training library once and builds
 * the process-scoped [AppContainer] (Room, DataStore, repositories, engine).
 */
class PocketSlothApp : Application() {

    lateinit var container: AppContainer
        private set

    /** Convenience alias for [AppContainer.loraEngine]. */
    val loraEngine: NativeLoRAEngine
        get() = container.loraEngine

    override fun onCreate() {
        super.onCreate()
        instance = this
        val engine = try {
            NativeLoRAEngine.loadLibrary()
            NativeLoRAEngine().also {
                Log.i(TAG, "Native LoRA engine ready (stub mode=${it.isStubMode()})")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize native LoRA engine", t)
            NativeLoRAEngine(libraryLoaded = false)
        }
        container = AppContainer(context = this, loraEngine = engine)
    }

    companion object {
        private const val TAG = "PocketSlothApp"

        @Volatile
        private var instance: PocketSlothApp? = null

        fun get(): PocketSlothApp =
            instance ?: error("PocketSlothApp not initialized")
    }
}
