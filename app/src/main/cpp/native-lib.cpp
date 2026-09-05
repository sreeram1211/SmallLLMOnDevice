/**
 * ============================================================================
 * STUB TRAINING LOOP — PocketSloth native JNI
 * ============================================================================
 * This file intentionally SIMULATES LoRA fine-tuning progress for UI wiring.
 * Replace the loop body with real llama.cpp LoRA train steps when ready.
 * See training_engine.h and CMakeLists.txt for integration guidelines.
 * ============================================================================
 */

#include "training_engine.h"

#include <android/log.h>
#include <jni.h>

#include <chrono>
#include <cmath>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <thread>
#include <unistd.h>

#define LOG_TAG "PocketSlothNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

JavaVM* g_vm = nullptr;
jclass g_bridge_class = nullptr;
jmethodID g_on_progress = nullptr;

float sample_memory_mb_impl() {
    // Prefer VmRSS from /proc/self/status (works on Android).
    std::ifstream in("/proc/self/status");
    std::string line;
    while (std::getline(in, line)) {
        if (line.rfind("VmRSS:", 0) == 0) {
            // VmRSS:   12345 kB
            std::istringstream iss(line.substr(6));
            long kb = 0;
            iss >> kb;
            return static_cast<float>(kb) / 1024.f;
        }
    }
    return 128.f; // fallback for STUB
}

void emit_progress(
    JNIEnv* env,
    jobject bridge,
    int step,
    int total_steps,
    int epoch,
    float loss,
    float lr,
    float memory_mb,
    float tokens_per_sec,
    TrainStatus status,
    const char* message
) {
    if (!env || !bridge || !g_on_progress) return;
    jstring jmsg = env->NewStringUTF(message ? message : "");
    env->CallVoidMethod(
        bridge,
        g_on_progress,
        step,
        total_steps,
        epoch,
        loss,
        lr,
        memory_mb,
        tokens_per_sec,
        static_cast<jint>(status),
        jmsg
    );
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    env->DeleteLocalRef(jmsg);
}

JNIEnv* attach_env(bool* attached) {
    *attached = false;
    if (!g_vm) return nullptr;
    JNIEnv* env = nullptr;
    jint rs = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rs == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != 0) return nullptr;
        *attached = true;
    } else if (rs != JNI_OK) {
        return nullptr;
    }
    return env;
}

/**
 * STUB: synthetic loss curve + paced callbacks (~100ms) for max_steps.
 *
 * REAL llama.cpp LoRA (outline):
 *   - llama_load_model_from_file(model_path)
 *   - load / allocate LoRA tensors (rank, alpha) for target modules
 *   - for each batch from train_data_path: forward, loss, backward, adamw step
 *   - export adapter GGUF periodically / on complete
 */
void stub_training_loop(TrainingSession* session) {
    bool attached = false;
    JNIEnv* env = attach_env(&attached);
    if (!env || !session->bridge_global) {
        LOGE("STUB loop: failed to attach JNI");
        session->running = false;
        return;
    }

    jobject bridge = static_cast<jobject>(session->bridge_global);
    const int total = session->params.max_steps > 0 ? session->params.max_steps : 100;
    const float base_lr = session->params.learning_rate;

    emit_progress(env, bridge, 0, total, 0, 0.f, 0.f,
                  sample_memory_mb_impl(), 0.f,
                  TrainStatus::INITIALIZING, "STUB: initializing session");

    std::this_thread::sleep_for(std::chrono::milliseconds(150));

    if (session->cancel_requested.load()) {
        emit_progress(env, bridge, 0, total, 0, 0.f, 0.f,
                      sample_memory_mb_impl(), 0.f,
                      TrainStatus::CANCELLED, "STUB: cancelled before start");
        session->running = false;
        if (attached) g_vm->DetachCurrentThread();
        return;
    }

    LOGI("STUB training start steps=%d lr=%f rank=%d", total, base_lr, session->params.rank);

    for (int step = 1; step <= total; ++step) {
        if (session->cancel_requested.load()) {
            emit_progress(env, bridge, step - 1, total, 0,
                          0.f, base_lr, sample_memory_mb_impl(), 0.f,
                          TrainStatus::CANCELLED, "STUB: cancelled");
            break;
        }

        // Synthetic decaying loss + cosine-ish LR schedule with warmup.
        const float t = static_cast<float>(step) / static_cast<float>(total);
        float lr = base_lr;
        if (session->params.warmup_steps > 0 && step <= session->params.warmup_steps) {
            lr = base_lr * (static_cast<float>(step) / session->params.warmup_steps);
        } else {
            lr = base_lr * 0.5f * (1.f + std::cos(3.14159265f * t));
        }
        const float loss = 2.5f * std::exp(-2.2f * t) + 0.08f + 0.02f * std::sin(step * 0.37f);
        const float tps = 80.f + 40.f * t + 5.f * std::sin(step * 0.2f);
        const float mem = sample_memory_mb_impl() + 32.f * t;

        emit_progress(env, bridge, step, total, 0, loss, lr, mem, tps,
                      TrainStatus::RUNNING, "STUB: training step");

        // ~100ms cadence so UI can animate without racing.
        std::this_thread::sleep_for(std::chrono::milliseconds(100));

        if (step == total) {
            emit_progress(env, bridge, step, total, 0, loss, lr, mem, tps,
                          TrainStatus::COMPLETED, "STUB: epoch complete");
        }
    }

    session->running = false;
    if (attached) g_vm->DetachCurrentThread();
    LOGI("STUB training finished");
}

