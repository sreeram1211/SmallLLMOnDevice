/**
 * PocketSloth training engine — header for the STUB LoRA loop.
 *
 * ============================================================================
 * REAL llama.cpp LoRA INTEGRATION (replace STUB bodies in native-lib.cpp):
 * ============================================================================
 * 1. Vendor llama.cpp under app/src/main/cpp/third_party/llama.cpp
 * 2. Expose ggml / llama train APIs (gguf load, LoRA adapters, optimizer step).
 * 3. Map LoraParams JSON -> llama_lora / train hyperparams (rank, alpha, lr, …).
 * 4. Drive tokens from train_data_path (jsonl) through llama_batch / train loop.
 * 5. After each optimizer step, call emit_progress(...) with real loss / t/s / RSS.
 * 6. Persist adapter weights (GGUF LoRA export) on COMPLETED.
 *
 * This STUB intentionally simulates timing and metrics so the UI/JNI path can
 * be developed without a full NDK llama.cpp build.
 */
#pragma once

#include <atomic>
#include <cstdint>
#include <string>

struct LoraHyperParams {
    int rank = 8;
    float alpha = 16.f;
    float dropout = 0.05f;
    float learning_rate = 2e-4f;
    int batch_size = 1;
    int epochs = 1;
    int max_steps = 100;
    int warmup_steps = 10;
    float weight_decay = 0.01f;
    int64_t seed = 42;
};

enum class TrainStatus : int {
    IDLE = 0,
    INITIALIZING = 1,
    RUNNING = 2,
    COMPLETED = 3,
    CANCELLED = 4,
    ERROR = 5
};

struct TrainingSession {
    std::string model_path;
    std::string train_data_path;
    LoraHyperParams params;
    std::atomic<bool> cancel_requested{false};
    std::atomic<bool> running{false};
    void* bridge_global = nullptr; // jobject GlobalRef to TrainingBridge
};

/** Parse minimal JSON fields we care about (no full JSON lib in STUB). */
LoraHyperParams parse_lora_params_json(const char* json);

/** Approximate process RSS in MiB (Linux /proc; falls back on Android). */
float sample_memory_mb();

