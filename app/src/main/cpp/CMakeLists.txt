# PocketSloth native build — currently STUB training engine only.
#
# ============================================================================
# Linking real llama.cpp for arm64-v8a (guidelines)
# ============================================================================
# 1. Clone / submodule llama.cpp under:
#      app/src/main/cpp/third_party/llama.cpp
# 2. Prefer building ggml + llama as STATIC libs via add_subdirectory, e.g.:
#      set(LLAMA_NATIVE OFF)          # let Android NDK set arch flags
#      set(GGML_OPENMP OFF)           # OpenMP often awkward on Android
#      set(GGML_VULKAN ON)            # GPU path when available (Adreno / Mali)
#      # OpenCL: experimental; use GGML_OPENCL if your fork supports it —
#      # requires libOpenCL.so from the vendor and careful ABI matching.
#      set(GGML_BLAS OFF)
#      add_subdirectory(third_party/llama.cpp llama_build EXCLUDE_FROM_ALL)
# 3. NEON / CPU:
#      - ABI is filtered to arm64-v8a in app/build.gradle.kts
#      - Pass -DANDROID_ARM_NEON=TRUE (already set in Gradle cmake arguments)
#      - Optional: target feature macros GGML_USE_ARM_FMA / dotprod when CPU supports
# 4. Link:
#      target_link_libraries(pocketsloth_native llama ggml android log)
# 5. Vulkan:
#      find_library(VULKAN_LIB vulkan)
#      target_link_libraries(pocketsloth_native ${VULKAN_LIB})
#      Ensure AndroidManifest has <uses-feature android:name="android.hardware.vulkan.level" ... />
#      if you hard-require GPU.
# 6. Keep abiFilters "arm64-v8a" only until multi-ABI llama builds are validated.
# 7. Replace STUB loop in native-lib.cpp with llama train / LoRA adapter APIs;
#    keep emit_progress(...) callback contract stable for the Kotlin UI.
# ============================================================================

cmake_minimum_required(VERSION 3.22.1)
project(pocketsloth_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(pocketsloth_native SHARED
    native-lib.cpp
)

target_include_directories(pocketsloth_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Android system libs used by the STUB (and by a future llama build).
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(pocketsloth_native
    ${log-lib}
    ${android-lib}
)

# Example (commented) — enable when third_party/llama.cpp is present:
# set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
# set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
# set(GGML_VULKAN ON CACHE BOOL "" FORCE)
# add_subdirectory(third_party/llama.cpp ${CMAKE_BINARY_DIR}/llama EXCLUDE_FROM_ALL)
# target_link_libraries(pocketsloth_native llama ggml)