int extract_int(const char* json, const char* key, int def) {
    if (!json || !key) return def;
    std::string needle = std::string("\"") + key + "\"";
    const char* p = std::strstr(json, needle.c_str());
    if (!p) return def;
    p = std::strchr(p + needle.size(), ':');
    if (!p) return def;
    return static_cast<int>(std::strtol(p + 1, nullptr, 10));
}

float extract_float(const char* json, const char* key, float def) {
    if (!json || !key) return def;
    std::string needle = std::string("\"") + key + "\"";
    const char* p = std::strstr(json, needle.c_str());
    if (!p) return def;
    p = std::strchr(p + needle.size(), ':');
    if (!p) return def;
    return static_cast<float>(std::strtod(p + 1, nullptr));
}

int64_t extract_long(const char* json, const char* key, int64_t def) {
    if (!json || !key) return def;
    std::string needle = std::string("\"") + key + "\"";
    const char* p = std::strstr(json, needle.c_str());
    if (!p) return def;
    p = std::strchr(p + needle.size(), ':');
    if (!p) return def;
    return static_cast<int64_t>(std::strtoll(p + 1, nullptr, 10));
}

} // namespace

LoraHyperParams parse_lora_params_json(const char* json) {
    LoraHyperParams p;
    if (!json) return p;
    p.rank = extract_int(json, "rank", p.rank);
    p.alpha = extract_float(json, "alpha", p.alpha);
    p.dropout = extract_float(json, "dropout", p.dropout);
    p.learning_rate = extract_float(json, "learning_rate", p.learning_rate);
    p.batch_size = extract_int(json, "batch_size", p.batch_size);
    p.epochs = extract_int(json, "epochs", p.epochs);
    p.max_steps = extract_int(json, "max_steps", p.max_steps);
    p.warmup_steps = extract_int(json, "warmup_steps", p.warmup_steps);
    p.weight_decay = extract_float(json, "weight_decay", p.weight_decay);
    p.seed = extract_long(json, "seed", p.seed);
    return p;
}

float sample_memory_mb() {
    return sample_memory_mb_impl();
}

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass local = env->FindClass("com/pocketsloth/app/native/TrainingBridge");
    if (!local) {
        LOGE("FindClass TrainingBridge failed");
        return JNI_ERR;
    }
    g_bridge_class = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    g_on_progress = env->GetMethodID(
        g_bridge_class,
        "onNativeProgress",
        "(IIIFFFFILjava/lang/String;)V"
    );
    if (!g_on_progress) {
        LOGE("GetMethodID onNativeProgress failed");
        return JNI_ERR;
    }
    LOGI("JNI_OnLoad OK (STUB training engine)");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) {
        if (g_bridge_class) {
            env->DeleteGlobalRef(g_bridge_class);
            g_bridge_class = nullptr;
        }
    }
    g_on_progress = nullptr;
    g_vm = nullptr;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocketsloth_app_native_NativeLoRAEngine_nativeIsStubMode(
    JNIEnv*, jobject
) {
    return JNI_TRUE; // flip to FALSE when real llama.cpp path is wired
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_pocketsloth_app_native_NativeLoRAEngine_nativeInitTrainingSession(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring jModelPath,
    jstring jTrainDataPath,
    jstring jLoraParamsJSON,
    jobject bridge
) {
    if (!bridge) {
        LOGE("bridge is null");
        return 0;
    }

    const char* model = env->GetStringUTFChars(jModelPath, nullptr);
    const char* data = env->GetStringUTFChars(jTrainDataPath, nullptr);
    const char* json = env->GetStringUTFChars(jLoraParamsJSON, nullptr);

    auto* session = new TrainingSession();
    session->model_path = model ? model : "";
    session->train_data_path = data ? data : "";
    session->params = parse_lora_params_json(json);
    session->bridge_global = env->NewGlobalRef(bridge);

    if (model) env->ReleaseStringUTFChars(jModelPath, model);
    if (data) env->ReleaseStringUTFChars(jTrainDataPath, data);
    if (json) env->ReleaseStringUTFChars(jLoraParamsJSON, json);

    LOGI("STUB session created model=%s data=%s steps=%d",
         session->model_path.c_str(),
         session->train_data_path.c_str(),
         session->params.max_steps);

    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pocketsloth_app_native_NativeLoRAEngine_nativeStartEpoch(
    JNIEnv*,
    jobject /*thiz*/,
    jlong handle
) {
    auto* session = reinterpret_cast<TrainingSession*>(handle);
    if (!session) return JNI_FALSE;
    if (session->running.exchange(true)) {
        LOGW("epoch already running");
        return JNI_FALSE;
    }
    session->cancel_requested = false;
    std::thread(stub_training_loop, session).detach();
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocketsloth_app_native_NativeLoRAEngine_nativeCancelTraining(
    JNIEnv*,
    jobject /*thiz*/,
    jlong handle
) {
    auto* session = reinterpret_cast<TrainingSession*>(handle);
    if (!session) return;
    session->cancel_requested = true;
    LOGI("STUB cancel requested");
}

extern "C" JNIEXPORT void JNICALL
Java_com_pocketsloth_app_native_NativeLoRAEngine_nativeDestroySession(
    JNIEnv* env,
    jobject /*thiz*/,
    jlong handle
) {
    auto* session = reinterpret_cast<TrainingSession*>(handle);
    if (!session) return;
    session->cancel_requested = true;
    // Best-effort wait for stub thread to notice cancel.
    for (int i = 0; i < 50 && session->running.load(); ++i) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    if (session->bridge_global) {
        env->DeleteGlobalRef(static_cast<jobject>(session->bridge_global));
        session->bridge_global = nullptr;
    }
    delete session;
    LOGI("STUB session destroyed");
}
