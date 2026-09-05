# PocketSloth native build — HybridEngine (llama.cpp when vendored, else STUB).
#
# Vendoring:
#   git submodule update --init --recursive app/src/main/cpp/third_party/llama.cpp
#   (see repo README / .gitmodules)
#
# Optional GPU backends (OFF by default for mobile binary size):
#   -DPOCKETSLOTH_GGML_VULKAN=ON
#   -DPOCKETSLOTH_GGML_OPENCL=ON
# NEON / arm64-v8a is enabled via Gradle: -DANDROID_ARM_NEON=TRUE + abiFilters.

cmake_minimum_required(VERSION 3.22.1)
project(pocketsloth_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Feature toggles (Gradle can override via cmake.arguments)
# ---------------------------------------------------------------------------
option(POCKETSLOTH_USE_LLAMA   "Build & link vendored llama.cpp" ON)
option(POCKETSLOTH_GGML_VULKAN "Enable ggml Vulkan backend (large)" OFF)
option(POCKETSLOTH_GGML_OPENCL "Enable ggml OpenCL backend (experimental)" OFF)
option(POCKETSLOTH_GGML_OPENMP "Enable OpenMP (needs NDK libomp)" OFF)

set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp")
set(POCKETSLOTH_LLAMA_FOUND FALSE)

if(POCKETSLOTH_USE_LLAMA AND EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    set(POCKETSLOTH_LLAMA_FOUND TRUE)
    message(STATUS "PocketSloth: llama.cpp found at ${LLAMA_CPP_DIR}")

    # Cross-compile / mobile-friendly defaults (do not host-tune).
    set(GGML_NATIVE OFF CACHE BOOL "ggml: optimize for host (disable on Android)" FORCE)
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "build llama/ggml as static into pocketsloth_native" FORCE)
    set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
    set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
    set(GGML_BLAS OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP ${POCKETSLOTH_GGML_OPENMP} CACHE BOOL "" FORCE)
    set(GGML_VULKAN ${POCKETSLOTH_GGML_VULKAN} CACHE BOOL "" FORCE)
    set(GGML_OPENCL ${POCKETSLOTH_GGML_OPENCL} CACHE BOOL "" FORCE)

    # ARM / NEON: ABI filtered to arm64-v8a in app/build.gradle.kts;
    # ANDROID_ARM_NEON=TRUE is passed from Gradle cmake arguments.
    if(DEFINED ANDROID_ABI AND ANDROID_ABI STREQUAL "arm64-v8a")
        message(STATUS "PocketSloth: configuring ggml CPU for arm64-v8a (NEON)")
    endif()

    add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_BINARY_DIR}/llama_build EXCLUDE_FROM_ALL)
else()
    message(WARNING "PocketSloth: llama.cpp not found — building STUB-only engine. "
                    "Init submodule: git submodule update --init --recursive")
endif()

add_library(pocketsloth_native SHARED
    native-lib.cpp
    hybrid_engine.cpp
)

target_include_directories(pocketsloth_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(POCKETSLOTH_LLAMA_FOUND)
    target_compile_definitions(pocketsloth_native PRIVATE LLAMA_AVAILABLE=1)
    target_include_directories(pocketsloth_native PRIVATE
        ${LLAMA_CPP_DIR}/include
        ${LLAMA_CPP_DIR}/ggml/include
    )
    # Link static llama (+ transitive ggml). Do not pull llama-common (tools/UI).
    target_link_libraries(pocketsloth_native llama)
    if(POCKETSLOTH_GGML_VULKAN)
        find_library(VULKAN_LIB vulkan)
        if(VULKAN_LIB)
            target_link_libraries(pocketsloth_native ${VULKAN_LIB})
            message(STATUS "PocketSloth: linking Vulkan")
        else()
            message(WARNING "PocketSloth: GGML_VULKAN on but vulkan lib not found")
        endif()
    endif()
else()
    target_compile_definitions(pocketsloth_native PRIVATE LLAMA_AVAILABLE=0)
endif()

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(pocketsloth_native
    ${log-lib}
    ${android-lib}
)
