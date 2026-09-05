/**
 * HybridEngine implementation.
 *
 * When LLAMA_AVAILABLE:
 *   - Load GGUF, create context
 *   - Emit memory / model-size metrics
 *   - Run llama_decode forward-pass probes as tokens/sec
 *   - Structure LoRA finetune hooks (TODO -> examples/training + tools/export-lora)
 *
 * Otherwise / on load failure: synthetic stub curve (same cadence as before).
 */

#include "hybrid_engine.h"

#include <android/log.h>

#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <mutex>
#include <thread>
#include <vector>

#define LOG_TAG "PocketSlothHybrid"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if defined(LLAMA_AVAILABLE) && LLAMA_AVAILABLE
#include "llama.h"
#endif

namespace {

void stub_loop(TrainingSession* session, const ProgressFn& emit) {
    session->used_stub_fallback = true;
    const int total = session->params.max_steps > 0 ? session->params.max_steps : 100;
    const float base_lr = session->params.learning_rate;

    emit(0, total, 0, 0.f, 0.f, sample_memory_mb(), 0.f,
         TrainStatus::INITIALIZING, "STUB: initializing session");
    std::this_thread::sleep_for(std::chrono::milliseconds(150));

    if (session->cancel_requested.load()) {
        emit(0, total, 0, 0.f, 0.f, sample_memory_mb(), 0.f,
             TrainStatus::CANCELLED, "STUB: cancelled before start");
        return;
    }

    LOGI("STUB training start steps=%d lr=%f rank=%d", total, base_lr, session->params.rank);

    for (int step = 1; step <= total; ++step) {
        if (session->cancel_requested.load()) {
            emit(step - 1, total, 0, 0.f, base_lr, sample_memory_mb(), 0.f,
                 TrainStatus::CANCELLED, "STUB: cancelled");
            break;
        }

        const float t = static_cast<float>(step) / static_cast<float>(total);
        float lr = base_lr;
        if (session->params.warmup_steps > 0 && step <= session->params.warmup_steps) {
            lr = base_lr * (static_cast<float>(step) / session->params.warmup_steps);
        } else {
            lr = base_lr * 0.5f * (1.f + std::cos(3.14159265f * t));
        }
        const float loss = 2.5f * std::exp(-2.2f * t) + 0.08f + 0.02f * std::sin(step * 0.37f);
        const float tps = 80.f + 40.f * t + 5.f * std::sin(step * 0.2f);
        const float mem = sample_memory_mb() + 32.f * t;

        emit(step, total, 0, loss, lr, mem, tps, TrainStatus::RUNNING, "STUB: training step");
        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        if (step == total) {
            emit(step, total, 0, loss, lr, mem, tps,
                 TrainStatus::COMPLETED, "STUB: epoch complete");
        }
    }
}

#if defined(LLAMA_AVAILABLE) && LLAMA_AVAILABLE

bool file_readable(const std::string& path) {
    if (path.empty()) return false;
    std::ifstream in(path, std::ios::binary);
    return in.good();
}

/**
 * Forward-pass throughput probe: tokenize a short prompt and llama_decode
 * one token at a time for `steps` iterations. Returns average tokens/sec.
 *
 * This is a TRAINING-COMPATIBLE scaffold metric (eval forward), not a full
 * optimizer step. Full LoRA train needs llama_opt_* (see TODOs below).
 */
float run_forward_probe(
    llama_model* model,
    llama_context* ctx,
    int steps,
    TrainingSession* session,
    const ProgressFn& emit,
    float base_lr
) {
    const char* probe = "PocketSloth hybrid forward probe for LoRA metrics.";
    const llama_vocab* vocab = llama_model_get_vocab(model);
    std::vector<llama_token> tokens;
    tokens.resize(64);
    int n = llama_tokenize(vocab, probe, static_cast<int32_t>(std::strlen(probe)),
                          tokens.data(), static_cast<int32_t>(tokens.size()),
                          /*add_special=*/true, /*parse_special=*/true);
    if (n < 0) {
        tokens.resize(static_cast<size_t>(-n));
        n = llama_tokenize(vocab, probe, static_cast<int32_t>(std::strlen(probe)),
                           tokens.data(), static_cast<int32_t>(tokens.size()),
                           true, true);
    }
    if (n <= 0) {
        LOGW("tokenize failed; using single BOS token");
        tokens.clear();
        tokens.push_back(llama_vocab_bos(vocab));
        n = 1;
    } else {
        tokens.resize(static_cast<size_t>(n));
    }

    // Prefill once.
    {
        llama_batch batch = llama_batch_get_one(tokens.data(), n);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("prefill llama_decode failed");
            return 0.f;
        }
    }

