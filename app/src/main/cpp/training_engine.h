/**
 * PocketSloth training engine — session + hyperparams shared by STUB and HybridEngine.
 *
 * Real llama.cpp LoRA path (see hybrid_engine.cpp):
 *  1. Vendor under app/src/main/cpp/third_party/llama.cpp (git submodule).
 *  2. CMake defines LLAMA_AVAILABLE and links llama/ggml into pocketsloth_native.
 *  3. HybridEngine loads GGUF, reports memory, runs forward-pass throughput metrics.
 *  4. TODO: wire llama_opt_init / llama_opt_epoch (examples/training/finetune.cpp)
 *     and tools/export-lora for adapter GGUF export.
 *  5. Keep emit_progress / onNativeProgress JNI contract stable for Compose UI.
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

    // Hybrid / llama state (opaque pointers; owned by HybridEngine).
    bool llama_ready = false;
    bool used_stub_fallback = false;
    void* llama_model = nullptr;   // struct llama_model*
    void* llama_ctx = nullptr;     // struct llama_context*
    float model_size_mb = 0.f;
};

/** Parse minimal JSON fields we care about (no full JSON lib required). */
LoraHyperParams parse_lora_params_json(const char* json);

/** Approximate process RSS in MiB (Linux /proc; works on Android). */
float sample_memory_mb();
