# PocketSloth

On-device LoRA fine-tuning playground for Android (`com.pocketsloth.app`).

## Architecture overview

```
┌─────────────────────────────────────────────────────────────┐
│  Compose UI (Material3)                                     │
│  Home → Datasets → Config → Training → Chat                 │
│  presentation.screens.* + PocketSlothNavHost                │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│  ViewModels + Domain / Data (Room, DataStore, importers)    │
│  FineTuningService (FGS) + ThermalMonitor                   │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│  Kotlin native façade                                       │
│  NativeLoRAEngine  →  TrainingBridge  →  TrainingMetrics    │
│  LoraParams (JSON)                                          │
└───────────────────────────┬─────────────────────────────────┘
                            │ JNI (stable API)
┌───────────────────────────▼─────────────────────────────────┐
│  libpocketsloth_native.so  (CMake, arm64-v8a + NEON)        │
│  HybridEngine — llama.cpp when vendored + GGUF loads        │
│  STUB fallback if submodule missing or model load fails     │
└─────────────────────────────────────────────────────────────┘
```

- **minSdk 29 / target & compile 34**, ABI filtered to **arm64-v8a**.
- **NDK + CMake** `externalNativeBuild` builds `pocketsloth_native` and (when present) static `llama` / `ggml`.
- Progress fields: `step`, `loss`, `lr`, `memory_mb`, `tokens_per_sec` via `TrainingBridge.onNativeProgress`.

## Project layout

| Path | Role |
|------|------|
| `app/.../MainActivity.kt` | Theme + hosts `PocketSlothNavHost` |
| `app/.../presentation/navigation/*` | Routes, bottom bar, NavHost wiring |
| `app/.../presentation/screens/*` | Full Compose screens |
| `app/.../presentation/viewmodel/*` | Screen VMs |
| `app/.../domain/*` | Models + repository contracts |
| `app/.../data/*` | Room, DataStore, importers, formatters |
| `app/.../service/*` | `FineTuningService`, `ThermalMonitor` |
| `app/.../native/*` | JNI façade + metrics |
| `app/.../cpp/native-lib.cpp` | JNI entrypoints (stable) |
| `app/.../cpp/hybrid_engine.*` | Hybrid llama / STUB training path |
| `app/.../cpp/third_party/llama.cpp` | **git submodule** → ggml-org/llama.cpp |

## Screens

- **Home** — workflow hub
- **Datasets** — Instruction / Input / Output builder + import
- **Training config** — LoRA rank/alpha, LR, batch, context, model picker
- **Training dashboard** — live metrics + Canvas loss curve, pause/stop
- **Chat playground** — Base vs fine-tuned adapter A/B toggle

## Clone & submodule init

llama.cpp is **not** vendored as a full tree in git (size). It is a submodule:

```bash
git clone https://github.com/sreeram1211/SmallLLMOnDevice.git
cd SmallLLMOnDevice
git submodule update --init --recursive
# equivalent path:
#   git submodule update --init --recursive app/src/main/cpp/third_party/llama.cpp
```

`.gitmodules` points at `https://github.com/ggml-org/llama.cpp.git` under
`app/src/main/cpp/third_party/llama.cpp`.

If the submodule is missing, CMake still builds a **STUB-only** `pocketsloth_native`
so the app compiles and the UI keeps working.

## Build

Open the project in Android Studio (Hedgehog+ / AGP 8.5), sync Gradle, and run on an
**arm64** device/emulator with NDK **26.1+** installed.

```bash
./gradlew :app:assembleDebug
```

Gradle wrapper (`gradlew`, `gradle-wrapper.jar`, properties for **Gradle 8.7**) is checked in.

### Native / CMake notes (arm64-v8a + NEON)

Defaults in `app/src/main/cpp/CMakeLists.txt`:

| Flag | Default | Purpose |
|------|---------|---------|
| `POCKETSLOTH_USE_LLAMA` | ON | `add_subdirectory` llama.cpp when present |
| `GGML_NATIVE` | OFF | Avoid host-CPU tuning when cross-compiling |
| `GGML_OPENMP` / `POCKETSLOTH_GGML_OPENMP` | OFF | Smaller / simpler NDK link |
| `POCKETSLOTH_GGML_VULKAN` | OFF | Optional GPU; enable via Gradle cmake args |
| `POCKETSLOTH_GGML_OPENCL` | OFF | Optional / experimental Adreno path |
| `LLAMA_BUILD_*` tests/tools/examples | OFF | Keep APK native size down |
| `BUILD_SHARED_LIBS` | OFF | Static `llama`/`ggml` into `pocketsloth_native` |

Gradle already passes `-DANDROID_ARM_NEON=TRUE` and `abiFilters += "arm64-v8a"`.

To experiment with Vulkan later:

```kotlin
// app/build.gradle.kts → defaultConfig.externalNativeBuild.cmake.arguments
"-DPOCKETSLOTH_GGML_VULKAN=ON"
```

(and add Vulkan `uses-feature` in the manifest if you hard-require GPU).

### HybridEngine behavior

1. **llama linked + GGUF loads** → `nativeIsStubMode() == false`; session runs a real
   forward-pass throughput probe (`llama_decode`) and reports RSS / model size.
2. **llama linked but GGUF missing/unloadable** → falls back to the STUB progress loop
   (no crash); `nativeIsStubMode()` stays `true` until a successful load.
3. **submodule absent** → STUB-only build; `nativeIsStubMode() == true`.

JNI API remains: `initTrainingSession`, `startEpoch`, `cancelTraining`,
`onNativeProgress` callbacks.

### LoRA finetune roadmap (TODOs in `hybrid_engine.cpp`)

Full on-device LoRA train is still heavy. Hooks point at upstream:

- `third_party/llama.cpp/examples/training/finetune.cpp` — `llama_opt_init` / `llama_opt_epoch`
- `third_party/llama.cpp/tools/export-lora` — adapter GGUF export for chat A/B
- Inference apply: `llama_adapter_lora_init` + `llama_set_adapters_lora`

## Status / known gaps

- Hybrid path = **real GGUF load + forward metrics**; optimizer / LoRA weight updates are scaffolded with TODOs (upstream train APIs are WIP and memory-heavy on phone).
- ViewModels may still use in-memory gateways until DI is fully wired.
- Chat generation beyond the native probe is still product-level work.

## License / status

Prototype scaffold. Native library links **llama.cpp** (MIT) when the submodule is initialized.
