cmake_minimum_required(VERSION 3.25)
project(ennam_native C CXX)

# llama.cpp sources (targeted subset for Android ARM64 inference)
set(LLAMA_SOURCES
    llama.cpp/ggml.c
    llama.cpp/ggml-alloc.c
    llama.cpp/ggml-backend.c
    llama.cpp/ggml-quants.c
    llama.cpp/llama.cpp
    llama.cpp/llama-grammar.cpp
    llama.cpp/llama-sampling.cpp
    llama.cpp/llama-vocab.cpp
    llama.cpp/unicode.cpp
)

set(LLAMA_HEADERS
    llama.cpp/include/llama.h
    llama.cpp/ggml.h
    llama.cpp/ggml-alloc.h
    llama.cpp/ggml-backend.h
    llama.cpp/ggml-quants.h
    llama.cpp/llama-vocab.h
    llama.cpp/unicode.h
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
    # NEON + SVE
    add_compile_options(-O3 -DNDEBUG -mcpu=cortex-x1)
endif()

# JNI bridge
add_library(ennam_jni SHARED jni_bridge.cpp ${LLAMA_SOURCES})
target_include_directories(ennam_jni PRIVATE
    llama.cpp
    llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)
target_link_libraries(ennam_jni PRIVATE
    log
    z
    m
)