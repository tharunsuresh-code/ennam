cmake_minimum_required(VERSION 3.25)
project(ennam_native C CXX)

# llama.cpp sources (targeted subset for Android ARM64 inference)
set(LLAMA_SOURCES
    llama.cpp/ggml/src/ggml.c
    llama.cpp/ggml/src/ggml-alloc.c
    llama.cpp/ggml/src/ggml-backend.cpp
    llama.cpp/ggml/src/ggml-backend-reg.cpp
    llama.cpp/ggml/src/ggml-quants.c
    llama.cpp/ggml/src/ggml.c
    llama.cpp/src/llama.cpp
    llama.cpp/src/llama-grammar.cpp
    llama.cpp/src/llama-sampling.cpp
    llama.cpp/src/llama-vocab.cpp
    llama.cpp/src/unicode.cpp
    llama.cpp/src/unicode-data.cpp
    llama.cpp/src/llama-impl.cpp
    llama.cpp/src/llama-model.cpp
    llama.cpp/src/llama-model-loader.cpp
    llama.cpp/src/llama-quant.cpp
    llama.cpp/src/llama-mmap.cpp
    llama.cpp/src/llama-kv-cache.cpp
    llama.cpp/src/llama-hparams.cpp
    llama.cpp/src/llama-batch.cpp
    llama.cpp/src/llama-adapter.cpp
    llama.cpp/src/llama-chat.cpp
    llama.cpp/src/llama-context.cpp
    llama.cpp/src/llama-cparams.cpp
    llama.cpp/ggml/src/ggml-cpu/ggml-cpu.c
    llama.cpp/ggml/src/ggml-cpu/ggml-cpu.cpp
    llama.cpp/ggml/src/ggml-cpu/ggml-cpu-quants.c
    llama.cpp/ggml/src/ggml-cpu/ggml-cpu-aarch64.cpp
    llama.cpp/ggml/src/ggml-cpu/ggml-cpu-traits.cpp
    llama.cpp/ggml/src/ggml-cpu/llamafile/sgemm.cpp
    llama.cpp/src/llama-arch.cpp
    llama.cpp/ggml/src/ggml-opt.cpp
    llama.cpp/ggml/src/ggml-threading.cpp
    llama.cpp/ggml/src/gguf.cpp
)

set(LLAMA_HEADERS
    llama.cpp/include/llama.h
    llama.cpp/ggml/include/ggml.h
    llama.cpp/ggml/include/ggml-alloc.h
    llama.cpp/ggml/include/ggml-backend.h
    llama.cpp/ggml/include/ggml-quants.h
    llama.cpp/src/llama-vocab.h
    llama.cpp/src/unicode.h
)

# Android NDK settings
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

# Disable everything we don't need
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON)
set(LLAMA_STATIC OFF)

# ARM64 optimizations
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64|ARM64")
    set(LLAMA_ARM64 ON CACHE BOOL "" FORCE)
    add_compile_definitions(GGML_USE_LLAMAFILE)
    add_compile_definitions(__ARM_ARCH_8A)
    # NEON - use portable flags, -mcpu=cortex-x1 can cause issues on Tensor G2
    add_compile_options(-O3 -DNDEBUG)
endif()

# JNI bridge
add_library(ennam_jni SHARED jni_bridge.cpp ${LLAMA_SOURCES})
target_include_directories(ennam_jni PRIVATE
    llama.cpp
    llama.cpp/include
    llama.cpp/ggml/include
    llama.cpp/ggml/src
    llama.cpp/ggml/src/ggml-cpu
    llama.cpp/src
    ${CMAKE_CURRENT_SOURCE_DIR}
)
target_link_libraries(ennam_jni PRIVATE
    log
    z
    m
)