/**
 * HybridEngine — llama.cpp when LLAMA_AVAILABLE, else STUB.
 *
 * Keeps the TrainingSession / progress callback contract used by JNI.
 *
 * Real path (best-effort until full on-device LoRA is practical):
 *   - load GGUF via llama_model_load_from_file
 *   - create context, report RSS / model size
 *   - run forward-pass throughput probes (llama_decode) as training-compatible metrics
 *   - hooks + TODOs for llama_opt_* finetune and tools/export-lora
 *
 * If the model fails to load, falls back to the stub loop so the app never hard-crashes.
 */
#pragma once

#include "training_engine.h"

#include <jni.h>

#include <functional>

/** Progress emitter supplied by native-lib (JNI attach + TrainingBridge). */
using ProgressFn = std::function<void(
    int step,
    int total_steps,
    int epoch,
    float loss,
    float lr,
    float memory_mb,
    float tokens_per_sec,
    TrainStatus status,
    const char* message)>;

struct HybridEngine {
    /** True when compiled with LLAMA_AVAILABLE=1. */
    static bool llamaCompiledIn();

    /**
     * Attempt to initialize llama from session->model_path.
     * On success sets session->llama_ready and returns true.
     * On failure leaves session in stub-safe state and returns false.
     */
    static bool tryInitLlama(TrainingSession* session);

    /** Release llama model/context held on the session (no-op if stub). */
    static void shutdownLlama(TrainingSession* session);

    /**
     * Worker-thread entry: hybrid training-compatible loop.
     * Uses real forward-pass metrics when llama_ready; otherwise stub curve.
     */
    static void runEpoch(TrainingSession* session, const ProgressFn& emit);
};