    double total_tok = 0.0;
    double total_sec = 0.0;
    const int total = steps > 0 ? steps : 100;
    float last_loss = 1.5f;

    for (int step = 1; step <= total; ++step) {
        if (session->cancel_requested.load()) {
            emit(step - 1, total, 0, last_loss, base_lr, sample_memory_mb(),
                 total_sec > 0 ? static_cast<float>(total_tok / total_sec) : 0.f,
                 TrainStatus::CANCELLED, "HYBRID: cancelled");
            break;
        }

        // Take argmax of last logits as next token (greedy micro-step).
        const float* logits = llama_get_logits_ith(ctx, -1);
        const int32_t n_vocab = llama_vocab_n_tokens(vocab);
        llama_token next = 0;
        if (logits && n_vocab > 0) {
            float best = logits[0];
            for (int32_t i = 1; i < n_vocab; ++i) {
                if (logits[i] > best) {
                    best = logits[i];
                    next = i;
                }
            }
            // Synthetic "loss" proxy from negative max logit (UI curve only).
            last_loss = std::fmax(0.05f, -best * 0.01f + 1.2f * std::exp(-0.02f * step));
        }

        auto t0 = std::chrono::steady_clock::now();
        llama_batch batch = llama_batch_get_one(&next, 1);
        const int rc = llama_decode(ctx, batch);
        auto t1 = std::chrono::steady_clock::now();
        if (rc != 0) {
            LOGE("step decode failed at %d", step);
            emit(step, total, 0, last_loss, base_lr, sample_memory_mb(), 0.f,
                 TrainStatus::ERROR, "HYBRID: decode failed");
            break;
        }

        const double dt = std::chrono::duration<double>(t1 - t0).count();
        total_tok += 1.0;
        total_sec += dt > 1e-9 ? dt : 1e-9;
        const float tps = static_cast<float>(total_tok / total_sec);

        float lr = base_lr;
        if (session->params.warmup_steps > 0 && step <= session->params.warmup_steps) {
            lr = base_lr * (static_cast<float>(step) / session->params.warmup_steps);
        }

        char msg[160];
        std::snprintf(msg, sizeof(msg),
                      "HYBRID: forward step (rank=%d alpha=%.1f) — LoRA train TODO",
                      session->params.rank, session->params.alpha);

        emit(step, total, 0, last_loss, lr, sample_memory_mb(), tps,
             TrainStatus::RUNNING, msg);

        // Light pacing so UI can animate on fast devices.
        if (dt < 0.02) {
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
        }

        if (step == total) {
            emit(step, total, 0, last_loss, lr, sample_memory_mb(), tps,
                 TrainStatus::COMPLETED,
                 "HYBRID: forward probe complete (LoRA finetune not yet applied)");
        }
    }

    return total_sec > 0 ? static_cast<float>(total_tok / total_sec) : 0.f;
}

/*
 * TODO(LoRA finetune): Port the desktop path from
 *   third_party/llama.cpp/examples/training/finetune.cpp
 * using llama_opt_init / llama_opt_epoch + ggml_opt_dataset on train_data_path.
 * On-device full FP32 train is memory-heavy; prefer LoRA-only param filter when
 * upstream exposes stable adapter training APIs.
 *
 * TODO(export-lora): After training, export adapter GGUF via
 *   third_party/llama.cpp/tools/export-lora
 * (or llama_model_save / adapter writers) for the chat playground A/B path.
 *
 * TODO(apply-lora): Inference path can use llama_adapter_lora_init +
 *   llama_set_adapters_lora for applying an existing adapter without training.
 */

