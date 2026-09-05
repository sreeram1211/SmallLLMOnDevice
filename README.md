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
                            │ JNI
┌───────────────────────────▼─────────────────────────────────┐
│  libpocketsloth_native.so  (CMake, arm64-v8a)               │
│  native-lib.cpp  — STUB training loop (~100ms callbacks)    │
│  training_engine.h — session + hyperparams                  │
└─────────────────────────────────────────────────────────────┘
```

- **minSdk 29 / target & compile 34**, ABI filtered to **arm64-v8a**.
- **NDK + CMake** `externalNativeBuild` builds `pocketsloth_native`.
- Progress fields: `step`, `loss`, `lr`, `memory_mb`, `tokens_per_sec` via `TrainingBridge.onNativeProgress`.

## Project layout

| Path | Role |
|------|------|
| `app/.../MainActivity.kt` | Theme + hosts `PocketSlothNavHost` |
| `app/.../presentation/navigation/*` | Routes, bottom bar, NavHost wiring |
| `app/.../presentation/screens/*` | Full Compose screens (Dataset, Config, Dashboard+Canvas loss, Chat A/B) |
| `app/.../presentation/viewmodel/*` | Screen VMs (in-memory / sim gateways until DI) |
| `app/.../domain/*` | Models + repository contracts |
| `app/.../data/*` | Room, DataStore, importers, formatters, mappers |
| `app/.../service/*` | `FineTuningService`, `ThermalMonitor` |
| `app/.../native/*` | JNI façade + metrics |
| `app/.../cpp/*` | STUB engine + CMake |

## Screens

- **Home** — workflow hub
- **Datasets** — Instruction / Input / Output builder + import
- **Training config** — LoRA rank/alpha, LR, batch, context, model picker
- **Training dashboard** — live metrics + Canvas loss curve, pause/stop
- **Chat playground** — Base vs fine-tuned adapter A/B toggle

## Build

Open the `PocketSloth` folder in Android Studio (Hedgehog+ / AGP 8.5), sync Gradle, and run on an **arm64** device/emulator with NDK **26.1+** installed.

```bash
./gradlew :app:assembleDebug
```

Gradle wrapper (`gradlew`, `gradle-wrapper.jar`, properties for **Gradle 8.7**) is checked in. Root `build.gradle.kts` applies KSP `apply false` matching the app module.

## How to plug in real llama.cpp

The C++ loop is clearly marked **STUB**. To swap in real LoRA training:

1. **Vendor sources** under `app/src/main/cpp/third_party/llama.cpp` (git submodule recommended).
2. **CMake** — extend `CMakeLists.txt` to add_subdirectory and link `llama` + `ggml` into `pocketsloth_native`.
3. **JNI contract** — keep emitting the same `onNativeProgress` signature so Compose UI stays unchanged.
4. **Session API** — map `LoraParams` JSON onto llama LoRA / train structs.
5. **Flip stub flag** — make `nativeIsStubMode()` return `false` once the real path is default.
6. **Export** — write LoRA adapter GGUF on `COMPLETED` for the chat playground to load.

See comments in `app/src/main/cpp/CMakeLists.txt` and `training_engine.h`.

## Status / known gaps

- Native training is a **simulation** until llama.cpp is linked.
- ViewModels currently use in-memory / local simulation gateways; wire to Room + `FineTuningService` via DI for production runs.
- Chat inference streams are stubbed pending real GGUF load / generate path.

## License / status

Prototype scaffold. Native training is a **simulation** until llama.cpp is linked.
