/**
 * ============================================================================
 * PocketSloth native JNI — HybridEngine (llama.cpp) with STUB fallback
 * ============================================================================
 * JNI API (stable):
 *   nativeIsStubMode / nativeInitTrainingSession / nativeStartEpoch /
 *   nativeCancelTraining / nativeDestroySession
 * Progress: TrainingBridge.onNativeProgress(...)
 * ============================================================================
 */

#include "hybrid_engine.h"
#include "training_engine.h"

#include <android/log.h>
#include <jni.h>

#include <chrono>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <thread>

#define LOG_TAG "PocketSlothNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

JavaVM* g_vm = nullptr;
jclass g_bridge_class = nullptr;
jmethodID g_on_progress = nullptr;
// True only after a session successfully loaded a GGUF with linked llama.
bool g_real_model_loaded = false;

float sample_memory_mb_impl() {
    std::ifstream in("/proc/self/status");
    std::string line;
    while (std::getline(in, line)) {
        if (line.rfind("VmRSS:", 0) == 0) {
            std::istringstream iss(line.substr(6));
            long kb = 0;
            iss >> kb;
            return static_cast<float>(kb) / 1024.f;
        }
    }
    return 128.f;
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

void training_worker(TrainingSession* session) {
    bool attached = false;
    JNIEnv* env = attach_env(&attached);
    if (!env || !session->bridge_global) {
        LOGE("training worker: failed to attach JNI");
        session->running = false;
        return;
    }

    jobject bridge = static_cast<jobject>(session->bridge_global);
    ProgressFn emit = [env, bridge](
        int step, int total, int epoch,
        float loss, float lr, float mem, float tps,
        TrainStatus status, const char* message
    ) {
        emit_progress(env, bridge, step, total, epoch, loss, lr, mem, tps, status, message);
    };

    HybridEngine::runEpoch(session, emit);

    session->running = false;
    if (attached) g_vm->DetachCurrentThread();
    LOGI("training worker finished (llama_ready=%d stub_fallback=%d)",
         session->llama_ready ? 1 : 0,
         session->used_stub_fallback ? 1 : 0);
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
    LOGI("JNI_OnLoad OK (llama_compiled=%d)", HybridEngine::llamaCompiledIn() ? 1 : 0);
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
    // false only when real lib linked AND a model has successfully loaded.
    // Otherwise stub (compiled without llama, or GGUF load failed → stub loop).
    if (!HybridEngine::llamaCompiledIn()) return JNI_TRUE;
    return g_real_model_loaded ? JNI_FALSE : JNI_TRUE;
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

    if (HybridEngine::llamaCompiledIn()) {
        if (HybridEngine::tryInitLlama(session)) {
            g_real_model_loaded = true;
        } else {
            LOGW("llama init failed — session will use STUB fallback");
            session->used_stub_fallback = true;
            // Keep g_real_model_loaded if a prior session succeeded; else stays false.
        }
    } else {
        session->used_stub_fallback = true;
    }

    LOGI("session created model=%s data=%s steps=%d llama_ready=%d",
         session->model_path.c_str(),
         session->train_data_path.c_str(),
         session->params.max_steps,
         session->llama_ready ? 1 : 0);

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
    std::thread(training_worker, session).detach();
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
    LOGI("cancel requested");
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
    for (int i = 0; i < 50 && session->running.load(); ++i) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    HybridEngine::shutdownLlama(session);
    if (session->bridge_global) {
        env->DeleteGlobalRef(static_cast<jobject>(session->bridge_global));
        session->bridge_global = nullptr;
    }
    delete session;
    LOGI("session destroyed");
}