void hybrid_loop(TrainingSession* session, const ProgressFn& emit) {
    auto* model = static_cast<llama_model*>(session->llama_model);
    auto* ctx = static_cast<llama_context*>(session->llama_ctx);
    const int total = session->params.max_steps > 0 ? session->params.max_steps : 100;
    const float base_lr = session->params.learning_rate;

    char init_msg[192];
    std::snprintf(init_msg, sizeof(init_msg),
                  "HYBRID: model loaded (%.1f MiB weights), RSS=%.1f MiB",
                  session->model_size_mb, sample_memory_mb());
    emit(0, total, 0, 0.f, 0.f, sample_memory_mb(), 0.f,
         TrainStatus::INITIALIZING, init_msg);

    if (session->cancel_requested.load()) {
        emit(0, total, 0, 0.f, 0.f, sample_memory_mb(), 0.f,
             TrainStatus::CANCELLED, "HYBRID: cancelled before start");
        return;
    }

    LOGI("HYBRID forward probe steps=%d model=%.1fMiB", total, session->model_size_mb);
    run_forward_probe(model, ctx, total, session, emit, base_lr);
}

#endif // LLAMA_AVAILABLE

} // namespace

bool HybridEngine::llamaCompiledIn() {
#if defined(LLAMA_AVAILABLE) && LLAMA_AVAILABLE
    return true;
#else
    return false;
#endif
}

bool HybridEngine::tryInitLlama(TrainingSession* session) {
    if (!session) return false;
    session->llama_ready = false;
    session->used_stub_fallback = false;
    session->model_size_mb = 0.f;

#if defined(LLAMA_AVAILABLE) && LLAMA_AVAILABLE
    if (!file_readable(session->model_path)) {
        LOGW("GGUF not readable at '%s' — will use STUB", session->model_path.c_str());
        return false;
    }

    static std::once_flag backend_once;
    std::call_once(backend_once, []() {
        llama_backend_init();
        LOGI("llama_backend_init OK: %s", llama_print_system_info());
    });

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0; // CPU / NEON path by default on mobile

    llama_model* model = llama_model_load_from_file(session->model_path.c_str(), mparams);
    if (!model) {
        LOGE("llama_model_load_from_file failed: %s", session->model_path.c_str());
        return false;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 512;
    cparams.n_batch = 512;
    cparams.n_ubatch = 256;
    // Keep memory modest for phones; caller can raise later.
    llama_context* ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        LOGE("llama_init_from_model failed");
        llama_model_free(model);
        return false;
    }

    session->llama_model = model;
    session->llama_ctx = ctx;
    session->model_size_mb =
        static_cast<float>(llama_model_size(model)) / (1024.f * 1024.f);
    session->llama_ready = true;

    char desc[128] = {0};
    llama_model_desc(model, desc, sizeof(desc));
    LOGI("Loaded GGUF desc='%s' size=%.1fMiB n_params=%llu RSS=%.1f",
         desc, session->model_size_mb,
         static_cast<unsigned long long>(llama_model_n_params(model)),
         sample_memory_mb());
    return true;
#else
    (void)session;
    return false;
#endif
}

void HybridEngine::shutdownLlama(TrainingSession* session) {
    if (!session) return;
#if defined(LLAMA_AVAILABLE) && LLAMA_AVAILABLE
    if (session->llama_ctx) {
        llama_free(static_cast<llama_context*>(session->llama_ctx));
        session->llama_ctx = nullptr;
    }
    if (session->llama_model) {
        llama_model_free(static_cast<llama_model*>(session->llama_model));
        session->llama_model = nullptr;
    }
#endif
    session->llama_ready = false;
}

void HybridEngine::runEpoch(TrainingSession* session, const ProgressFn& emit) {
    if (!session) return;

#if defined(LLAMA_AVAILABLE) && LLAMA_AVAILABLE
    if (session->llama_ready && session->llama_model && session->llama_ctx) {
        hybrid_loop(session, emit);
        return;
    }
#endif
    stub_loop(session, emit);
}
